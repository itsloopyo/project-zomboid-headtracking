#!/usr/bin/env pwsh
#Requires -Version 5.1
# Downloads a released Steam Workshop item from GitHub Releases and stages it
# the way stage-workshop does, so the build players get can be played and
# uploaded from the game. With no version it takes the newest Workshop ZIP on
# any release, the nightly included; a version (0.1.0, v0.1.0) or a tag (dev)
# takes that release's.
#
# The copy Steam keeps for a subscription to the item is removed too: with it
# beside the staged copy the game has two of one mod to load. The game folder,
# where the installer and Lopari put the mod, is not touched.
#
# Usage: get-package.ps1 [version]

param(
    [Parameter(Position=0)]
    [string]$Version
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

Add-Type -AssemblyName System.IO.Compression.FileSystem

$repo = 'itsloopyo/project-zomboid-headtracking'
$modName = 'ProjectZomboidHeadTracking'

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'gh (GitHub CLI) not found on PATH.' }

$releases = gh api --paginate "repos/$repo/releases" --jq '.[] | select(.draft | not) | {tag: .tag_name, assets: [.assets[] | {name, updated_at}]}' | ForEach-Object { $_ | ConvertFrom-Json }
if ($LASTEXITCODE -ne 0) { throw "Listing the releases of $repo failed." }

$candidates = foreach ($release in $releases) {
    foreach ($asset in $release.assets) {
        if ($asset.name -like '*-workshop.zip') {
            [pscustomobject]@{ Tag = $release.tag; Name = $asset.name; Updated = [datetime]$asset.updated_at }
        }
    }
}
if ($Version) {
    $tag = if ($Version -match '^\d+\.\d+\.\d+') { "v$Version" } else { $Version }
    $candidates = @($candidates | Where-Object { $_.Tag -eq $tag })
    if ($candidates.Count -eq 0) { throw "No release $tag with a Workshop ZIP on $repo." }
    if ($candidates.Count -gt 1) { throw "Release $tag has more than one Workshop ZIP: $(($candidates.Name) -join ', ')." }
}
$candidates = @($candidates | Sort-Object Updated -Descending)
if ($candidates.Count -eq 0) { throw "No release on $repo has a Workshop ZIP." }
$pick = $candidates[0]
Write-Host "  $($pick.Name) from release $($pick.Tag), built $($pick.Updated.ToUniversalTime().ToString('u'))"

$download = Join-Path ([System.IO.Path]::GetTempPath()) "pzht-get-package-$([guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Path $download | Out-Null
try {
    gh release download $pick.Tag --repo $repo --pattern $pick.Name --dir $download
    if ($LASTEXITCODE -ne 0) { throw "Downloading $($pick.Name) failed." }
    $zip = Join-Path $download $pick.Name

    # Expand-Archive unpacks into the Workshop folder itself, so the ZIP must hold the one item folder and nothing else.
    $archive = [System.IO.Compression.ZipFile]::OpenRead($zip)
    try {
        # The full path is what Expand-Archive writes to, so an entry climbing out with ../ is caught as well.
        $base = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
        $inside = Join-Path $base "$modName\"
        $stray = @($archive.Entries | Where-Object {
            -not $_.FullName.StartsWith("$modName/") -or
            -not [System.IO.Path]::GetFullPath((Join-Path $base $_.FullName)).StartsWith($inside, [System.StringComparison]::OrdinalIgnoreCase)
        })
        if ($stray.Count -gt 0) { throw "$($pick.Name) holds entries outside $modName/, first $($stray[0].FullName)." }
    } finally {
        $archive.Dispose()
    }

    & (Join-Path $PSScriptRoot 'remove-subscribed-workshop.ps1')
    & (Join-Path $PSScriptRoot 'stage-workshop.ps1') -Zip $zip
} finally {
    Remove-Item -LiteralPath $download -Recurse -Force
}
