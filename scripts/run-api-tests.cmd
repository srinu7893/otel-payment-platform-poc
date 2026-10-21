@echo off
setlocal
cd /d "%~dp0.."
node --use-system-ca scripts\run-api-tests.mjs %*
exit /b %ERRORLEVEL%
