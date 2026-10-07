@echo off
setlocal
cd /d "%~dp0.."
set POC_GATEWAY_PORT=18081
node scripts/local-poc.mjs check
exit /b %errorlevel%
