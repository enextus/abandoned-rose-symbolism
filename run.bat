@echo off
cd /d "%~dp0"
java -jar abandoned-rose-symbolism.jar %*
if errorlevel 1 pause
