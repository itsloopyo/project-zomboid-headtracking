#!/usr/bin/env pwsh
#Requires -Version 5.1
# Stops ZombieBuddy updating itself, or lets it again with -Unlock, in every installed copy of
# Project Zomboid. ZombieBuddy copies a newer signed jar from its Workshop item over the game
# folder's ZombieBuddy.jar without asking. While the game runs the JVM holds that jar open, so the
# copy fails and the jar is left as ZombieBuddy.jar.new, which zbNative.dll renames over the jar at
# the next launch unless it is a directory. The lock is that directory, with a file in it so the
# copy cannot replace it. A deny on the jar itself cannot work: Users have full control of the game
# folder, and its delete-child right overrides a file's own deny. An explicit game path wins over
# detection.
#
# Usage: zb-lock.ps1 [-Unlock] [GamePath]

param(
    [switch]$Unlock,
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

$markerName = 'zb-lock.txt'
$markerText = "This folder stops ZombieBuddy replacing ZombieBuddy.jar with a newer copy from its Workshop item.`r`n" +
    "Remove it with 'pixi run unlock-zb' in project-zomboid-headtracking, or delete it by hand, to let ZombieBuddy update.`r`n" +
    "ZombieBuddy's own uninstaller cannot remove ZombieBuddy.jar.new while this folder is here.`r`n"

function Test-OurLock([string]$pending) {
    $entries = @(Get-ChildItem -LiteralPath $pending -Force)
    $entries.Count -eq 1 -and $entries[0].Name -eq $markerName
}

foreach ($path in $paths) {
    $jar = Join-Path $path 'ZombieBuddy.jar'
    $pending = "$jar.new"
    if (-not (Test-Path -LiteralPath $jar)) {
        Write-Host "$path`: no ZombieBuddy.jar, left alone" -ForegroundColor Yellow
        continue
    }
    $isDir = Test-Path -LiteralPath $pending -PathType Container
    $isFile = Test-Path -LiteralPath $pending -PathType Leaf
    if ($isDir -and -not (Test-OurLock $pending)) {
        throw "$pending is a folder this script did not make; look at it before locking or unlocking"
    }

    if ($Unlock) {
        if ($isDir) {
            Remove-Item -LiteralPath $pending -Recurse -Force
            Write-Host "$path`: unlocked, ZombieBuddy updates itself again" -ForegroundColor Green
        } else {
            Write-Host "$path`: was not locked" -ForegroundColor Cyan
        }
        if ($isFile) {
            Write-Host "  an update is waiting in ZombieBuddy.jar.new and is applied at the next launch" -ForegroundColor Yellow
        }
    } else {
        if ($isDir) {
            Write-Host "$path`: already locked" -ForegroundColor Cyan
            continue
        }
        if ($isFile) {
            Remove-Item -LiteralPath $pending -Force
            Write-Host "  dropped an update that was waiting in ZombieBuddy.jar.new" -ForegroundColor Yellow
        }
        New-Item -ItemType Directory -Path $pending | Out-Null
        [IO.File]::WriteAllText((Join-Path $pending $markerName), $markerText)
        Write-Host "$path`: locked, ZombieBuddy no longer updates itself" -ForegroundColor Green
    }
}
