@echo off
setlocal
set "APP_HOME=%~dp0"
set "JAVA=%APP_HOME%runtime\bin\java.exe"
set "JAR=%APP_HOME%app\stock-analyzer.jar"

echo Stock Lens startup diagnostics
echo Application: %APP_HOME%
echo.

if not exist "%JAVA%" (
    echo ERROR: Bundled Java is missing: %JAVA%
    echo Extract the complete ZIP to a normal folder first.
    pause
    exit /b 1
)
if not exist "%JAR%" (
    echo ERROR: Application JAR is missing: %JAR%
    pause
    exit /b 1
)

"%JAVA%" -version
echo.
"%JAVA%" -Dfile.encoding=UTF-8 -Dstock.app.desktop=true -jar "%JAR%" --spring.profiles.active=desktop
set "RESULT=%ERRORLEVEL%"
echo.
echo Stock Lens exited with code %RESULT%.
pause
exit /b %RESULT%
