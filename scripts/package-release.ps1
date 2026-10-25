#!/usr/bin/env pwsh
#Requires -Version 5.1
# Packages Project Zomboid Head Tracking (a Java agent jar with cameraunlock-core compiled into it, no loader).
# Produces three ZIPs:
#   ProjectZomboidHeadTracking-v{version}-installer.zip (GitHub Release): the code
#       jar and the art jars, for install.cmd and launchers before schema 4
#   ProjectZomboidHeadTracking-v{version}-launcher.zip  (GitHub Release): the same
#       without the art jars, which the launcher downloads from the release on
#       their own, each only when it does not have that one already
#   ProjectZomboidHeadTracking-v{version}-dev.zip       (never published: the
#       installer with the development jar, which has the development keys)
# and release/assets/, the art jars under the names the launcher downloads them
# by, ProjectZomboidHeadTracking-<folder>-<first 12 of sha256>.jar, which the
# release uploads beside the ZIPs.
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

# The art jars build.ps1 writes beside the code jar, one a folder of src/main/resources.
$artJars = @(Get-ChildItem -Directory (Join-Path $projectDir 'src/main/resources') | Sort-Object Name | ForEach-Object {
    $jar = Join-Path $projectDir "build/$modName-$($_.Name).jar"
    if (-not (Test-Path $jar)) { throw "$jar not found. Run 'pixi run build' first." }
    Get-Item $jar
})

# install.cmd copies what MOD_DLLS names and uninstall.cmd removes it, so both name every jar.
$expectedDlls = (@("$modName.jar") + @($artJars | ForEach-Object Name)) -join ' '
foreach ($script in @('install.cmd', 'uninstall.cmd')) {
    $dlls = Get-ProjectVersion -Source regex -Path (Join-Path $projectDir "scripts/$script") -Pattern '(?m)^set "MOD_DLLS=([^"]+)"'
    if ($dlls -ne $expectedDlls) {
        throw "scripts/$script has MOD_DLLS=$dlls but the build writes $expectedDlls. Name every jar there."
    }
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

# Each art jar goes to release/assets/ under a name with its hash in it, so a release whose art
# has not changed uploads the same names and bytes as the one before, and the manifest gets a row
# that has the launcher download it by that name.
$assetsDir = Join-Path $releaseDir 'assets'
if (Test-Path $assetsDir) { Remove-Item -Recurse -Force $assetsDir }
New-Item -ItemType Directory -Path $assetsDir -Force | Out-Null
# Not Get-FileHash: CI starts this powershell.exe from pwsh, it inherits pwsh's PSModulePath,
# resolves the Core-only Microsoft.PowerShell.Utility and reports the cmdlet as not recognized.
$sha = [System.Security.Cryptography.SHA256]::Create()
$assetRows = foreach ($jar in $artJars) {
    $stream = [System.IO.File]::OpenRead($jar.FullName)
    try {
        $hash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
    } finally {
        $stream.Dispose()
    }
    $assetName = $jar.BaseName + '-' + $hash.Substring(0, 12) + '.jar'
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $assetsDir $assetName)
    Copy-Item -LiteralPath $jar.FullName -Destination $pluginsDir
    Write-Host ("  plugins/$($jar.Name), assets/$assetName ({0:N1} MB)" -f ($jar.Length / 1MB)) -ForegroundColor Green
    '    { "release_asset": { "name": "' + $assetName + '", "sha256": "' + $hash + '", "size": ' + $jar.Length + ' }, "target": "' + $jar.Name + '" }'
}
$sha.Dispose()

Copy-LicenceNotices -StagingDir $staging -ProjectRoot $projectDir -Additional @('CHANGELOG.md')

$readme = Join-Path $projectDir 'README.md'
Copy-Item $readme -Destination $staging -Force
Write-Host '  README.md' -ForegroundColor Green

# The staged manifest takes the version being packaged, so it cannot disagree
# with the jar beside it.
$manifestVersion = [regex]'("version":\s*")\d+\.\d+\.\d+(")'
$manifestSource  = [System.IO.File]::ReadAllText($manifestPath)
if ($manifestVersion.Matches($manifestSource).Count -ne 1) {
    throw 'launcher-manifest.json must hold exactly one "version": "x.y.z", at mod_info.version.'
}
$manifestText = $manifestVersion.Replace($manifestSource, "`${1}$version`$2")
$codeRow = '{ "source": "plugins/' + $modName + '.jar", "target": "' + $modName + '.jar" }'
if (([regex]::Matches($manifestText, [regex]::Escape($codeRow))).Count -ne 1) {
    throw "launcher-manifest.json must hold the files[] row $codeRow exactly once: the art jar rows go after it."
}
$manifestText = $manifestText.Replace($codeRow, $codeRow + ",`n" + ($assetRows -join ",`n"))
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

# The launcher ZIP is the installer without the art jars.
$launcherStaging = Join-Path $releaseDir 'staging-launcher'
if (Test-Path $launcherStaging) { Remove-Item -Recurse -Force $launcherStaging }
Copy-Item -Recurse $staging $launcherStaging
foreach ($jar in $artJars) { Remove-Item -LiteralPath (Join-Path $launcherStaging "plugins/$($jar.Name)") }
$launcherZip = Join-Path $releaseDir "$modName-v$version-launcher.zip"
if (Test-Path $launcherZip) { Remove-Item $launcherZip -Force }
Push-Location $launcherStaging
try { Compress-Archive -Path '.\*' -DestinationPath $launcherZip -Force } finally { Pop-Location }
Remove-Item -Recurse -Force $launcherStaging

$launcherKb = [math]::Round((Get-Item $launcherZip).Length / 1KB, 1)
Write-Host ("  $launcherZip ({0:N1} KB, the art jars downloaded from release/assets)" -f $launcherKb) -ForegroundColor Green

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

foreach ($zip in @($installerZip, $launcherZip)) {
    & node (Join-Path $projectDir 'cameraunlock-core/scripts/validate-manifest.mjs') $zip
    if ($LASTEXITCODE -ne 0) { throw "validate-manifest rejected $zip" }
}
Write-Host '  manifests validated' -ForegroundColor Green

Write-Host ''
Write-Host '=== Package Complete ===' -ForegroundColor Magenta

Write-Output $installerZip
