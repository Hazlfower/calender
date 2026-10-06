@echo off
cd /d "%~dp0"

set HAZ_JAVA=java
if exist "runtime\bin\java.exe" set HAZ_JAVA="runtime\bin\java.exe"

echo Compiling...
if exist bin rmdir /s /q bin
javac -encoding UTF-8 -d bin *.java
if errorlevel 1 (
    echo.
    echo [ERROR] Compile failed. Check that JDK 17+ is installed.
    pause
    exit /b 1
)

echo Starting...
%HAZ_JAVA% -Dfile.encoding=UTF-8 -cp bin Main
if errorlevel 1 pause
