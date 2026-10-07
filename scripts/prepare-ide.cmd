@echo off
setlocal
cd /d "%~dp0.."
call scripts\env-local.cmd
rem Install the parent POM and shared library into the editor's default Maven repository.
rem No application JARs are repackaged, so running services are unaffected.
call mvn -B -pl cloud-runtime -am install -DskipTests
exit /b %errorlevel%
