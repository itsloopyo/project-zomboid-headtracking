#!/usr/bin/env pwsh
#Requires -Version 5.1
# Unattended release workflow for ProjectZomboidHeadTracking.
# Usage: pixi run release <major|minor|patch|nightly|X.Y.Z>
#
# Running this command is the authorization: the release runs end to end with
# no prompt. A release that is not on main with a clean tree stops before
# anything is written. The other preconditions (valid semver, tag absent,
# nothing under .lab tracked, the full test suite, a user-facing commit) each
# stop the release with a non-zero exit before any file is edited.
# THIRD-PARTY-NOTICES.md is re-synced after them and goes in the release commit.
# A nightly re-syncs and commits it before handing over to release-nightly.ps1.

[CmdletBinding()]
param(
    [Parameter(Position=0)]
    [string]$Version,
    # Passed through to the nightly publisher only. A versioned release always
    # needs a clean tree.
    [switch]$AllowDirty,
    # Release even when no commit since the last tag is user-facing: writes a
    # maintenance changelog entry instead of stopping.
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

if (-not $Version) {
    throw 'Usage: pixi run release <major|minor|patch|nightly|X.Y.Z>'
}

Import-Module (Join-Path $ProjectRoot 'cameraunlock-core/powershell/ReleaseWorkflow.psm1') -Force

# --- 1. On main, with a clean tree ------------------------------------------
# A nightly makes its own check of the tree, which -AllowDirty skips.
if ($Version -ne 'nightly') {
    Push-Location $ProjectRoot
    try {
        $branch = (git rev-parse --abbrev-ref HEAD).Trim()
        if ($branch -ne 'main') { throw "Releases are cut from main, not '$branch'." }
        if (-not (Test-CleanGitStatus)) { throw 'Working tree is not clean. Commit or stash changes before releasing.' }
    } finally {
        Pop-Location
    }
}

# THIRD-PARTY-NOTICES.md names the cameraunlock-core commit whose scripts go in
# the release ZIP, and packaging refuses a stale one. Re-syncing it before
# packaging means the release carries the correction instead of failing in CI
# after the tag is out.
function Sync-CoreNotices {
    & git -C $ProjectRoot diff --quiet -- THIRD-PARTY-NOTICES.md
    if ($LASTEXITCODE -ne 0) { throw 'THIRD-PARTY-NOTICES.md has uncommitted edits. Commit or discard them, then re-run.' }
    & (Join-Path $ProjectRoot 'cameraunlock-core\scripts\sync-core-notices.ps1') -Repo $ProjectRoot
    if ($LASTEXITCODE -ne 0) { throw "sync-core-notices.ps1 exited $LASTEXITCODE. Fix THIRD-PARTY-NOTICES.md before releasing." }
    & git -C $ProjectRoot diff --quiet -- THIRD-PARTY-NOTICES.md
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'THIRD-PARTY-NOTICES.md re-synced to the pinned cameraunlock-core commit.' -ForegroundColor Yellow
    }
}

if ($Version -eq 'nightly') {
    Sync-CoreNotices
    & git -C $ProjectRoot diff --quiet -- THIRD-PARTY-NOTICES.md
    if ($LASTEXITCODE -ne 0) {
        & git -C $ProjectRoot commit -q -m 'chore: record the cameraunlock-core commit this build ships' -- THIRD-PARTY-NOTICES.md
        if ($LASTEXITCODE -ne 0) { throw 'Could not commit the re-synced THIRD-PARTY-NOTICES.md.' }
    }
    & (Join-Path $PSScriptRoot 'release-nightly.ps1') -AllowDirty:$AllowDirty
    exit $LASTEXITCODE
}

