@echo off
cd /d "%~dp0"
if not exist target\classes mkdir target\classes
javac --release 17 -encoding UTF-8 -d target/classes src/main/java/org/example/*.java
if errorlevel 1 exit /b 1
jar --create --file abandoned-rose-symbolism.jar --main-class org.example.RoseDrawing -C target/classes .
