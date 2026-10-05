$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

& java --enable-native-access=ALL-UNNAMED -cp 'build/classes;build/test-classes' com.cameraunlock.zomboid.Tests
if ($LASTEXITCODE -ne 0) { throw "tests failed with exit code $LASTEXITCODE" }

& build-native/Release/config_tests.exe
if ($LASTEXITCODE -ne 0) { throw "config tests failed with exit code $LASTEXITCODE" }
