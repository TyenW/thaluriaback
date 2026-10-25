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
echo   THALURIA RPG - INICIANDO MOTOR BACKEND
echo ================================================================================

:: 1. Liberar a porta 8080 e processos anteriores
echo [1/4] Verificando e liberando porta 8080...
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { Get-NetTCPConnection -LocalPort 8080 -ErrorAction Ignore | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction Ignore }; Stop-Process -Name cloudflared -Force -ErrorAction Ignore; Remove-Item -Force tunnel.log -ErrorAction Ignore }" >nul 2>&1

:: 2. Sincronizar compilacao do codigo Java
echo [2/4] Sincronizando compilacao das classes Java...
if not exist "bin" mkdir bin
"%JAVAC_CMD%" -encoding UTF-8 -cp "lib/sqlite-jdbc.jar;lib/*;src" -d bin src/Main.java src/servidor/ServidorThaluriaAPI.java src/persistencia/*.java 2>nul
if errorlevel 1 (
    "%JAVAC_CMD%" -encoding UTF-8 -cp "lib/sqlite-jdbc.jar;src" -d bin src/Main.java src/servidor/ServidorThaluriaAPI.java 2>nul
)

:: 3. Escolha do Modo de Operacao
echo.
echo ================================================================================
echo   Como deseja iniciar?
echo     [1] Conectar a Vercel / Internet (Com Tunel HTTPS Cloudflare) [PADRAO]
echo     [2] Somente Local (http://localhost:8080)
echo ================================================================================
choice /c 12 /t 6 /d 1 /m "Selecione a opcao (iniciando modo 1 automaticamente em 6s): "
if errorlevel 2 goto modo_local

:modo_vercel
echo.
echo [3/4] Inicializando tunel Cloudflare para a Vercel...
powershell -NoProfile -ExecutionPolicy Bypass -File .\conectar_tunel.ps1
goto iniciar_java

:modo_local
echo.
echo [3/4] Modo local selecionado (acessivel apenas em http://localhost:8080).

:iniciar_java
echo [4/4] Iniciando Servidor API HTTP na porta 8080...
echo ================================================================================
echo Pressione Ctrl+C para encerrar o servidor e fechar o tunel.
echo ================================================================================
"%JAVA_CMD%" --enable-native-access=ALL-UNNAMED -cp "lib/*;bin" servidor.ServidorThaluriaAPI

:: Encerramento limpo
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { Stop-Process -Name cloudflared -Force -ErrorAction Ignore; Remove-Item -Force tunnel.log -ErrorAction Ignore }" >nul 2>&1
pause
