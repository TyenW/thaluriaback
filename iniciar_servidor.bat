@echo off
title Thaluria RPG - Servidor Backend
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
echo   Iniciando Backend Thaluria RPG (Porta 8080)...
echo ================================================================================

:: 1. Liberar a porta 8080 automaticamente caso esteja ocupada por processo anterior
echo [1/3] Verificando e liberando porta 8080...
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { Get-NetTCPConnection -LocalPort 8080 -ErrorAction Ignore | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction Ignore } }" >nul 2>&1

:: 2. Sincronizar compilacao do codigo Java
echo [2/3] Sincronizando compilacao das classes Java...
if not exist "bin" mkdir bin
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib/sqlite-jdbc.jar;lib/*;src" -d bin src/Main.java src/servidor/ServidorThaluriaAPI.java src/persistencia/*.java 2>nul
if errorlevel 1 (
    "%JAVAC_CMD%" -encoding UTF-8 -cp "lib/sqlite-jdbc.jar;src" -d bin src/Main.java src/servidor/ServidorThaluriaAPI.java 2>nul
)

:: 3. Iniciar Servidor API
echo [3/3] Iniciando Servidor API HTTP na porta 8080...
echo ================================================================================
"%JAVA_CMD%" --enable-native-access=ALL-UNNAMED -cp "lib/*;bin" servidor.ServidorThaluriaAPI
pause
