@echo off
cd /d "%~dp0"
call mvn clean verify %*
if errorlevel 1 exit /b 1
