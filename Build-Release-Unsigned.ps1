param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$sourceRoot = $PSScriptRoot
$buildRoot = Join-Path ([IO.Path]::GetTempPath()) ('NetToggle-release-build-' + [guid]::NewGuid().ToString('N'))
if ($buildRoot -match '[^\x00-\x7F]') { throw 'TEMP must be an ASCII path for Windows AIDL.' }
New-Item -ItemType Directory -Path $buildRoot | Out-Null
foreach ($file in Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Force) {
    $relative = $file.FullName.Substring($sourceRoot.Length + 1)
    if ($relative -match '(^|[\\/])(build|\.gradle|\.kotlin|\.git|\.idea|artifacts)([\\/]|$)' -or
        $relative -eq 'local.properties' -or $file.Extension -in '.p12','.pfx','.jks','.keystore','.key','.pem','.dpapi' -or
        $file.Name -in 'keystore.properties','signing.properties','key-record.json','RECOVERY.txt' -or $file.Name -like '.env*') { continue }
    $target = Join-Path $buildRoot $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $target
}
$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $sdkRoot) { throw 'Set ANDROID_HOME to the installed SDK.' }
('sdk.dir=' + $sdkRoot.Replace('\', '/').Replace(':', '\:')) | Set-Content -LiteralPath (Join-Path $buildRoot 'local.properties') -Encoding ascii
$artifactRoot = Join-Path $sourceRoot 'artifacts\release'
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
$gradleArgs = @('--no-daemon','--console=plain','clean',':app:assembleRelease',
    ':app:test',':app:lintDebug',':app:lintRelease')
if ($Offline) { $gradleArgs += '--offline' }
Push-Location $buildRoot
try {
    & '.\gradlew.bat' @gradleArgs 2>&1 | Tee-Object -FilePath (Join-Path $artifactRoot 'gradle-release-build.log')
    $buildExit = $LASTEXITCODE
} finally { Pop-Location }
('Build directory: ' + $buildRoot) | Set-Content -LiteralPath (Join-Path $artifactRoot 'build-location.txt')
if ($buildExit -ne 0) { throw "Gradle failed (exit $buildExit). See artifacts/release/gradle-release-build.log" }
$metadata = Get-Content -LiteralPath (Join-Path $buildRoot 'app\build\outputs\apk\release\output-metadata.json') -Raw | ConvertFrom-Json
$version = $metadata.elements[0].versionName
if ($version -notmatch '^[A-Za-z0-9.+-]+$') { throw 'Unsafe versionName for artifact filename.' }
$unsigned = Join-Path $artifactRoot ('NetToggle-' + $version + '-unsigned.apk')
Copy-Item -LiteralPath (Join-Path $buildRoot 'app\build\outputs\apk\release\app-release-unsigned.apk') -Destination $unsigned
Copy-Item -LiteralPath (Join-Path $buildRoot 'app\build\outputs\apk\release\output-metadata.json') -Destination $artifactRoot
Copy-Item -LiteralPath (Join-Path $buildRoot 'app\build\reports') -Destination $artifactRoot -Recurse -Force
Copy-Item -LiteralPath (Join-Path $buildRoot 'app\build\test-results') -Destination $artifactRoot -Recurse -Force
Get-FileHash -LiteralPath $unsigned -Algorithm SHA256
Write-Output 'This APK is UNSIGNED and is not the installable release delivery.'
Write-Output "Unsigned APK and reports: $artifactRoot"
Write-Output "Temporary build copy retained: $buildRoot"
