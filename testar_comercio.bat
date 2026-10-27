@echo off
title Thaluria RPG - Teste do Comercio
chcp 65001 > nul

set "JAVA_CMD=java"
set "JAVAC_CMD=javac"
if exist "C:\JavaJDK\bin\java.exe" (
    set "JAVA_CMD=C:\JavaJDK\bin\java.exe"
    set "JAVAC_CMD=C:\JavaJDK\bin\javac.exe"
) else if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
    )
)

echo ================================================================================
echo   Testando o sistema de comercio (usa um banco proprio; o thaluria.db nao e tocado)
echo ================================================================================

if not exist "bin" mkdir bin
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib/*;src" -d bin src/teste/TesteComercioVivo.java
if errorlevel 1 (
    echo Falha ao compilar o teste.
    pause
    exit /b 1
)
"%JAVA_CMD%" --enable-native-access=ALL-UNNAMED -Dstdout.encoding=UTF-8 -cp "lib/*;bin" teste.TesteComercioVivo
pause