Push-Location $ProjectRoot
try {
    # --- 2. Resolve and validate the target version ------------------------
    # pixi.toml is the version source. build.ps1 reads it into the jar's
    # manifest, and scripts/install.cmd (MOD_VERSION) and launcher-manifest.json
    # (mod_info.version) each carry a copy.
    $pixiPath = Join-Path $ProjectRoot 'pixi.toml'
    $current  = Get-ProjectVersion -Source pixi -Path $pixiPath
    $target   = Resolve-ReleaseVersion -Argument $Version -CurrentVersion $current
    Assert-ReleaseNotBelowCanonicalSince -RepoRoot $ProjectRoot -Version $target
    $tag = "v$target"

    # --- 3. Tag absent, nothing under .lab tracked -------------------------
    if (Test-GitTagExists -Tag $tag) { throw "Tag $tag already exists." }

    & git check-ignore -q .lab/x
    if ($LASTEXITCODE -ne 0) { throw '.gitignore does not list .lab/. Add it and commit that before releasing.' }
    if (git ls-files .lab) { throw 'Files under .lab are tracked, so a push would publish them. Nothing was pushed.' }

    Write-Host "Releasing $current -> $target" -ForegroundColor Cyan

    # --- 4. Full test suite, before any other file is edited ---------------
    Write-Host 'Running the full test suite...' -ForegroundColor Cyan
    try {
        Invoke-ReleaseTestSuite -ProjectRoot $ProjectRoot
    } catch {
        Write-Host "Error: $($_.Exception.Message). Nothing was changed." -ForegroundColor Red
        exit 1
    }

    # --- 5. Changelog -------------------------------------------------------
    # Before the version bump: this is the step that stops a release with no
    # user-facing commit, and stopping here leaves the tree clean.
    Write-Host 'Writing the CHANGELOG entry...' -ForegroundColor Cyan
    try {
        New-ChangelogFromCommits -ChangelogPath (Join-Path $ProjectRoot 'CHANGELOG.md') -Version $target -ArtifactPaths @(
            'src/',
            'scripts/',
            'pixi.toml',
            'cameraunlock-core'
        ) -Maintenance:$Force | Out-Null
    } catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host 'Re-run with -Force for a maintenance release.' -ForegroundColor Yellow
        exit 1
    }

    # --- 6. Notices re-sync -------------------------------------------------
    Sync-CoreNotices

    # --- 7. Version bump ----------------------------------------------------
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    $versionFiles = @(
        @{ Path = 'pixi.toml';           Pattern = '(?m)^version = "\d+\.\d+\.\d+"';       Replacement = "version = `"$target`"" },
        @{ Path = 'scripts/install.cmd'; Pattern = '(?m)^set "MOD_VERSION=\d+\.\d+\.\d+"'; Replacement = "set `"MOD_VERSION=$target`"" },
        @{ Path = 'launcher-manifest.json'; Pattern = '"version": "\d+\.\d+\.\d+"';        Replacement = "`"version`": `"$target`"" }
    )
    foreach ($vf in $versionFiles) {
        $vfPath = Join-Path $ProjectRoot $vf.Path
        $vfText = [System.IO.File]::ReadAllText($vfPath)
        $regex  = [regex]$vf.Pattern
        if ($regex.Matches($vfText).Count -ne 1) { throw "Expected exactly one version line in $($vf.Path)." }
        [System.IO.File]::WriteAllText($vfPath, $regex.Replace($vfText, $vf.Replacement), $utf8NoBom)
    }

    # --- 8. Build -----------------------------------------------------------
    Write-Host 'Building...' -ForegroundColor Cyan
    pixi run build
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }

    # --- 9. Commit, tag, push ----------------------------------------------
    # The subject starts 'Release v' because build.yml skips a commit that
    # does: release.yml builds the tag.
    git add pixi.toml scripts/install.cmd launcher-manifest.json CHANGELOG.md THIRD-PARTY-NOTICES.md
    if ($LASTEXITCODE -ne 0) { throw 'git add failed.' }
    git commit -m "Release $tag"
    if ($LASTEXITCODE -ne 0) { throw 'git commit failed.' }

    git tag -a $tag -m "Release $tag"
    if ($LASTEXITCODE -ne 0) { throw 'git tag failed.' }

    git push --atomic origin main $tag
    if ($LASTEXITCODE -ne 0) { throw "git push failed. The release commit and tag $tag exist locally: git push --atomic origin main $tag" }

    Write-Host "Released $tag. release.yml publishes it." -ForegroundColor Green
} finally {
    Pop-Location
}
