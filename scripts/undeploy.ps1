#!/usr/bin/env pwsh
#Requires -Version 5.1
# Dev uninstall: runs uninstall.cmd against every installed copy of Project Zomboid,
# the same copies deploy.ps1 writes to, so a second store's copy is not left running
# the last development build. An explicit game path wins over detection and stays a
# single target. No prompts.
#
# Without a game path it also removes the Workshop item: the copy stage-workshop
# and get-package put in the Zomboid user folder, and the copy Steam keeps for a
# subscription.
#
# Usage: undeploy.ps1 [GamePath]

param(
    [Parameter(Position=0)]
    [string]$GamePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot

Import-Module (Join-Path $projectRoot 'cameraunlock-core\powershell\GamePathDetection.psm1') -Force

if ($GamePath) {
    $paths = @($GamePath)
} else {
    $paths = @(Find-AllGamePaths -GameId 'project-zomboid')
    if ($paths.Count -eq 0) { throw 'No installed copy of Project Zomboid was found.' }
}
$failed = @()
foreach ($path in $paths) {
    Write-Host "Uninstalling from: $path" -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot 'uninstall.cmd') $path /y
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  uninstall.cmd exited $LASTEXITCODE for $path" -ForegroundColor Red
        $failed += $path
    } else {
        Write-Host "  removed from $path" -ForegroundColor Green
    }
}
if (-not $GamePath) {
    $staged = Join-Path $env:USERPROFILE 'Zomboid\Workshop\ProjectZomboidHeadTracking'
    if (Test-Path -LiteralPath $staged) {
        Remove-Item -LiteralPath $staged -Recurse -Force
        Write-Host "  removed the staged Workshop item at $staged" -ForegroundColor Green
    }
    & (Join-Path $PSScriptRoot 'remove-subscribed-workshop.ps1')
}
if ($failed.Count -gt 0) {
    throw "Uninstall failed for $($failed.Count) of $($paths.Count) installs: $($failed -join '; ')"
}
