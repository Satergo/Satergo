@echo off

rem Set local scope for the variables with Windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

set APP_HOME=%DIRNAME%..

rem Add default JVM options here. You can also use JAVA_OPTS and SATERGO_OPTS to pass JVM options to this script.
set JVM_OPTS={JVM_OPTS}

set JAVA_EXE=%APP_HOME%/bin/{WIN_JAVA_BINARY_NAME}.exe
set JAVA_EXE="%JAVA_EXE:"=%"

if exist %JAVA_EXE% goto init

goto fail

:init
rem Get command-line arguments, handling Windows variants
if not "%OS%" == "Windows_NT" goto win9xME_args

:win9xME_args
rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute
set CMD_LINE_ARGS=%*

:execute
set CLASSPATH="%APP_HOME:"=%/lib/*"
%JAVA_EXE% %JVM_OPTS% %JAVA_OPTS% %SATERGO_OPTS% -classpath %CLASSPATH% {MAIN_CLASS} %CMD_LINE_ARGS%
if "%OS%"=="Windows_NT" endlocal
exit