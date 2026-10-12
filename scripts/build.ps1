$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

function Invoke-Tool {
    param([string]$Tool, [string[]]$Arguments)
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}

# The sources go to javac in an argument file: listed on its command line they pass Windows'
# 32767 character limit from a checkout as deep as a worktree.
function Invoke-Javac {
    param([string]$Name, [string[]]$Options, [string[]]$Sources)
    $list = "build/$Name.sources"
    $Sources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' } | Set-Content -Encoding ascii $list
    Invoke-Tool javac ($Options + @("@$list"))
}

function Get-JavaSources {
    param([string]$Directory)
    Get-ChildItem -Recurse -Filter *.java $Directory | ForEach-Object { Resolve-Path -LiteralPath $_.FullName -Relative }
}

# The sources go to javac in a file of their own: on the command line, a few hundred of them pass
# Windows' limit on its length in a checkout a few folders deep. The file is ASCII, as javac reads
# a byte order mark as part of the first path.
function Invoke-Javac {
    param([string]$Name, [string[]]$Options, [string[]]$Sources)
    $list = Join-Path 'build' "$Name.sources"
    Set-Content -Encoding Ascii -Path $list -Value ($Sources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' })
    Invoke-Tool javac ($Options + @("@$list"))
}

$versionLine = Select-String -Path pixi.toml -Pattern '^version = "(.+)"$' | Select-Object -First 1
if (-not $versionLine) { throw 'pixi.toml has no version line' }
$version = $versionLine.Matches[0].Groups[1].Value

if (Test-Path build) { Remove-Item -Recurse -Force build }
New-Item -ItemType Directory -Force build | Out-Null

$javac = @('--release', '25', '-Xlint:all', '-Werror')
Invoke-Javac stubs ($javac + @('-d', 'build/stubs')) (Get-JavaSources stubs)
# cameraunlock-core's Java is compiled into the mod's jars from the submodule's source: the binding
# to CameraUnlockCore.dll and the boot class the installed site JVM config names.
$core = Get-JavaSources cameraunlock-core/java/src
# Two jars from one source tree. The one that ships is compiled with src/shipped, whose DevKeys
# is empty. The development one is compiled with src/dev, whose DevKeys is the development
# keys, so none of that code is in a jar a player installs.
Invoke-Javac classes ($javac + @('-cp', 'build/stubs', '-d', 'build/classes')) ($core + (Get-JavaSources src/main/java) + (Get-JavaSources src/shipped/java))
Invoke-Javac dev-classes ($javac + @('-cp', 'build/stubs', '-d', 'build/dev-classes')) ($core + (Get-JavaSources src/main/java) + (Get-JavaSources src/dev/java))
Invoke-Javac test-classes ($javac + @('-cp', 'build/classes', '-d', 'build/test-classes')) (Get-JavaSources src/test/java)

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

# cameraunlock-core itself is C++: the tracker's receiver, the pose pipeline, the config owner,
# the hotkey poller and the log, built from the submodule as CameraUnlockCore.dll, which the jar
# loads from beside itself. The mod has no C++ of its own.
Invoke-Tool cmake @('-S', 'cameraunlock-core/cpp', '-B', 'build-core', '-A', 'x64', '-DCAMERAUNLOCK_BUILD_C=ON', '-DCAMERAUNLOCK_BUILD_TESTS=OFF')
Invoke-Tool cmake @('--build', 'build-core', '--config', 'Release', '--target', 'cameraunlock_c')
# For CoreTests alone, and left in build-core: a library that says it is from the release before.
Invoke-Tool cmake @('--build', 'build-core', '--config', 'Release', '--target', 'cameraunlock_c_other_abi')
Copy-Item -Force build-core/Release/CameraUnlockCore.dll build/
Copy-Item -Force build-core/Release/CameraUnlockCore.dll build/dev/
Write-Host 'Built build/CameraUnlockCore.dll'
