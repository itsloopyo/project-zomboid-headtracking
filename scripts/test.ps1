$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

& java --enable-native-access=ALL-UNNAMED -cp 'build/classes;build/test-classes' com.cameraunlock.zomboid.Tests
if ($LASTEXITCODE -ne 0) { throw "tests failed with exit code $LASTEXITCODE" }

# What the mod does with cameraunlock-core, in a process of its own: core reads one settings file a process.
& java --enable-native-access=ALL-UNNAMED -cp 'build/classes;build/test-classes' com.cameraunlock.zomboid.CoreTests
if ($LASTEXITCODE -ne 0) { throw "core tests failed with exit code $LASTEXITCODE" }
