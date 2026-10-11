$ErrorActionPreference = 'Stop'
$appDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$pidFile = Join-Path $appDirectory 'data/demo-server.pid'
if (-not (Test-Path -LiteralPath $pidFile)) { Write-Output 'Không có PID server demo.'; exit }
$demoServerId = [int](Get-Content -LiteralPath $pidFile -Raw)
$serverInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$demoServerId"
if ($serverInfo) {
    if ($serverInfo.Name -ne 'java.exe' -or $serverInfo.CommandLine -notlike '*server/target/server-1.0.0-SNAPSHOT-shaded.jar*') { throw 'PID không còn trỏ đến server SafeChat; không dừng tiến trình.' }
    Stop-Process -Id $demoServerId
}
Remove-Item -LiteralPath $pidFile
Write-Output 'Đã dừng server demo. Database được giữ lại.'
