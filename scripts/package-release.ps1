$ErrorActionPreference='Stop'
$appDirectory=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $appDirectory
$releaseName='safechat-'+(Get-Date -Format 'yyyyMMdd-HHmmss')
$releaseDirectory=Join-Path $appDirectory ('release/'+$releaseName)
foreach ($jar in @('server/target/server-1.0.0-SNAPSHOT-shaded.jar','client/target/client-1.0.0-SNAPSHOT-shaded.jar')) {
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Build package trước khi phát hành.' }
}
New-Item -ItemType Directory -Path $releaseDirectory | Out-Null
foreach ($folder in @('server/target','client/target','server/config','qa')) { New-Item -ItemType Directory -Force -Path (Join-Path $releaseDirectory $folder) | Out-Null }
foreach ($module in @('common','server','client')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $releaseDirectory $module) | Out-Null
    Copy-Item -LiteralPath ($module+'/pom.xml') -Destination (Join-Path $releaseDirectory ($module+'/pom.xml'))
    Copy-Item -LiteralPath ($module+'/src') -Destination (Join-Path $releaseDirectory ($module+'/src')) -Recurse
}
foreach ($jar in @('server/target/server-1.0.0-SNAPSHOT-shaded.jar','client/target/client-1.0.0-SNAPSHOT-shaded.jar')) { Copy-Item -LiteralPath $jar -Destination (Join-Path $releaseDirectory $jar) }
Copy-Item -LiteralPath 'server/config/social.properties' -Destination (Join-Path $releaseDirectory 'server/config/social.properties')
Copy-Item -LiteralPath 'pom.xml','.gitignore','README.md','plan.md','workflow.md' -Destination $releaseDirectory
Copy-Item -LiteralPath 'scripts','docs' -Destination $releaseDirectory -Recurse
if (Test-Path -LiteralPath 'server/target/qa/performance.json') { Copy-Item -LiteralPath 'server/target/qa/performance.json' -Destination (Join-Path $releaseDirectory 'qa/performance.json') }
if (Test-Path -LiteralPath 'qa/safechat-smoke/evidence.json') { Copy-Item -LiteralPath 'qa/safechat-smoke/evidence.json' -Destination (Join-Path $releaseDirectory 'qa/smoke-evidence.json') }
foreach ($module in @('common','server','client')) {
    $qaDirectory=Join-Path $releaseDirectory ('qa/'+$module)
    New-Item -ItemType Directory -Force -Path $qaDirectory | Out-Null
    Get-ChildItem -LiteralPath ($module+'/target/surefire-reports') -Filter 'TEST-*.xml' -File | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $qaDirectory }
}
$sensitive=Get-ChildItem -LiteralPath $releaseDirectory -File -Recurse | Where-Object { $_.Extension -in @('.p12','.jks','.db','.local') -or $_.Name -like '.env*' }
if ($sensitive) { throw 'Gói có file nhạy cảm ngoài phạm vi phát hành.' }
$prefix=$releaseDirectory.TrimEnd('\','/')+[IO.Path]::DirectorySeparatorChar
$lines=Get-ChildItem -LiteralPath $releaseDirectory -File -Recurse | Sort-Object FullName | ForEach-Object {
    if (-not $_.FullName.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)) { throw 'File nằm ngoài release.' }
    (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+$_.FullName.Substring($prefix.Length).Replace('\','/')
}
Set-Content -LiteralPath (Join-Path $releaseDirectory 'checksums.sha256') -Value $lines -Encoding utf8
$zipPath=$releaseDirectory+'.zip'
Compress-Archive -LiteralPath $releaseDirectory -DestinationPath $zipPath
Write-Output ('SafeChat release: '+$zipPath)
Write-Output 'TLS keys, API keys, databases, uploaded media and Maven cache are excluded.'
