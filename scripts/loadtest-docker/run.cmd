@echo off
REM ============================================================
REM Docker-deploy one-click load test launcher (Windows)
REM
REM Purpose: bypass PowerShell execution policy for unsigned .ps1
REM so a single command runs the full Docker load-test flow and
REM the k6 exit code is forwarded to the caller.
REM
REM NOTE: keep this file ASCII-only. cmd.exe reads batch files with
REM the OEM code page (e.g. 936); non-ASCII bytes get mis-tokenized
REM and print as "'xxx' is not recognized" noise. Put Chinese docs
REM and detailed usage in run.ps1.
REM
REM Usage:
REM   run.cmd                       default: 01-main.js, 20 VUs, 60s
REM   run.cmd -Vus 100 -Duration 2m
REM   run.cmd -Script 04-search.js -Vus 50
REM   run.cmd -SkipUp               stack already running, just run k6
REM   run.cmd -SkipUp -TearDown     run k6 then tear the stack down
REM   run.cmd -NoBuild              skip image rebuild on up
REM
REM Target: deploy/docker-compose.loadtest.yml (project blog-loadtest,
REM         --profile app), fully isolated from the production stack.
REM ============================================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" %*
exit /b %ERRORLEVEL%
