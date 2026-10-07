#!/usr/bin/env pwsh
#Requires -Version 5.1
# Packages Project Zomboid Head Tracking (a Java agent jar and cameraunlock-core's DLL, which it loads, no loader).
# Produces two ZIPs:
#   ProjectZomboidHeadTracking-v{version}-installer.zip (GitHub Release)
#   ProjectZomboidHeadTracking-v{version}-dev.zip       (never published: the
#       installer with the development jar, which has the development keys)
#
# There is no Nexus ZIP. Installing the mod is the jar next to the game exe
# plus a site JVM config that install.cmd generates from the game's own
# ProjectZomboid64.json, and an archive extracted over the game folder cannot
# generate that file.
#
# Consumes what is committed and what `pixi run build` produced. It never
# touches the network.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'

$projectDir = Split-Path -Parent $PSScriptRoot

Import-Module (Join-Path $projectDir 'cameraunlock-core/powershell/ReleaseWorkflow.psm1') -Force

$modName = 'ProjectZomboidHeadTracking'
$version = Get-ProjectVersion -Source pixi -Path (Join-Path $projectDir 'pixi.toml')

# install.cmd writes MOD_VERSION into the state file, so a copy that lags
# pixi.toml ships an installer that reports the wrong version.
$installCmd = Join-Path $projectDir 'scripts/install.cmd'
$installVersion = Get-ProjectVersion -Source regex -Path $installCmd -Pattern '(?m)^set "MOD_VERSION=([^"]+)"'
if ($installVersion -ne $version) {
    throw "scripts/install.cmd has MOD_VERSION=$installVersion but pixi.toml has version $version. They move together; release.ps1 bumps both."
}

Write-Host ""
Write-Host "=== Packaging $modName v$version ===" -ForegroundColor Magenta
Write-Host ""

$jarPath = Join-Path $projectDir "build/$modName.jar"
if (-not (Test-Path $jarPath)) {
    throw "$modName.jar not found at: $jarPath. Run 'pixi run build' first."
}

$devJarPath = Join-Path $projectDir "build/dev/$modName.jar"
if (-not (Test-Path $devJarPath)) {
    throw "the development $modName.jar not found at: $devJarPath. Run 'pixi run build' first."
}

$coreDll = 'CameraUnlockCore.dll'
$dllPath = Join-Path $projectDir "build/$coreDll"
if (-not (Test-Path $dllPath)) {
    throw "$coreDll not found at: $dllPath. Run 'pixi run build' first."
}

# The launcher reads this file from the root of the ZIP to learn how to install
# the mod, and an absent one reads to it as a package of an older kind.
# Copy-SharedBundle checks its delivery mode and validate-manifest the built ZIP.
$manifestPath = Join-Path $projectDir 'launcher-manifest.json'
if (-not (Test-Path $manifestPath)) {
    throw "launcher-manifest.json not found at: $manifestPath. Without it the installer ZIP is not a package the launcher can read."
}

$releaseDir = Join-Path $projectDir 'release'
$staging    = Join-Path $releaseDir 'staging-installer'
if (Test-Path $staging) { Remove-Item -Recurse -Force $staging }
New-Item -ItemType Directory -Path $staging -Force | Out-Null

foreach ($script in @('install.cmd', 'uninstall.cmd')) {
    Copy-Item (Join-Path $projectDir "scripts/$script") -Destination $staging -Force
    Write-Host "  $script" -ForegroundColor Green
}

# install.cmd and uninstall.cmd are thin wrappers: the bodies they call, the
# game finder and jvm-site-config.ps1 all travel in shared/.
Copy-SharedBundle -StagingDir $staging

$pluginsDir = Join-Path $staging 'plugins'
New-Item -ItemType Directory -Path $pluginsDir -Force | Out-Null
Copy-Item $jarPath -Destination $pluginsDir -Force
Write-Host "  plugins/$modName.jar" -ForegroundColor Green
Copy-Item $dllPath -Destination $pluginsDir -Force
Write-Host "  plugins/$coreDll" -ForegroundColor Green

Copy-LicenceNotices -StagingDir $staging -ProjectRoot $projectDir -Additional @('CHANGELOG.md')

$readme = Join-Path $projectDir 'README.md'
if (Test-Path $readme) {
    Copy-Item $readme -Destination $staging -Force
    Write-Host '  README.md' -ForegroundColor Green
} else {
    Write-Warning 'README.md does not exist yet, so this installer ZIP ships without one.'
}

# The staged manifest takes the version being packaged, so it cannot disagree
# with the jar beside it.
$manifestVersion = [regex]'("version":\s*")\d+\.\d+\.\d+(")'
$manifestSource  = [System.IO.File]::ReadAllText($manifestPath)
if ($manifestVersion.Matches($manifestSource).Count -ne 1) {
    throw 'launcher-manifest.json must hold exactly one "version": "x.y.z", at mod_info.version.'
}
$manifestText = $manifestVersion.Replace($manifestSource, "`${1}$version`$2")
[System.IO.File]::WriteAllText((Join-Path $staging 'launcher-manifest.json'), $manifestText, (New-Object System.Text.UTF8Encoding $false))
Write-Host "  launcher-manifest.json (version $version)" -ForegroundColor Green

# cmd.exe misparses a batch file with LF line endings, and it does so quietly.
foreach ($cmd in Get-ChildItem -Path $staging -Recurse -File -Filter '*.cmd') {
    if ([System.IO.File]::ReadAllText($cmd.FullName) -match '(?<!\r)\n') {
        throw "$($cmd.FullName) has LF line endings. Batch files ship CRLF: run unix2dos on its source."
    }
}

$installerZip = Join-Path $releaseDir "$modName-v$version-installer.zip"
if (Test-Path $installerZip) { Remove-Item $installerZip -Force }
Push-Location $staging
try { Compress-Archive -Path '.\*' -DestinationPath $installerZip -Force } finally { Pop-Location }

$installerKb = [math]::Round((Get-Item $installerZip).Length / 1KB, 1)
Write-Host ("  $installerZip ({0:N1} KB)" -f $installerKb) -ForegroundColor Green

# The development ZIP is the installer with the development jar in the place of
# the one that ships. Its name must not end in -installer.zip: build.yml and the
# release paths pick the installer up by that ending, and a ZIP with the
# development keys in it never goes to a player.
Copy-Item $devJarPath -Destination $pluginsDir -Force

$devZip = Join-Path $releaseDir "$modName-v$version-dev.zip"
if (Test-Path $devZip) { Remove-Item $devZip -Force }
Push-Location $staging
try { Compress-Archive -Path '.\*' -DestinationPath $devZip -Force } finally { Pop-Location }
Remove-Item -Recurse -Force $staging

$devKb = [math]::Round((Get-Item $devZip).Length / 1KB, 1)
Write-Host ("  $devZip ({0:N1} KB, development keys in)" -f $devKb) -ForegroundColor Green

& node (Join-Path $projectDir 'cameraunlock-core/scripts/validate-manifest.mjs') $installerZip
if ($LASTEXITCODE -ne 0) { throw "validate-manifest rejected $installerZip" }
Write-Host '  manifest validated' -ForegroundColor Green

Write-Host ''
Write-Host '=== Package Complete ===' -ForegroundColor Magenta

Write-Output $installerZip
