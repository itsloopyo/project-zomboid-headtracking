# Starts every installed copy of the game with the built agent loaded, waits,
# stops it and prints the agent's log. Development only: nothing is written to
# the install except the log the agent itself creates.
param(
    [int]$Seconds = 90
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $root 'cameraunlock-core\powershell\GamePathDetection.psm1') -Force

$jar = Join-Path $root 'build\ProjectZomboidHeadTracking.jar'
if (-not (Test-Path $jar)) { throw "$jar is missing, run 'pixi run build'" }

$gamePaths = @(Find-AllGamePaths -GameId 'project-zomboid')
if ($gamePaths.Count -eq 0) { throw 'no installed copy of Project Zomboid was found' }

# JAVA_TOOL_OPTIONS is split on spaces, so a checkout under a path with one needs the quotes.
$options = "-javaagent:`"$jar`""

foreach ($game in $gamePaths) {
    Write-Host "== $game"
    $log = Join-Path $game 'ProjectZomboidHeadTracking.log'
    $savedPath = $env:PATH
    # The JVM loads instrument.dll for a Java agent, and that DLL's own imports
    # (jli.dll, java.dll) resolve through PATH when the game's launcher starts
    # the JVM. Another Java on PATH then fails the launch, so the bundled
    # runtime goes first.
    $env:PATH = "$(Join-Path $game 'jre64\bin');$savedPath"
    $env:JAVA_TOOL_OPTIONS = $options
    try {
        $process = Start-Process -FilePath (Join-Path $game 'ProjectZomboid64.exe') -WorkingDirectory $game -PassThru
    } finally {
        $env:PATH = $savedPath
        $env:JAVA_TOOL_OPTIONS = $null
    }

    $process.WaitForExit($Seconds * 1000) | Out-Null
    if ($process.HasExited) {
        Write-Host "the game exited by itself with code $($process.ExitCode)"
    } else {
        Stop-Process -Id $process.Id -Force
        Write-Host "stopped after $Seconds seconds"
    }
    Get-Content $log
}
