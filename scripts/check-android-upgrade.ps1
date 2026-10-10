# Read-only preflight for a Pitchcraft 1.0.1 -> 1.0.2 in-place update.
# Requires adb and Android SDK build-tools. Does NOT install, uninstall, or clear data.
param(
    [Parameter(Mandatory = $true)] [string]$NewApk,
    [string]$OriginalApk = ""
)
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $NewApk -PathType Leaf)) {
    throw "Candidate APK not found: $NewApk"
}
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
$toolsDir = Get-ChildItem -LiteralPath (Join-Path $sdk "build-tools") -Directory |
    Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } -Descending |
    Select-Object -First 1
if (-not $toolsDir) { throw "Android SDK build-tools are missing." }
$aapt = Join-Path $toolsDir.FullName "aapt.exe"
$apksigner = Join-Path $toolsDir.FullName "apksigner.bat"
if (-not (Test-Path $aapt) -or -not (Test-Path $apksigner)) {
    throw "aapt.exe or apksigner.bat not found in $($toolsDir.FullName)"
}
function Read-Apk([string]$path) {
    $meta = (& $aapt dump badging $path | Select-Object -First 1)
    if ($LASTEXITCODE -ne 0 -or $meta -notmatch "package: name='([^']+)' versionCode='([0-9]+)'") {
        throw "Invalid APK package metadata: $path"
    }
    $packageId = $Matches[1]
    $versionCode = [int64]$Matches[2]
    $signing = (& $apksigner verify --print-certs $path)
    if ($LASTEXITCODE -ne 0) { throw "APK signature invalid: $path" }
    $cert = ($signing | Out-String)
    if ($cert -notmatch "Signer #1 certificate SHA-256 digest:\s*([0-9A-Fa-f]{64})") {
        throw "Cannot read APK signing certificate: $path"
    }
    return [PSCustomObject]@{ Package = $packageId; VersionCode = $versionCode; Cert = $Matches[1].ToLowerInvariant() }
}

$new = Read-Apk (Resolve-Path -LiteralPath $NewApk).Path
Write-Host "Candidate: $($new.Package), versionCode=$($new.VersionCode), certificate=$($new.Cert)"
if ($OriginalApk) {
    $oldPath = (Resolve-Path -LiteralPath $OriginalApk).Path
    $old = Read-Apk $oldPath
} else {
    $installed = & adb shell pm list packages $new.Package
    if ($LASTEXITCODE -ne 0 -or -not (($installed | Out-String) -match ("(?m)^package:" + [regex]::Escape($new.Package) + "\s*$"))) {
        throw "The candidate package ID is NOT installed. This APK would not update your existing app. Check the old app package with: adb shell pm list packages | findstr pitchcraft"
    }
    $remote = & adb shell pm path $new.Package | Select-Object -First 1
    if ($LASTEXITCODE -ne 0 -or -not ($remote -match '^package:(.+\.apk)\s*$')) {
        throw "Could not locate the installed APK."
    }
    $remotePath = $Matches[1].Trim()
    $oldPath = Join-Path ([IO.Path]::GetTempPath()) ("pitchcraft-installed-" + [guid]::NewGuid().ToString() + ".apk")
    try {
        & adb pull $remotePath $oldPath
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $oldPath)) {
            throw "Phone blocked reading its installed APK. Supply -OriginalApk with your actual installed v1.0.1 APK instead."
        }
        $old = Read-Apk $oldPath
    } finally {
        Remove-Item -LiteralPath $oldPath -ErrorAction SilentlyContinue
    }
}
Write-Host "Original:  $($old.Package), versionCode=$($old.VersionCode), certificate=$($old.Cert)"
if ($new.Package -ne $old.Package) { throw "UNSAFE: package IDs differ. No in-place update is possible with this APK." }
if ($new.Cert -ne $old.Cert) { throw "UNSAFE: signing certificates differ. The installed database cannot be kept by normal APK installation." }
if ($new.VersionCode -le $old.VersionCode) { throw "UNSAFE: new versionCode is not higher." }
Write-Host "PASS: Package, signer and version allow an in-place Android update." -ForegroundColor Green
Write-Host "No changes were made to the phone. To install manually: adb install -r `"$NewApk`""
