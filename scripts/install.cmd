@echo off
:: ============================================
:: Project Zomboid Head Tracking - Install
:: ============================================
:: Thin wrapper - install body lives in cameraunlock-core/scripts/install-body-javaagent.cmd,
:: staged into the release ZIP's shared/ by Copy-SharedBundle. To change
:: install behaviour edit the body, not this wrapper.
::
:: Source of truth for everything below the CONFIG BLOCK:
:: cameraunlock-core/scripts/templates/install-wrapper-javaagent.cmd. Copy this
:: file to <mod>/scripts/install.cmd, fill in the CONFIG BLOCK, change nothing
:: else. scripts/conformance.ps1 checks that nothing else changed.
:: Keep every CONFIG BLOCK line, blank where it does not apply. A name the
:: block leaves out is not unset: it keeps whatever another mod's wrapper set
:: in the same console, and the body acts on that value.
::
:: Java agent: the game is a Java program whose own launcher reads its JVM
:: arguments from <Exe>.json next to the exe, and from <Exe>.site.json instead
:: when that is there. The mod is a jar the JVM loads as a Java agent, so
:: there is no loader, which is why FRAMEWORK_TYPE is JavaAgent. The jar goes
:: next to the exe, with the CameraUnlockCore.dll it loads from beside itself,
:: and the body writes the site config from the stock one, with core's boot
:: class, which is compiled into the jar, as the main class.
:: ============================================

:: --- CONFIG BLOCK ---
set "GAME_ID=project-zomboid"
set "MOD_DISPLAY_NAME=Project Zomboid Head Tracking"
:: Copied from plugins\ to the exe's folder on every install. Each .jar named
:: here is added to the classpath in the site config.
set "MOD_DLLS=ProjectZomboidHeadTracking.jar CameraUnlockCore.dll"
:: Files an older version of the mod put next to the exe under names this one
:: no longer uses. An install removes them once the new files are in place.
set "LEGACY_DLLS=ProjectZomboidHeadTracking.dll"
set "MOD_INTERNAL_NAME=ProjectZomboidHeadTracking"
set "MOD_VERSION=0.0.0"
set "STATE_FILE=.headtracking-state.json"
set "FRAMEWORK_TYPE=JavaAgent"
:: Files copied only when they are not already there, so an upgrade keeps
:: whatever the user tuned. A config never goes in MOD_DLLS, which is copied
:: unconditionally and would reset every key on every update.
set "MOD_SEED_FILES="
:: Post-install help text. `&echo ` starts each further line.
set "MOD_CONTROLS=Controls (nav cluster / chord):&echo   End      / Ctrl+Shift+Y  Toggle head tracking&echo   PageUp   / Ctrl+Shift+G  Cycle tracking mode: full, rotation only, position only&echo   PageDown / Ctrl+Shift+H  Toggle yaw mode: world-locked or camera-local&echo   Insert   / Ctrl+Shift+U  Cycle aim mode: sights locked, free look with a marker, stock sights&echo   Delete                   Toggle the first person view&echo   F9                       Cycle graphics mode: Low, Medium, High&echo   F8                       Cycle picture style: Vivid, Natural, Bleak"
:: --- END CONFIG BLOCK ---

:: Pin delayed expansion off before `%*` is expanded on the `call` below.
:: Under `cmd /V:ON`, or with DelayedExpansion=1 in
:: HKCU\Software\Microsoft\Command Processor, cmd.exe eats a `!` out of the
:: expanded line, and a real game path like C:\Games\Oh! My Game reaches the
:: body already mangled. The body pins expansion off at its own outer scope
:: too, but that is one `call` too late to save the argument it was handed.
setlocal disabledelayedexpansion

set "WRAPPER_DIR=%~dp0"
set "_BODY=%WRAPPER_DIR%shared\install-body-javaagent.cmd"
if not exist "%_BODY%" set "_BODY=%WRAPPER_DIR%..\cameraunlock-core\scripts\install-body-javaagent.cmd"
if not exist "%_BODY%" (
    echo ERROR: install-body-javaagent.cmd not found in shared\ or ..\cameraunlock-core\scripts\.
    echo If this is a release ZIP, re-download it from GitHub ^(corrupt installer^).
    echo If this is the dev tree, run: git submodule update --init --recursive
    exit /b 1
)
call "%_BODY%" %*
exit /b %errorlevel%
