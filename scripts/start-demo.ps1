param([switch]$ConfigureAi)
$ErrorActionPreference = 'Stop'
$appDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $appDirectory
foreach ($jar in @('server/target/server-1.0.0-SNAPSHOT-shaded.jar','client/target/client-1.0.0-SNAPSHOT-shaded.jar')) {
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Chưa có JAR. Chạy scripts/build-all.ps1 trước.' }
}
if (-not (Test-Path -LiteralPath 'config/tls/server.p12')) { & (Join-Path $PSScriptRoot 'init-local.ps1') }
if (-not (Test-Path -LiteralPath 'data/social-network.db')) {
    $env:SOCIAL_DEMO_PASSWORD = 'SafeChat-Demo-2026'
    & (Join-Path $PSScriptRoot 'seed-demo.ps1')
}
$pidFile = Join-Path $appDirectory 'data/demo-server.pid'
if (Test-Path -LiteralPath $pidFile) {
    $existingServerId = [int](Get-Content -LiteralPath $pidFile -Raw)
    if (Get-Process -Id $existingServerId -ErrorAction SilentlyContinue) { throw 'Server demo đang chạy. Dùng stop-demo.ps1 nếu cần dừng.' }
}
if (Get-NetTCPConnection -LocalPort 5000 -State Listen -ErrorAction SilentlyContinue) { throw 'Cổng TCP 5000 đang được sử dụng.' }
New-Item -ItemType Directory -Force -Path 'data/demo-logs' | Out-Null
$env:SOCIAL_TLS_PASSWORD = (Get-Content -LiteralPath 'config/tls/password.local' -Raw).Trim()
$env:SOCIAL_TRUSTSTORE_PASSWORD = $env:SOCIAL_TLS_PASSWORD
if ($ConfigureAi) {
    $apiSecret = Read-Host 'Nhập OPENAI_API_KEY trên server (không hiển thị, không lưu vào tệp)' -AsSecureString
    $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($apiSecret)
    try { $env:OPENAI_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer) }
}
$javaExecutable = (Get-Command java.exe).Source
try {
    $serverProcess = Start-Process -FilePath $javaExecutable -ArgumentList @('-Dfile.encoding=UTF-8','-jar','server/target/server-1.0.0-SNAPSHOT-shaded.jar','server/config/social.properties') -WorkingDirectory $appDirectory -WindowStyle Hidden -RedirectStandardOutput 'data/demo-logs/server.out.log' -RedirectStandardError 'data/demo-logs/server.err.log' -PassThru
} finally {
    # The server inherits the key at process creation; clients must never inherit it.
    Remove-Item Env:OPENAI_API_KEY -ErrorAction SilentlyContinue
}
Set-Content -LiteralPath $pidFile -Value $serverProcess.Id
$ready = $false
for ($attempt=0; $attempt -lt 30; $attempt++) {
    $serverProcess.Refresh()
    if ($serverProcess.HasExited) { throw 'Server không khởi động được. Xem data/demo-logs/server.err.log.' }
    try { $probe = [Net.Sockets.TcpClient]::new(); $probe.Connect('127.0.0.1',5000); $probe.Dispose(); $ready=$true; break } catch { Start-Sleep -Milliseconds 200 }
}
if (-not $ready) { throw 'Server chưa sẵn sàng. Xem log và dùng stop-demo.ps1 để dừng.' }
$demoTlsPassword = $env:SOCIAL_TLS_PASSWORD
try {
    Remove-Item Env:SOCIAL_TLS_PASSWORD -ErrorAction SilentlyContinue
    for ($clientNumber=1; $clientNumber -le 2; $clientNumber++) {
        Start-Process -FilePath $javaExecutable -ArgumentList @('-Dfile.encoding=UTF-8','-jar','client/target/client-1.0.0-SNAPSHOT-shaded.jar') -WorkingDirectory $appDirectory -WindowStyle Hidden | Out-Null
    }
} finally { $env:SOCIAL_TLS_PASSWORD = $demoTlsPassword }
Write-Output 'SafeChat đã mở 2 client. Demo: demo_alice / demo_bob; mật khẩu SafeChat-Demo-2026.'
Write-Output 'Ảnh cần OPENAI_API_KEY trên server. Dừng server bằng scripts/stop-demo.ps1.'
