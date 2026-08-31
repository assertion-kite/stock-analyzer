@echo off
setlocal
set "APP_HOME=%~dp0"
set "JAVA=%APP_HOME%runtime\bin\javaw.exe"
set "JAR=%APP_HOME%app\stock-analyzer.jar"

if not exist "%JAVA%" goto missing_java
if not exist "%JAR%" goto missing_jar

start "Stock Lens" /D "%APP_HOME%" "%JAVA%" -Dfile.encoding=UTF-8 -Dstock.app.desktop=true -jar "%JAR%" --spring.profiles.active=desktop
exit /b 0

:missing_java
echo The bundled Java runtime is incomplete.
echo Please extract the whole ZIP before starting Stock Lens.
echo Missing: %JAVA%
pause
exit /b 1

:missing_jar
echo The Stock Lens application files are incomplete.
echo Missing: %JAR%
pause
exit /b 1
