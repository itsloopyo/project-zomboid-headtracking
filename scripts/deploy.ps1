#!/usr/bin/env pwsh
#Requires -Version 5.1
# Dev deploy: puts the freshly built development jar next to the exe of every
# installed copy of Project Zomboid, with the DLL it loads, and writes the site
# JVM config that loads it, the same writes install.cmd makes. An explicit game path wins over
# detection and stays a single target. No prompts.
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
    -MainClass 'com/cameraunlock/zomboid/Boot' `
    -GivenPath $GamePath

# The agent loads cameraunlock-core's config owner from this DLL, beside its jar.
$nativeDll = 'ProjectZomboidHeadTracking.dll'
Import-Module (Join-Path $projectRoot 'cameraunlock-core\powershell\GamePathDetection.psm1') -Force
$targets = if ($GamePath) { @($GamePath) } else { @(Find-AllGamePaths -GameId 'project-zomboid') }
foreach ($target in $targets) {
    Copy-Item -LiteralPath (Join-Path $projectRoot "build\$nativeDll") -Destination (Join-Path $target $nativeDll) -Force
    Write-Host "Deployed $nativeDll"
}
