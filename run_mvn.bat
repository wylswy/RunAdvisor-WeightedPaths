@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
set "M2_HOME=C:\STS-Modding\apache-maven-3.9.6"
set "MAVEN_HOME=%M2_HOME%"
set "PATH=%JAVA_HOME%\bin;%M2_HOME%\bin;%PATH%"
cd /d "C:\STS-Modding\RunAdvisorMod\WeightedPaths"
echo === maven %* ===
call "%M2_HOME%\bin\mvn.cmd" %*
