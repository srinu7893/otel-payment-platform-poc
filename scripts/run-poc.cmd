@echo off
setlocal
cd /d "%~dp0.."
call scripts\env-local.cmd
if /I "%~1"=="check" goto check
if /I "%~1"=="build" goto build
node scripts/native-poc.mjs %*
exit /b %errorlevel%
:check
call scripts\check-native.cmd
exit /b %errorlevel%
:build
call scripts\build-local.cmd
exit /b %errorlevel%
