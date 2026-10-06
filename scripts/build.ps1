$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

function Invoke-Tool {
    param([string]$Tool, [string[]]$Arguments)
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}

function Get-JavaSources {
    param([string]$Directory)
    Get-ChildItem -Recurse -Filter *.java $Directory | ForEach-Object FullName
}

$versionLine = Select-String -Path pixi.toml -Pattern '^version = "(.+)"$' | Select-Object -First 1
if (-not $versionLine) { throw 'pixi.toml has no version line' }
$version = $versionLine.Matches[0].Groups[1].Value

if (Test-Path build) { Remove-Item -Recurse -Force build }
New-Item -ItemType Directory -Force build | Out-Null

$javac = @('--release', '25', '-Xlint:all', '-Werror')
Invoke-Tool javac ($javac + @('-d', 'build/stubs') + (Get-JavaSources stubs))
# Two jars from one source tree. The one that ships is compiled with src/shipped, whose DevKeys
# is empty. The development one is compiled with src/dev, whose DevKeys is the development
# keys, so none of that code is in a jar a player installs.
Invoke-Tool javac ($javac + @('-cp', 'build/stubs', '-d', 'build/classes') + (Get-JavaSources src/main/java) + (Get-JavaSources src/shipped/java))
Invoke-Tool javac ($javac + @('-cp', 'build/stubs', '-d', 'build/dev-classes') + (Get-JavaSources src/main/java) + (Get-JavaSources src/dev/java))
Invoke-Tool javac ($javac + @('-cp', 'build/classes', '-d', 'build/test-classes') + (Get-JavaSources src/test/java))

Copy-Item -Recurse -Force src/main/resources/* build/classes/
Copy-Item -Recurse -Force src/main/resources/* build/dev-classes/

@(
    'Premain-Class: com.cameraunlock.zomboid.Agent'
    'Agent-Class: com.cameraunlock.zomboid.Agent'
    'Implementation-Title: Project Zomboid Head Tracking'
    "Implementation-Version: $version"
) | Set-Content -Encoding ascii build/MANIFEST.MF
Invoke-Tool jar @('--create', '--file', 'build/ProjectZomboidHeadTracking.jar', '--manifest', 'build/MANIFEST.MF', '-C', 'build/classes', '.')
Write-Host "Built build/ProjectZomboidHeadTracking.jar $version"
New-Item -ItemType Directory -Force build/dev | Out-Null
Invoke-Tool jar @('--create', '--file', 'build/dev/ProjectZomboidHeadTracking.jar', '--manifest', 'build/MANIFEST.MF', '-C', 'build/dev-classes', '.')
Write-Host "Built build/dev/ProjectZomboidHeadTracking.jar $version (development keys in)"

# cameraunlock-core's config owner and hotkey poller are C++, so they are built into a DLL the
# agent loads from beside its jar. The same build makes the config tests, which test.ps1 runs.
Invoke-Tool cmake @('-S', 'native', '-B', 'build-native', '-A', 'x64')
Invoke-Tool cmake @('--build', 'build-native', '--config', 'Release')
Copy-Item -Force build-native/Release/ProjectZomboidHeadTracking.dll build/
Write-Host 'Built build/ProjectZomboidHeadTracking.dll'
