#!/usr/bin/env pwsh
#Requires -Version 5.1
# Packages the Steam Workshop item for players who load Java mods through
# ZombieBuddy 2.x, as release/ProjectZomboidHeadTracking-v{version}-workshop.zip.
# The ZIP holds the item folder as the game's uploader reads it from the
# Workshop folder of the Zomboid user folder: preview.png and workshop.txt
# beside Contents/mods/<id>, whose 42/ holds mod.info and the jar. The game
# does not find a Build 42 mod with no common/ beside 42/, and common/ is
# where the notices go.
#
# `pixi run package` builds it beside the installer ZIP, and a release carries
# both. The item reaches Steam through the game's own uploader, from the
# Workshop folder that `pixi run stage-workshop` unpacks it into (or that a
# release's copy of the ZIP is extracted into by hand).

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

$projectDir = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $projectDir 'cameraunlock-core/powershell/ReleaseWorkflow.psm1') -Force
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem

$modName = 'ProjectZomboidHeadTracking'
$version = Get-ProjectVersion -Source pixi -Path (Join-Path $projectDir 'pixi.toml')

$jarPath = Join-Path $projectDir "build/$modName.jar"
if (-not (Test-Path $jarPath)) { throw "$modName.jar not found at: $jarPath. Run 'pixi run build' first." }

$releaseDir = Join-Path $projectDir 'release'
New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
$source = Join-Path $projectDir 'workshop'
$staging = Join-Path $releaseDir 'staging-workshop'
if (Test-Path $staging) { Remove-Item -Recurse -Force $staging }
$item = Join-Path $staging $modName
$mod = Join-Path $item "Contents/mods/$modName"
# media/java/client: ZombieBuddy leaves a jar under it out on a dedicated server.
$java = Join-Path $mod '42/media/java/client'
$common = Join-Path $mod 'common'
New-Item -ItemType Directory -Path $java, $common -Force | Out-Null

Copy-Item (Join-Path $source 'preview.png'), (Join-Path $source 'workshop.txt') -Destination $item -Force
Copy-Item (Join-Path $source 'poster.png') -Destination (Join-Path $mod '42') -Force
$modInfo = [System.IO.File]::ReadAllText((Join-Path $source 'mod.info'))
if (-not $modInfo.Contains('modversion=@VERSION@')) {
    throw 'workshop/mod.info must hold modversion=@VERSION@, which the packager fills in.'
}
[System.IO.File]::WriteAllText((Join-Path $mod '42/mod.info'), $modInfo.Replace('@VERSION@', $version), (New-Object System.Text.UTF8Encoding $false))
Copy-Item $jarPath -Destination $java -Force
# The notices name the core commit compiled in, as the installer's must.
Assert-CoreCommitInNotices -RepoRoot $projectDir
Copy-LicenceNotices -StagingDir $common -ProjectRoot $projectDir -Additional @('CHANGELOG.md')

# Compress-Archive on Windows PowerShell writes its entry names with backslashes.
$zip = Join-Path $releaseDir "$modName-v$version-workshop.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
$archive = [System.IO.Compression.ZipFile]::Open($zip, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -Path $staging -Recurse -File) {
        $entry = $file.FullName.Substring($staging.Length + 1).Replace('\', '/')
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $entry, [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    $archive.Dispose()
}
Remove-Item -Recurse -Force $staging

$kb = [math]::Round((Get-Item $zip).Length / 1KB, 1)
Write-Host ("  $zip ({0:N1} KB, Steam Workshop item for ZombieBuddy)" -f $kb) -ForegroundColor Green
Write-Output $zip
