#!/usr/bin/env pwsh
#Requires -Version 5.1
# Puts the packaged Steam Workshop item in the Workshop folder of the Zomboid
# user folder, where the game's uploader (main menu, Workshop) reads it and
# where the game also loads it as a local mod, so the item can be played
# exactly as it will be uploaded.
#
# The uploader writes the item's Workshop id into workshop.txt after the first
# upload, and an upload without it makes a new item. An id in the staged copy
# is carried into the new one; once there is one, it belongs in
# workshop/workshop.txt too, so every machine updates the same item.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

$projectDir = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $projectDir 'cameraunlock-core/powershell/ReleaseWorkflow.psm1') -Force

$modName = 'ProjectZomboidHeadTracking'
$version = Get-ProjectVersion -Source pixi -Path (Join-Path $projectDir 'pixi.toml')
$zip = Join-Path $projectDir "release/$modName-v$version-workshop.zip"
if (-not (Test-Path $zip)) { throw "$zip not found. Run 'pixi run package-workshop' first." }

$workshop = Join-Path $env:USERPROFILE 'Zomboid\Workshop'
if (-not (Test-Path $workshop)) { throw "$workshop not found: the game makes it at its first start." }
$item = Join-Path $workshop $modName

$id = $null
$stagedText = Join-Path $item 'workshop.txt'
if (Test-Path $stagedText) {
    $id = Get-Content $stagedText | Where-Object { $_ -match '^id=\d+$' } | Select-Object -First 1
}
if (Test-Path $item) { Remove-Item -Recurse -Force $item }
$committedId = Get-Content (Join-Path $projectDir 'workshop/workshop.txt') | Where-Object { $_ -match '^id=\d+$' } | Select-Object -First 1
if (-not $id -and -not $committedId) {
    Write-Host '  no Workshop id here or in workshop/workshop.txt: an upload makes a NEW item. After the first upload, put its id= line in workshop/workshop.txt.' -ForegroundColor Yellow
}

Expand-Archive -Path $zip -DestinationPath $workshop
if ($id) {
    $text = Get-Content (Join-Path $item 'workshop.txt')
    if (-not ($text -match '^id=')) {
        Set-Content -Encoding ascii (Join-Path $item 'workshop.txt') (@($text[0], $id) + $text[1..($text.Count - 1)])
        Write-Host "  carried $id over from the copy that was staged; put it in workshop/workshop.txt" -ForegroundColor Yellow
    }
}
Write-Host "  staged v$version at $item" -ForegroundColor Green
