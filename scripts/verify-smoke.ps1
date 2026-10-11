$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Join-Path $PSScriptRoot '..')
if (-not (Test-Path -LiteralPath 'config/tls/password.local')) { throw 'Chạy init-local.ps1 trước.' }
$env:SOCIAL_TLS_PASSWORD=(Get-Content -LiteralPath 'config/tls/password.local' -Raw).Trim()
$smokeClasspath='server/target/server-1.0.0-SNAPSHOT-shaded.jar;client/target/client-1.0.0-SNAPSHOT-shaded.jar'
New-Item -ItemType Directory -Force -Path 'qa' | Out-Null
javac -encoding UTF-8 -cp $smokeClasspath -d qa scripts/qa/SafeChatSmoke.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java '-Dfile.encoding=UTF-8' -cp ('qa;'+$smokeClasspath) SafeChatSmoke .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
