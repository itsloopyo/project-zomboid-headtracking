$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

function Invoke-Tool {
    param([string]$Tool, [string[]]$Arguments)
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
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
# cameraunlock-core as one jar, built from the submodule: its Java binding, the boot class the
# installed site JVM config names, and core's C++ compiled to WebAssembly and from there to JVM
# classes, with the Endive runtime they run on (cameraunlock-core/docs/core-jar.md). There is no
# native file: each of the mod's jars carries all of it, which the Workshop item needs, as the
# game's uploader refuses a DLL.
Invoke-Tool node @('cameraunlock-core/scripts/build-core-jar.mjs', 'build/cameraunlock-core.jar')
# Two jars from one source tree. The one that ships is compiled with src/shipped, whose DevKeys
# is empty. The development one is compiled with src/dev, whose DevKeys is the development
# keys, so none of that code is in a jar a player installs.
Invoke-Javac classes ($javac + @('-cp', 'build/stubs;build/cameraunlock-core.jar', '-d', 'build/classes')) ((Get-JavaSources src/main/java) + (Get-JavaSources src/shipped/java))
Invoke-Javac dev-classes ($javac + @('-cp', 'build/stubs;build/cameraunlock-core.jar', '-d', 'build/dev-classes')) ((Get-JavaSources src/main/java) + (Get-JavaSources src/dev/java))
foreach ($classes in @('build/classes', 'build/dev-classes')) {
    Push-Location $classes
    try { Invoke-Tool jar @('--extract', '--file', '../cameraunlock-core.jar') } finally { Pop-Location }
}
Invoke-Javac test-classes ($javac + @('-cp', 'build/classes', '-d', 'build/test-classes')) (Get-JavaSources src/test/java)

Copy-Item -Recurse -Force src/main/resources/* build/classes/
Copy-Item -Recurse -Force src/main/resources/* build/dev-classes/

@(
    'Premain-Class: com.cameraunlock.zomboid.Agent'
    'Agent-Class: com.cameraunlock.zomboid.Agent'
    'Can-Retransform-Classes: true'
    'Implementation-Title: Project Zomboid Head Tracking'
    "Implementation-Version: $version"
) | Set-Content -Encoding ascii build/MANIFEST.MF
Invoke-Tool jar @('--create', '--file', 'build/ProjectZomboidHeadTracking.jar', '--manifest', 'build/MANIFEST.MF', '-C', 'build/classes', '.')
Write-Host "Built build/ProjectZomboidHeadTracking.jar $version"
New-Item -ItemType Directory -Force build/dev | Out-Null
Invoke-Tool jar @('--create', '--file', 'build/dev/ProjectZomboidHeadTracking.jar', '--manifest', 'build/MANIFEST.MF', '-C', 'build/dev-classes', '.')
Write-Host "Built build/dev/ProjectZomboidHeadTracking.jar $version (development keys in)"
