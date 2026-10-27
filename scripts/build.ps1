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
# What takes longest to make and changes least, the core jar and the art jars, is kept here between
# builds under a key of what it is made from, and made again only when that changes.
New-Item -ItemType Directory -Force build-cache | Out-Null

function Get-Key {
    param([string]$Text)
    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    [BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash($bytes)).Replace('-', '').Substring(0, 16)
}

# Made into the cache under a name of its own and moved into place whole, so a build stopped
# half way leaves no jar there to be taken for a finished one.
$script:cachedNow = @()
function Use-Cached {
    param([string]$Cached, [string]$Target, [scriptblock]$Make)
    $script:cachedNow += (Split-Path -Leaf $Cached)
    if (-not (Test-Path $Cached)) {
        $making = "$Cached.making"
        if (Test-Path $making) { Remove-Item -Force $making }
        & $Make $making
        Move-Item -Force $making $Cached
    }
    New-Item -ItemType HardLink -Path $Target -Target (Resolve-Path $Cached).Path | Out-Null
}

$javac = @('--release', '25', '-Xlint:all', '-Werror')
Invoke-Javac stubs ($javac + @('-d', 'build/stubs')) (Get-JavaSources stubs)
# cameraunlock-core as one jar, built from the submodule: its Java binding, the boot class the
# installed site JVM config names, and core's C++ compiled to WebAssembly and from there to JVM
# classes, with the Endive runtime they run on (cameraunlock-core/docs/core-jar.md). There is no
# native file: each of the mod's jars carries all of it, which the Workshop item needs, as the
# game's uploader refuses a DLL.
# Kept by the core commit and the script, and only while core has no edits of its own.
$coreCommit = (git -C cameraunlock-core rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'git could not read the cameraunlock-core commit' }
$coreEdits = git -C cameraunlock-core status --porcelain
if ($LASTEXITCODE -ne 0) { throw 'git could not read whether cameraunlock-core has edits' }
if ($coreEdits) {
    Invoke-Tool node @('cameraunlock-core/scripts/build-core-jar.mjs', 'build/cameraunlock-core.jar')
} else {
    $coreKey = Get-Key "$coreCommit $(node --version)"
    Use-Cached "build-cache/cameraunlock-core-$coreKey.jar" build/cameraunlock-core.jar {
        param($out) Invoke-Tool node @('cameraunlock-core/scripts/build-core-jar.mjs', $out)
    }
}
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

# Every entry is stamped with one fixed time, so a jar built twice from the same files is the same
# bytes. The Workshop and the launcher both download only what changed, and a jar stamped with
# the time of the build is all changed.
$stamp = '1980-01-01T00:00:02Z'

# The art goes in a jar of its own a folder of src/main/resources, beside the code jar, whose
# Class-Path names them: the class loader that reads the code jar reads them as part of it. A code
# change then leaves the art jars as they were. The names are relative to the code jar, so the art
# jars sit in build/ and in build/dev/ alike, where the rig and the development deploy load them.
$artFolders = @(Get-ChildItem -Directory src/main/resources | ForEach-Object Name | Sort-Object)
$looseResources = @(Get-ChildItem -File src/main/resources)
if ($looseResources.Count -gt 0) { throw "src/main/resources holds files outside a folder, which no art jar takes: $($looseResources.Name -join ', ')" }
New-Item -ItemType Directory -Force build/dev | Out-Null
# Each art jar is kept by the names, sizes and times of the files in its folder: an edit, an added
# or a removed file changes the key, and a jar made of the same files is the same bytes.
$artJars = foreach ($folder in $artFolders) {
    $name = "ProjectZomboidHeadTracking-$folder.jar"
    $files = Get-ChildItem -Recurse -File "src/main/resources/$folder" | Sort-Object FullName |
        ForEach-Object { "$($_.FullName.Substring($PWD.Path.Length)) $($_.Length) $($_.LastWriteTimeUtc.Ticks)" }
    $artKey = Get-Key (($files -join "`n") + " $stamp")
    Use-Cached "build-cache/$folder-$artKey.jar" "build/$name" {
        param($out) Invoke-Tool jar @('--create', '--file', $out, '--no-manifest', '--date', $stamp, '-C', 'src/main/resources', $folder)
    }
    New-Item -ItemType HardLink -Path "build/dev/$name" -Target (Resolve-Path "build/$name").Path | Out-Null
    $name
}
Write-Host "Built $($artJars.Count) art jars in build: $($artJars -join ', ')"
# What this build did not use is what an older one made, and goes: the cache holds one of each.
Get-ChildItem -File build-cache | Where-Object { $script:cachedNow -notcontains $_.Name } | Remove-Item -Force

@(
    'Premain-Class: com.cameraunlock.zomboid.Agent'
    'Agent-Class: com.cameraunlock.zomboid.Agent'
    'Can-Retransform-Classes: true'
    'Implementation-Title: Project Zomboid Head Tracking'
    "Implementation-Version: $version"
    # A manifest line holds at most 72 bytes, so one jar a line: a line led by a space continues
    # the one before it, and the second space separates the names.
    "Class-Path: $($artJars[0])"
    ($artJars | Select-Object -Skip 1 | ForEach-Object { "  $_" })
) | Set-Content -Encoding ascii build/MANIFEST.MF

function New-CodeJar {
    param([string]$File, [string]$Classes)
    $entries = Get-ChildItem $Classes | Where-Object { $artFolders -notcontains $_.Name } | Sort-Object Name | ForEach-Object { '-C', $Classes, $_.Name }
    Invoke-Tool jar (@('--create', '--file', $File, '--manifest', 'build/MANIFEST.MF', '--date', $stamp) + $entries)
}
New-CodeJar build/ProjectZomboidHeadTracking.jar build/classes
Write-Host "Built build/ProjectZomboidHeadTracking.jar $version"
New-CodeJar build/dev/ProjectZomboidHeadTracking.jar build/dev-classes
Write-Host "Built build/dev/ProjectZomboidHeadTracking.jar $version (development keys in)"
