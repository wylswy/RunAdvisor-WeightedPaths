@echo off
setlocal
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
if not defined M2_HOME set "M2_HOME=C:\STS-Modding\apache-maven-3.9.6"
set "MAVEN_HOME=%M2_HOME%"
set "PATH=%JAVA_HOME%\bin;%M2_HOME%\bin;%PATH%"
cd /d "%~dp0"
echo === maven %* ===
call "%M2_HOME%\bin\mvn.cmd" %*