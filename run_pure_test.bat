@echo off
cd /d "%~dp0"
call run_mvn.bat -Ppure test
