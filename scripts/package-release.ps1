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
# release uploads beside the ZIPs, and release/headstart/, the Headstart package: the code jar and
# the settings list (config/cameraunlock-settings.json), each under a name with its hash in it,
# and the cameraunlock-package.toml that lists them and every art jar.
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
function Get-Sha256([string]$Path) {
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
    } finally {
        $stream.Dispose()
    }
}
# The Headstart package's files, as (path in the package, release asset name, sha256, size).
$packageFiles = New-Object System.Collections.Generic.List[object]
$assetRows = foreach ($jar in $artJars) {
    $hash = Get-Sha256 $jar.FullName
    $assetName = $jar.BaseName + '-' + $hash.Substring(0, 12) + '.jar'
    $asset = Join-Path $assetsDir $assetName
    Copy-Item -LiteralPath $jar.FullName -Destination $asset
    # The build hard links an unchanged art jar to its copy in build-cache, so the copy keeps the
    # time that art was first built, and the nightly publish refuses a release asset older than
    # its build. This copy is the asset this package made: it carries the time it was made.
    (Get-Item -LiteralPath $asset).LastWriteTime = Get-Date
    Copy-Item -LiteralPath $jar.FullName -Destination $pluginsDir
    Write-Host ("  plugins/$($jar.Name), assets/$assetName ({0:N1} MB)" -f ($jar.Length / 1MB)) -ForegroundColor Green
    $packageFiles.Add([PSCustomObject]@{ Path = $jar.Name; Asset = $assetName; Sha256 = $hash; Size = $jar.Length })
    '    { "release_asset": { "name": "' + $assetName + '", "sha256": "' + $hash + '", "size": ' + $jar.Length + ' }, "target": "' + $jar.Name + '" }'
}

# release/headstart/ is the Headstart package (universal-head-tracking docs/packages.md): the code
# jar and the settings list under names with their hashes in them, and cameraunlock-package.toml,
# which names them and the art jars in release/assets/ by their downloads from this version's tag.
# The settings list is config/cameraunlock-settings.json, the rows of CameraUnlock.ini as Headstart's
# settings page reads them, which the tests hold to the rows the code jar describes.
# release-mod.yml uploads it to a tagged release and has the catalog pin it. It is not in
# release/assets/, which the nightly uploads to the dev release as it is: for a dev build the
# nightly rewrites this manifest to name the dev build's version and the dev release's files, and
# has the catalog list it as dev.
$headstartDir = Join-Path $releaseDir 'headstart'
if (Test-Path $headstartDir) { Remove-Item -Recurse -Force $headstartDir }
New-Item -ItemType Directory -Path $headstartDir -Force | Out-Null
$codeHash = Get-Sha256 $jarPath
$codeAsset = "$modName-$($codeHash.Substring(0, 12)).jar"
Copy-Item -LiteralPath $jarPath -Destination (Join-Path $headstartDir $codeAsset)
$packageFiles.Insert(0, [PSCustomObject]@{ Path = "$modName.jar"; Asset = $codeAsset; Sha256 = $codeHash; Size = (Get-Item -LiteralPath $jarPath).Length })
$settingsName = 'cameraunlock-settings.json'
$settingsPath = Join-Path $projectDir "config/$settingsName"
if (-not (Test-Path -LiteralPath $settingsPath)) {
    throw "config/$settingsName not found at: $settingsPath. Run 'pixi run render-config' and commit it."
}
$settingsHash = Get-Sha256 $settingsPath
$settingsAsset = "$modName-settings-$($settingsHash.Substring(0, 12)).json"
Copy-Item -LiteralPath $settingsPath -Destination (Join-Path $headstartDir $settingsAsset)
# The copy keeps the time of the checkout, and the nightly publish refuses a file older than its build.
(Get-Item -LiteralPath (Join-Path $headstartDir $settingsAsset)).LastWriteTime = Get-Date
$packageFiles.Add([PSCustomObject]@{ Path = $settingsName; Asset = $settingsAsset; Sha256 = $settingsHash; Size = (Get-Item -LiteralPath $settingsPath).Length })
$sha.Dispose()

$download = "https://github.com/itsloopyo/project-zomboid-headtracking/releases/download/v$version"
$packageToml = @(
    'schema = 1'
    'id = "project-zomboid-headtracking"'
    "version = `"$version`""
    'kind = "java-agent"'
    'interface = 1'
    foreach ($file in $packageFiles) {
        ''
        '[[file]]'
        "path = `"$($file.Path)`""
        "url = `"$download/$($file.Asset)`""
        "sha256 = `"$($file.Sha256)`""
        "size = $($file.Size)"
    }
    ''
    '[java_agent]'
    "jar = `"$modName.jar`""
    ''
    '[java_agent.properties]'
    # Agent.GAME_DIRECTORY_PROPERTY: where the agent keeps CameraUnlock.ini and its log.
    '"cameraunlock.zomboid.gameDirectory" = "{data}"'
    # Agent.UDP_PORT_PROPERTY: the port Headstart passes the first player's tracker on to.
    '"cameraunlock.zomboid.udpPort" = "{port}"'
) -join "`n"
[System.IO.File]::WriteAllText((Join-Path $headstartDir 'cameraunlock-package.toml'), $packageToml + "`n", (New-Object System.Text.UTF8Encoding $false))
Write-Host "  headstart/$codeAsset, headstart/$settingsAsset, headstart/cameraunlock-package.toml ($($packageFiles.Count) files)" -ForegroundColor Green

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
