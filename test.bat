@echo off
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
if not exist target\test-classes mkdir target\test-classes
javac --release 17 -encoding UTF-8 -cp target/classes -d target/test-classes src/test/java/org/example/RoseRenderingCheck.java
if errorlevel 1 exit /b 1
java -Djava.awt.headless=true -cp "target/classes;target/test-classes" org.example.RoseRenderingCheck
