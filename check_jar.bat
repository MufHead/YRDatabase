@echo off
echo Checking JAR contents...
echo.
"C:\Program Files\Java\jdk-21\bin\jar.exe" -tf "E:\ServerPLUGINS\Allay-YRDatabase\yrdatabase-allay\build\libs\yrdatabase-allay-1.0.0-SNAPSHOT.jar" > jar_contents.txt
echo Total files in JAR:
find /c /v "" jar_contents.txt
echo.
echo Checking for plugin.json:
find /i "plugin.json" jar_contents.txt
echo.
echo Checking for core classes:
find /i "yrdatabase/core" jar_contents.txt | more
echo.
echo Checking for Redis:
find /i "redis" jar_contents.txt | more
echo.
pause
