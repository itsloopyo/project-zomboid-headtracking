$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

# Both suites run whatever the first says, and the task fails once, after both have run.
& java --enable-native-access=ALL-UNNAMED -cp 'build/classes;build/test-classes' com.cameraunlock.zomboid.Tests
$tests = $LASTEXITCODE

# What the mod does with cameraunlock-core, in a process of its own: core reads one settings file a process.
& java --enable-native-access=ALL-UNNAMED -cp 'build/classes;build/test-classes' com.cameraunlock.zomboid.CoreTests
$core = $LASTEXITCODE

if ($tests -ne 0 -or $core -ne 0) { throw "tests failed: Tests exited with $tests, CoreTests with $core" }
