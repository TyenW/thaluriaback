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

# 5. Exibir banner de conexão para o Mestre
if ($url) {
    Set-Clipboard -Value $url -ErrorAction SilentlyContinue
    Write-Host ""
    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host "  URL PUBLICA PARA A VERCEL ATIVA COM SUCESSO!" -ForegroundColor Green
    Write-Host "  Link HTTPS: $url" -ForegroundColor White -BackgroundColor DarkGreen
    Write-Host ""
    Write-Host "  (O link acima ja foi copiado para a sua Area de Transferencia - Ctrl+V)" -ForegroundColor Yellow
    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host "  INSTRUCOES PARA A VERCEL:" -ForegroundColor Cyan
    Write-Host "  1. Abra seu projeto na Vercel: https://vercel.com" -ForegroundColor Gray
    Write-Host "  2. Va em: Settings -> Environment Variables" -ForegroundColor Gray
    Write-Host "  3. Crie ou atualize: VITE_API_URL = $url" -ForegroundColor Gray
    Write-Host "  4. Faca Redeploy na Vercel (aba Deployments -> Redeploy)" -ForegroundColor Gray
    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host "  [AVISO] Tunel iniciado. Se o link nao aparecer, confira o arquivo tunnel.log." -ForegroundColor Yellow
}
