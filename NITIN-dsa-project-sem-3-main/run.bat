@echo off
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Starting the standalone browser version.
  start "Boardroom server" powershell.exe -NoProfile -ExecutionPolicy Bypass -NoExit -File "%~dp0Serve-Web.ps1"
  timeout /t 2 /nobreak >nul
  start "" http://localhost:8080
  exit /b
)
java --add-modules jdk.httpserver GameServer.java
