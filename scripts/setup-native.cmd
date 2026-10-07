@echo off
setlocal
cd /d "%~dp0.."
call scripts\env-local.cmd
node --use-system-ca scripts\download-support.mjs
if errorlevel 1 exit /b 1
node scripts\native-assets.mjs
if errorlevel 1 exit /b 1
node scripts\download-native.mjs
if errorlevel 1 exit /b 1
node scripts\download-native.mjs grafana
if errorlevel 1 exit /b 1
node scripts\local-poc.mjs agent
if errorlevel 1 exit /b 1
call scripts\build-local.cmd
if errorlevel 1 exit /b 1
pushd frontend
call npm ci --cache ..\artifacts\npm-cache
set "POC_RESULT=%errorlevel%"
popd
exit /b %POC_RESULT%
