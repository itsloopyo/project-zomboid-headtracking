#!/usr/bin/env pwsh
#Requires -Version 5.1
# Dev deploy: puts the freshly built development jar next to the exe of every
# installed copy of Project Zomboid, with cameraunlock-core's DLL, which it loads,
# writes the site JVM config that loads it and removes the DLL an older build left,
# the same writes install.cmd makes. An explicit game path wins over detection and
# stays a single target. No prompts.
#
# Usage: deploy.ps1 [GamePath]

param(
    [Parameter(Position=0)]
    [string]$GamePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

$projectRoot = Split-Path -Parent $PSScriptRoot

Import-Module (Join-Path $projectRoot 'cameraunlock-core\powershell\DevDeploy.psm1') -Force

$null = Invoke-DevDeployJavaAgent `
    -GameId 'project-zomboid' `
    -GameDisplayName 'Project Zomboid' `
    -BuildOutputPath (Join-Path $projectRoot 'build\dev') `
    -AgentJars @('ProjectZomboidHeadTracking.jar') `
    -NativeFiles @('CameraUnlockCore.dll') `
    -LegacyFiles @('ProjectZomboidHeadTracking.dll') `
    -GivenPath $GamePath
