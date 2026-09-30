@echo off
cd /d "%~dp0"
if exist target\classes rmdir /s /q target\classes
mkdir target\classes
javac --release 17 -encoding UTF-8 -d target/classes src/main/java/org/example/*.java
if errorlevel 1 exit /b 1
if exist src\main\resources xcopy /e /i /y src\main\resources\* target\classes\ >nul
jar --create --file abandoned-rose-symbolism.jar --main-class org.example.RoseDrawing -C target/classes .
if errorlevel 1 exit /b 1
