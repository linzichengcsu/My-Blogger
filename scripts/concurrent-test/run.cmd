@echo off
REM ============================================================
REM Concurrent correctness tests - one-click launcher (Windows)
REM
REM Purpose: bypass PowerShell's default execution policy for
REM unsigned .ps1 so a single command runs the tests and the
REM exit code is forwarded to the caller.
REM
REM NOTE: keep this file ASCII-only. cmd.exe reads batch files
REM with the OEM code page (e.g. 936), so any non-ASCII byte
REM (including UTF-8 Chinese) gets mis-tokenized and printed as
REM "'xxx' is not recognized" noise. Put Chinese docs in run.ps1.
REM
REM Usage:
REM   run.cmd                       default: H2 concurrency cases (no Docker)
REM   run.cmd -All                  H2 + MySQL concurrency (MySQL auto-skipped without Docker)
REM   run.cmd -MysqlOnly            MySQL concurrency only (requires Docker)
REM   run.cmd -Perf                 existing perf tests (entity mass-ops + repo batch insert)
REM   run.cmd -TestPattern <glob>   raw surefire -Dtest pattern (highest priority)
REM
REM Stress tuning (forwarded to Maven as -Dstress.*, no source changes needed):
REM   run.cmd -Scale 2              multiply ALL stress params by 2
REM   run.cmd -Threads 500          force all concurrency scenarios to 500 threads
REM   run.cmd -Profile high         preset: smoke(0.1x) default(1x) high(2x) extreme(5x)
REM   combinable: -Profile high -Scale 1.5  => 2 x 1.5 = 3x; -Threads overrides concurrency
REM ============================================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" %*
exit /b %ERRORLEVEL%
