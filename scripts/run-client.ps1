Set-Location (Join-Path $PSScriptRoot '..')
if (-not $env:SOCIAL_TRUSTSTORE_PASSWORD -and (Test-Path -LiteralPath 'config/tls/password.local')) {
    $env:SOCIAL_TRUSTSTORE_PASSWORD = (Get-Content -LiteralPath 'config/tls/password.local' -Raw).Trim()
}
$clientLaunchApiKey = $env:OPENAI_API_KEY
$clientLaunchTlsPassword = $env:SOCIAL_TLS_PASSWORD
try {
    Remove-Item Env:OPENAI_API_KEY,Env:SOCIAL_TLS_PASSWORD -ErrorAction SilentlyContinue
    java '-Dfile.encoding=UTF-8' -jar 'client/target/client-1.0.0-SNAPSHOT-shaded.jar'
} finally {
    $env:OPENAI_API_KEY = $clientLaunchApiKey
    $env:SOCIAL_TLS_PASSWORD = $clientLaunchTlsPassword
}
