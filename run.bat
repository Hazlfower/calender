@echo off
rem ------------------------------------------------------------
rem  HazCalendar: double-click to start.
rem  Rebuilds HazCalendar.jar only when a .java file is newer.
rem ------------------------------------------------------------
setlocal
cd /d "%~dp0"

if not exist HazCalendar.jar goto build
powershell -NoProfile -ExecutionPolicy Bypass -Command "$j=(Get-Item 'HazCalendar.jar').LastWriteTime; if (Get-ChildItem -Filter *.java | Where-Object { $_.LastWriteTime -gt $j }) { exit 1 } else { exit 0 }" >nul 2>nul
if errorlevel 1 goto build
goto launch

:build
call build.bat
if errorlevel 1 goto failed

:launch
set "HAZ_JAVAW=javaw"
where javaw >nul 2>nul
if errorlevel 1 if defined JAVA_HOME set "HAZ_JAVAW=%JAVA_HOME%\bin\javaw.exe"
if exist "runtime\bin\javaw.exe" set "HAZ_JAVAW=%~dp0runtime\bin\javaw.exe"
start "" "%HAZ_JAVAW%" -Dfile.encoding=UTF-8 -jar "%~dp0HazCalendar.jar"
if errorlevel 1 goto nojava
exit /b 0

:nojava
echo [ERROR] Java 17 or newer is needed. https://adoptium.net
pause
exit /b 1

:failed
pause
exit /b 1
