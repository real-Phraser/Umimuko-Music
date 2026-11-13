@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-windows.ps1" -Mode Installer
set "buildResult=%errorlevel%"
pause
exit /b %buildResult%
