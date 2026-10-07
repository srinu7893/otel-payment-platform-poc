@echo off
rem This project requires Java 21; preserve the corporate Java 11 outside this shell.
set "JAVA_HOME=C:\Program Files\Zulu\zulu-21"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" set "MAVEN_HOME=C:\Program Files\apache-maven-3.9.16"
if not defined PG_HOME set "PG_HOME=C:\Program Files\PostgreSQL\17"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PG_HOME%\bin;%SystemRoot%\System32;%PATH%"
