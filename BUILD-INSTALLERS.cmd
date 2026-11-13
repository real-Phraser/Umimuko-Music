@echo off
cd /d "%~dp0"
echo Building native installer package for current OS...
call .\gradlew.bat packageDistributionForCurrentOS
set "buildResult=%errorlevel%"
if %buildResult% equ 0 (
    echo Done! Output located in build\compose\binaries\main\
) else (
    echo Build failed with error code %buildResult%
)
pause
exit /b %buildResult%
