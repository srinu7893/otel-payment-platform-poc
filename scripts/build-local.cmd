@echo off
setlocal
cd /d "%~dp0.."
call scripts\env-local.cmd
if not exist artifacts mkdir artifacts
java -version
call mvn -version
call mvn -B -Dmaven.repo.local=%CD%/artifacts/maven-cache package -DskipTests > artifacts\build.log 2>&1
exit /b %errorlevel%
