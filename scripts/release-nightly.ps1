[CmdletBinding()]
param([switch]$AllowDirty)

$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

Import-Module (Join-Path $ProjectRoot 'cameraunlock-core\powershell\ReleaseWorkflow.psm1') -Force
Import-Module (Join-Path $ProjectRoot 'cameraunlock-core\powershell\NightlyRelease.psm1') -Force

$version = Get-ProjectVersion -Source pixi -Path (Join-Path $ProjectRoot 'pixi.toml')

# -NoNexusZip: package-release.ps1 builds no Nexus ZIP, and says why.
# -WorkshopZip: package also builds the Steam Workshop item.
Publish-NightlyBuild `
    -ModId 'project-zomboid' `
    -ModName 'ProjectZomboidHeadTracking' `
    -Version $version `
    -ProjectRoot $ProjectRoot `
    -BuildCommand 'pixi run build' `
    -NoNexusZip `
    -WorkshopZip `
    -AllowDirty:$AllowDirty
