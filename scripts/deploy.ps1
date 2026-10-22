#!/usr/bin/env pwsh
#Requires -Version 5.1
# Dev deploy: puts the freshly built development jar next to the exe of every
# installed copy of Project Zomboid, writes the site JVM config that loads it and
# removes the files an older build left, cameraunlock-core's DLL among them: the
# same writes install.cmd makes. An explicit game path wins over detection and
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

# The art jars go too, beside the jar whose Class-Path names them, as install.cmd's MOD_DLLS has it.
$artJars = @(Get-ChildItem -Directory (Join-Path $projectRoot 'src\main\resources') | Sort-Object Name | ForEach-Object { "ProjectZomboidHeadTracking-$($_.Name).jar" })

$null = Invoke-DevDeployJavaAgent `
    -GameId 'project-zomboid' `
    -GameDisplayName 'Project Zomboid' `
    -BuildOutputPath (Join-Path $projectRoot 'build\dev') `
    -AgentJars (@('ProjectZomboidHeadTracking.jar') + $artJars) `
    -LegacyFiles @('ProjectZomboidHeadTracking.dll', 'CameraUnlockCore.dll') `
    -GivenPath $GamePath
