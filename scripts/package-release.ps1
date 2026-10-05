#!/usr/bin/env pwsh
#Requires -Version 5.1
# Packages Project Zomboid Head Tracking (a Java agent jar and the DLL it loads, no loader).
# Produces two ZIPs:
#   ProjectZomboidHeadTracking-v{version}-installer.zip (GitHub Release)
#   ProjectZomboidHeadTracking-v{version}-dev.zip       (never published: the
#       installer with the development keys switched on)
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

$dllPath = Join-Path $projectDir "build/$modName.dll"
if (-not (Test-Path $dllPath)) {
    throw "$modName.dll not found at: $dllPath. Run 'pixi run build' first."
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
Write-Host "  plugins/$modName.dll" -ForegroundColor Green

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
$manifestPath = Join-Path $projectDir 'launcher-manifest.json'
$hasManifest  = Test-Path $manifestPath
if ($hasManifest) {
    $manifestText = (Get-Content $manifestPath -Raw) -replace '("version":\s*")\d+\.\d+\.\d+(")', "`${1}$version`$2"
    [System.IO.File]::WriteAllText((Join-Path $staging 'launcher-manifest.json'), $manifestText, (New-Object System.Text.UTF8Encoding $false))
    Write-Host "  launcher-manifest.json (version $version)" -ForegroundColor Green
} else {
    Write-Warning 'launcher-manifest.json does not exist yet, so this installer ZIP is not a package the launcher can read.'
}

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

# The development ZIP is the installer plus the marker DevKeys looks for, which
# its install.cmd copies next to the jar. Its name must not end in
# -installer.zip: build.yml and the release paths pick the installer up by that
# ending, and a ZIP that switches the development keys on never goes to a player.
$marker = "$modName.dev"
Set-Content -Path (Join-Path $pluginsDir $marker) -Value 'development install' -Encoding ascii
$stagedInstall = Join-Path $staging 'install.cmd'
$payloadLine   = "set `"MOD_DLLS=$modName.jar $modName.dll`""
$installText   = [System.IO.File]::ReadAllText($stagedInstall)
if (-not $installText.Contains($payloadLine)) {
    throw "scripts/install.cmd no longer has the line $payloadLine, so the development ZIP's installer cannot be told to copy $marker."
}
[System.IO.File]::WriteAllText($stagedInstall, $installText.Replace($payloadLine, "set `"MOD_DLLS=$modName.jar $modName.dll $marker`""), (New-Object System.Text.ASCIIEncoding))

$devZip = Join-Path $releaseDir "$modName-v$version-dev.zip"
if (Test-Path $devZip) { Remove-Item $devZip -Force }
Push-Location $staging
try { Compress-Archive -Path '.\*' -DestinationPath $devZip -Force } finally { Pop-Location }
Remove-Item -Recurse -Force $staging

$devKb = [math]::Round((Get-Item $devZip).Length / 1KB, 1)
Write-Host ("  $devZip ({0:N1} KB, development keys on)" -f $devKb) -ForegroundColor Green

if ($hasManifest) {
    & node (Join-Path $projectDir 'cameraunlock-core/scripts/validate-manifest.mjs') $installerZip
    if ($LASTEXITCODE -ne 0) { throw "validate-manifest rejected $installerZip" }
    Write-Host '  manifest validated' -ForegroundColor Green
}

Write-Host ''
Write-Host '=== Package Complete ===' -ForegroundColor Magenta

Write-Output $installerZip
