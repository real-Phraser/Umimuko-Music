@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-windows.ps1" -Mode Portable
set "buildResult=%errorlevel%"
pause
exit /b %buildResult%
