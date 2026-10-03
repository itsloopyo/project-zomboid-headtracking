$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

& java -cp 'build/classes;build/test-classes' com.cameraunlock.zomboid.Tests
if ($LASTEXITCODE -ne 0) { throw "tests failed with exit code $LASTEXITCODE" }
