@echo off
echo Building LumaSG Plugin...
echo.

REM Clean previous builds
call gradlew clean

REM Build the plugin
call gradlew build

REM Check if build was successful
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Build successful! JAR file created at:
    echo build\libs\LumaSG-1.0.0.jar
    echo.
    echo To deploy:
    echo 1. Stop your server
    echo 2. Copy the JAR to your plugins folder
    echo 3. Start your server
    echo.
) else (
    echo.
    echo Build failed! Check the output above for errors.
    echo.
)

pause 