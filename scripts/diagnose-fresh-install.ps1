# Diagnose a first-time APK install on your Android phone (Windows + USB debugging).
# Read-only checks; optional -TestInstall installs ONLY when the app is not present.
# NEVER uninstalls or clears any app data.
param(
    [Parameter(Mandatory=$true)] [string]$Apk,
    [switch]$TestInstall
)
$ErrorActionPreference = "Stop"
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$directories = Get-ChildItem (Join-Path $sdk "build-tools") -Directory | Sort-Object Name -Descending
$toolsDir = $directories | Select-Object -First 1
if (-not $toolsDir) { throw "Android SDK build-tools missing." }
$aapt = Join-Path $toolsDir.FullName "aapt.exe"
$signer = Join-Path $toolsDir.FullName "apksigner.bat"
if (-not (Test-Path $aapt) -or -not (Test-Path $signer)) { throw "Install Android SDK build-tools." }

$metadata = (& $aapt dump badging $apkPath)
if ($LASTEXITCODE -ne 0) { throw "Corrupt or invalid APK file." }
$first = $metadata | Select-Object -First 1
if ($first -notmatch "package: name='([^']+)'") { throw "APK package ID missing." }
$packageId = $Matches[1]
$minText = $metadata | Where-Object { $_ -match '^sdkVersion:' } | Select-Object -First 1
if ($minText -notmatch "sdkVersion:'(\d+)'") { throw "APK minimum Android API missing." }
$minApi = [int]$Matches[1]
& $signer verify --verbose --print-certs $apkPath
if ($LASTEXITCODE -ne 0) { throw "APK signature invalid." }
Write-Host "APK package: $packageId" -ForegroundColor Cyan
Write-Host "APK minimum Android API: $minApi"
$metaNative = $metadata | Where-Object { $_ -match '^native-code:' } | Select-Object -First 1
Write-Host "APK native support: $metaNative"

& adb get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Connect phone and approve USB debugging." }
$phoneApi = [int]((& adb shell getprop ro.build.version.sdk) | Out-String).Trim()
$phoneAbi = ((& adb shell getprop ro.product.cpu.abilist) | Out-String).Trim()
Write-Host "Phone Android API: $phoneApi"
Write-Host "Phone supported ABIs: $phoneAbi"
if ($phoneApi -lt $minApi) {
    throw "INSTALL_FAILED_OLDER_SDK: phone Android API $phoneApi is below required APK API $minApi."
}
$existing = (& adb shell pm list packages $packageId | Out-String)
if ($existing -match ("package:" + [regex]::Escape($packageId) + "(\r?\n|$)")) {
    throw "App with this package ID is ALREADY installed. Not attempting to replace or erase its existing data. Run safe upgrade checker instead."
}
if (-not $TestInstall) {
    Write-Host "Read-only checks passed. To test first-time installation add -TestInstall." -ForegroundColor Green
    return
}
Write-Host "Attempting only first-time adb install (never uninstall)..." -ForegroundColor Cyan
& adb install $apkPath
if ($LASTEXITCODE -ne 0) {
    throw "Installation failed. Copy the exact INSTALL_FAILED_* line above. No existing app was removed."
}
Write-Host "Success: Android reports the APK installed on this fresh phone." -ForegroundColor Green
