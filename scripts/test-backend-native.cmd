@echo off
setlocal
cd /d "%~dp0.."
call scripts\env-local.cmd
set "DEBUG=false"
set "LOGGING_LEVEL_ROOT=INFO"
node scripts\prepare-native-tests.mjs
if errorlevel 1 exit /b 1
call mvn -B -Dpoc.native.integration=true test
exit /b %errorlevel%
