@echo off
setlocal
cd /d "%~dp0.."
call scripts\env-local.cmd
set "POC_NATIVE=true"
set "OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:14318"
if "%~1"=="support" goto support
if "%~1"=="resilience" goto resilience
artifacts\native\python\python.exe scripts\e2e\synthetic-journey.py
exit /b %errorlevel%
:support
artifacts\native\python\python.exe scripts\e2e\support-acceptance.py
exit /b %errorlevel%
:resilience
set "RESILIENCE=true"
artifacts\native\python\python.exe scripts\e2e\otel_acceptance.py
exit /b %errorlevel%
