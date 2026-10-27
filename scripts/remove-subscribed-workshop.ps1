#!/usr/bin/env pwsh
#Requires -Version 5.1
# Removes the copy of the Workshop item that Steam keeps for a subscription,
# in every Steam library, the item named by the id in workshop/workshop.txt.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectDir = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $projectDir 'cameraunlock-core/powershell/GamePathDetection.psm1') -Force

$id = Get-Content (Join-Path $projectDir 'workshop/workshop.txt') | Where-Object { $_ -match '^id=(\d+)$' } | ForEach-Object { $Matches[1] } | Select-Object -First 1
if (-not $id) { throw 'workshop/workshop.txt holds no id= line.' }
foreach ($library in Find-SteamLibraries) {
    $subscribed = Join-Path $library "steamapps\workshop\content\108600\$id"
    if (Test-Path -LiteralPath $subscribed) {
        Remove-Item -LiteralPath $subscribed -Recurse -Force
        Write-Host "  removed the subscribed copy at $subscribed"
    }
}
