param([switch]$Offline, [switch]$UseEnvironmentProxy)
$ErrorActionPreference = 'Stop'
$sourceRoot = $PSScriptRoot
$buildRoot = Join-Path ([IO.Path]::GetTempPath()) ('NetToggle-build-' + [guid]::NewGuid().ToString('N'))
if ($buildRoot -match '[^\x00-\x7F]') { throw 'TEMP must be an ASCII path for Windows AIDL.' }
New-Item -ItemType Directory -Path $buildRoot | Out-Null
# Build a disposable copy: Windows AIDL cannot reliably handle non-ASCII project paths.
foreach ($file in Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Force) {
    $relative = $file.FullName.Substring($sourceRoot.Length + 1)
    if ($relative -match '(^|[\\/])(build|\.gradle|\.kotlin|\.git|\.idea|artifacts)([\\/]|$)' -or
        $relative -eq 'local.properties' -or $file.Extension -in '.p12','.pfx','.jks','.keystore','.key','.pem','.dpapi' -or
        $file.Name -in 'keystore.properties','signing.properties','key-record.json','RECOVERY.txt' -or
        $file.Name -like '.env*') { continue }
    $target = Join-Path $buildRoot $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $target
}
$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $sdkRoot -and (Test-Path -LiteralPath (Join-Path $sourceRoot 'local.properties'))) {
    Copy-Item -LiteralPath (Join-Path $sourceRoot 'local.properties') -Destination (Join-Path $buildRoot 'local.properties')
} elseif ($sdkRoot) {
    ('sdk.dir=' + $sdkRoot.Replace('\', '/').Replace(':', '\:')) | Set-Content -LiteralPath (Join-Path $buildRoot 'local.properties') -Encoding ascii
}
$artifactRoot = Join-Path $sourceRoot 'artifacts'
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
$gradleArgs = @('--no-daemon', '--console=plain', 'clean', ':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
if ($Offline) { $gradleArgs += '--offline' }
if ($UseEnvironmentProxy) {
    $proxyAddress = $env:HTTPS_PROXY
    if (-not $proxyAddress) { $proxyAddress = $env:HTTP_PROXY }
    if (-not $proxyAddress) { throw 'No HTTPS_PROXY / HTTP_PROXY environment variable is set.' }
    $proxyUri = [uri]$proxyAddress
    if ($proxyUri.Scheme -ne 'http' -or $proxyUri.UserInfo) { throw 'Only credential-free HTTP proxy URLs are supported.' }
    $gradleArgs += @("-Dhttps.proxyHost=$($proxyUri.Host)", "-Dhttps.proxyPort=$($proxyUri.Port)",
        "-Dhttp.proxyHost=$($proxyUri.Host)", "-Dhttp.proxyPort=$($proxyUri.Port)")
}
Push-Location $buildRoot
try {
    & '.\gradlew.bat' @gradleArgs 2>&1 | Tee-Object -FilePath (Join-Path $artifactRoot 'gradle-build.log')
    $buildExit = $LASTEXITCODE
} finally { Pop-Location }
('Build directory: ' + $buildRoot) | Set-Content -LiteralPath (Join-Path $artifactRoot 'build-location.txt')
if ($buildExit -ne 0) { throw "Gradle failed (exit $buildExit). See artifacts/gradle-build.log" }
$metadata = Get-Content -LiteralPath (Join-Path $buildRoot 'app\build\outputs\apk\debug\output-metadata.json') -Raw | ConvertFrom-Json
$debugVersion = $metadata.elements[0].versionName -replace '-debug$', ''
if ($debugVersion -notmatch '^[A-Za-z0-9.+-]+$') { throw 'Unsafe versionName for artifact filename.' }
$debugApk = Join-Path $artifactRoot ('NetToggle-' + $debugVersion + '-debug.apk')
Copy-Item -LiteralPath (Join-Path $buildRoot 'app\build\outputs\apk\debug\app-debug.apk') -Destination $debugApk
Copy-Item -LiteralPath (Join-Path $buildRoot 'app\build\outputs\apk\debug\output-metadata.json') -Destination $artifactRoot
Copy-Item -LiteralPath (Join-Path $buildRoot 'app\build\reports') -Destination $artifactRoot -Recurse -Force
Copy-Item -LiteralPath (Join-Path $buildRoot 'app\build\test-results') -Destination $artifactRoot -Recurse -Force
Get-FileHash -LiteralPath $debugApk -Algorithm SHA256
Write-Output "APK and reports: $artifactRoot"
Write-Output "Temporary build copy retained: $buildRoot"
