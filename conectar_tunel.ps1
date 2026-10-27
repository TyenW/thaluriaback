# Conexão automática de Túnel HTTPS Cloudflare para Thalúria RPG
Write-Host ""
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "  [TUNEL VERCEL] Preparando conexao segura HTTPS via Cloudflare..." -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

# 1. Baixar executável oficial caso ainda não exista
if (-not (Test-Path "cloudflared.exe")) {
    Write-Host "  -> Baixando executavel oficial cloudflared.exe..." -ForegroundColor Yellow
    & curl.exe -L -s -o cloudflared.exe https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe
}

# 2. Limpar processos e logs anteriores
Stop-Process -Name cloudflared -Force -ErrorAction SilentlyContinue
Remove-Item -Force tunnel.log -ErrorAction SilentlyContinue

# 3. Iniciar o túnel em segundo plano
Write-Host "  -> Estabelecendo tunel apontando para http://localhost:8080..." -ForegroundColor Yellow
$proc = Start-Process -FilePath ".\cloudflared.exe" -ArgumentList "tunnel --url http://localhost:8080 --logfile tunnel.log" -WindowStyle Hidden -PassThru

# 4. Aguardar captura da URL pública
$url = $null
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Seconds 1
    if (Test-Path tunnel.log) {
        $match = Select-String -Path tunnel.log -Pattern 'https://[a-zA-Z0-9-]+\.trycloudflare\.com'
        if ($match) {
            $url = $match.Matches[0].Value
            break
        }
    }
}

# 5. Sincronizacao automatica e exibicao do banner
if ($url) {
    Set-Clipboard -Value $url -ErrorAction SilentlyContinue

    # 5.1 Atualiza o arquivo local .env do frontend
    $envFront = "..\thaluriafront\.env"
    if (Test-Path "..\thaluriafront") {
        "VITE_API_URL=$url" | Out-File -FilePath $envFront -Encoding utf8 -Force
    }

    # 5.2 Se o projeto estiver autenticado e linkado na Vercel, atualiza e dispara redeploy automatico
    $vercelVinculado = Test-Path "..\thaluriafront\.vercel\project.json"
    $vercelStatus = "Nao vinculado (Execute publicar_na_vercel.bat uma unica vez)"

    if ($vercelVinculado) {
        Write-Host "  -> Vercel vinculada detectada! Atualizando VITE_API_URL na nuvem..." -ForegroundColor Yellow
        try {
            Push-Location "..\thaluriafront"
            & npx --yes vercel env add VITE_API_URL --environment production --value "$url" --force --yes 2>$null
            Write-Host "  -> Disparando Redeploy automatico na Vercel em segundo plano..." -ForegroundColor Yellow
            Start-Process -FilePath "cmd.exe" -ArgumentList "/c npx vercel redeploy --no-wait" -WorkingDirectory "..\thaluriafront" -WindowStyle Hidden
            $vercelStatus = "Sincronizado automaticamente na Vercel!"
            Pop-Location
        } catch {
            if ($pwd.Path -like "*thaluriafront*") { Pop-Location }
        }
    }

    Write-Host ""
    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host "  MOTOR ONLINE COM TUNEL HTTPS ATIVO!" -ForegroundColor Green
    Write-Host "  URL da API: $url" -ForegroundColor White -BackgroundColor DarkGreen
    Write-Host "  Status Vercel: $vercelStatus" -ForegroundColor Cyan
    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "  OPCAO 1: LINK INSTANTANEO (ZERO ESPERA - RECOMENDADO)" -ForegroundColor Yellow
    Write-Host "  Os jogadores conectam na hora sem esperar a Vercel compilar:" -ForegroundColor Gray
    Write-Host "  https://SEU-APP.vercel.app/?servidor=$url" -ForegroundColor White
    Write-Host ""
    if (-not $vercelVinculado) {
        Write-Host "  OPCAO 2: ATUALIZACAO 100% AUTOMATICA NA VERCEL" -ForegroundColor Cyan
        Write-Host "  Para que o script atualize a Vercel sozinho toda vez que abrir o servidor:" -ForegroundColor Gray
        Write-Host "  1. Feche o servidor e va na pasta 'thaluriafront'" -ForegroundColor Gray
        Write-Host "  2. Execute: publicar_na_vercel.bat (faca o login na Vercel 1 unica vez)" -ForegroundColor Gray
        Write-Host "  Apos isso, toda vez que abrir o servidor, a Vercel sera atualizada sozinha!" -ForegroundColor Gray
        Write-Host "================================================================================" -ForegroundColor Green
    }
    Write-Host "  (A URL $url ja foi copiada para sua Area de Transferencia - Ctrl+V)" -ForegroundColor DarkYellow
    Write-Host ""
} else {
    Write-Host "  [AVISO] Tunel iniciado. Se o link nao aparecer, confira o arquivo tunnel.log." -ForegroundColor Yellow
}
