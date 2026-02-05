@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21
cd /d E:\ServerPLUGINS\Allay-YRDatabase
call gradlew.bat build --no-daemon
echo EXIT_CODE=%ERRORLEVEL%
