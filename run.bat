@echo off
chcp 65001 > nul
cd /d "%~dp0src"

set HAZ_JAVAC=javac
set HAZ_JAVA=java
if exist "%~dp0runtime\bin\java.exe" set HAZ_JAVA="%~dp0runtime\bin\java.exe"

echo 컴파일 중...
%HAZ_JAVAC% -encoding UTF-8 haz\*.java
if errorlevel 1 (
    echo 컴파일 실패
    pause
    exit /b 1
)

echo 실행 중...
%HAZ_JAVA% haz.Main
pause
