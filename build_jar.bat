@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21
cd /d E:\ServerPLUGINS\Allay-YRDatabase
call gradlew.bat :yrdatabase-allay:jar --no-daemon
echo.
echo JAR file location:
dir /b yrdatabase-allay\build\libs\*.jar
echo EXIT_CODE=%ERRORLEVEL%
