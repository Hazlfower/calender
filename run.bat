@echo off
chcp 65001 > nul
cd /d "%~dp0"

set HAZ_JAVA=java
if exist "runtime\bin\java.exe" set HAZ_JAVA="runtime\bin\java.exe"

echo 컴파일 중...
javac -encoding UTF-8 -d bin *.java
if errorlevel 1 (
    echo 컴파일 실패! JDK가 설치되어 있는지 확인하세요.
    pause
    exit /b 1
)

%HAZ_JAVA% -cp bin Main
