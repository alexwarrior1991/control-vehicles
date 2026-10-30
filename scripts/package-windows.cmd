@echo off
rem Builds the Windows application of Control Vehicles with its own Java:
rem   - with WiX Toolset on the PATH: an .msi installer (Start menu entry and desktop shortcut);
rem   - without it: a folder with "Control Vehicles.exe" that runs as it is (copy the whole folder).
rem Needs a JDK 21 or newer (jpackage comes with the JDK, not with a JRE). Run it from anywhere.
setlocal
cd /d "%~dp0.."

where jpackage >nul 2>nul
if errorlevel 1 (
    echo jpackage was not found: install a JDK 21 or newer and put its bin folder on the PATH.
    exit /b 1
)

echo Building target\control-vehicles.jar ...
call mvnw.cmd -q -DskipTests package
if errorlevel 1 exit /b 1

rem jpackage takes the whole input folder, so the jar goes alone into its own.
if exist target\dist rmdir /s /q target\dist
if exist target\installer rmdir /s /q target\installer
mkdir target\dist
copy /y target\control-vehicles.jar target\dist\ >nul

set TYPE=app-image
set EXTRA=
where wix >nul 2>nul && set TYPE=msi
where candle >nul 2>nul && set TYPE=msi
if "%TYPE%"=="msi" set EXTRA=--win-menu --win-shortcut --win-dir-chooser --win-upgrade-uuid 6f1d2c1e-3b8a-4c55-9a0e-2f6b7c9d4e10

echo Packaging (%TYPE%) ...
jpackage --name "Control Vehicles" --app-version 1.0.0 --vendor "Control Vehicles" ^
    --input target\dist --main-jar control-vehicles.jar ^
    --main-class com.alejandro.controlvehicles.app.Launcher ^
    --dest target\installer --type %TYPE% %EXTRA%
if errorlevel 1 exit /b 1

echo.
if "%TYPE%"=="msi" (
    echo Installer ready in target\installer
) else (
    echo Application ready in "target\installer\Control Vehicles": run "Control Vehicles.exe".
    echo Install WiX Toolset to get an .msi installer instead.
)
endlocal
