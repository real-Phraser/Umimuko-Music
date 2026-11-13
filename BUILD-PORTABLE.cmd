@echo off
cd /d "%~dp0"
echo Building portable distribution for current OS...
call .\gradlew.bat createDistributable
set "buildResult=%errorlevel%"
if %buildResult% equ 0 (
    echo Done! Output located in build\compose\binaries\main\app\
) else (
    echo Build failed with error code %buildResult%
)
pause
exit /b %buildResult%
