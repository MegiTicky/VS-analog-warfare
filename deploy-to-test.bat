@echo off
setlocal EnableExtensions

set "MOD_ID=vs_analog_warfare"
set "TEST_MODS=C:\Users\lauya\curseforge\minecraft\Instances\JPCreate2.5\mods"
set "PROPERTIES=%~dp0gradle.properties"
set "BUILD_LIBS=%~dp0build\libs"

for /f "tokens=1,* delims==" %%A in ('findstr /b "mod_version=" "%PROPERTIES%"') do set "MOD_VERSION=%%B"
if not defined MOD_VERSION (
    echo Could not read mod_version from "%PROPERTIES%".
    exit /b 1
)

set "ARTIFACT=%MOD_ID%-%MOD_VERSION%.jar"
set "SOURCE=%BUILD_LIBS%\%ARTIFACT%"
if not exist "%SOURCE%" (
    echo Build artifact not found: "%SOURCE%"
    echo Build this VS2.3 branch first.
    exit /b 1
)
if not exist "%TEST_MODS%" (
    echo Test instance mods directory not found: "%TEST_MODS%"
    exit /b 1
)

del /q "%TEST_MODS%\%MOD_ID%-*.jar" 2>nul
copy /y "%SOURCE%" "%TEST_MODS%\%ARTIFACT%" >nul
if errorlevel 1 (
    echo Deployment failed.
    exit /b 1
)

echo Deployed "%ARTIFACT%" to JPCreate2.5.
exit /b 0
