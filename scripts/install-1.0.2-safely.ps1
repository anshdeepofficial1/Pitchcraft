# Install a verified v1.0.2 over the EXISTING 1.0.1 without deleting app data.
# This script refuses to run if version, package or signing cert differs.
# Usage (PowerShell): .\scripts\install-1.0.2-safely.ps1 -Apk "C:\Downloads\app-debug.apk"
param(
    [Parameter(Mandatory=$true)]
    [string]$Apk
)
$ErrorActionPreference = "Stop"
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$checker = Join-Path $PSScriptRoot "check-android-upgrade.ps1"
if (-not (Test-Path -LiteralPath $checker -PathType Leaf)) {
    throw "Safety checker is missing: $checker. Refusing to install."
}
Write-Host "Checking installed package, signing certificate and version (read-only)..." -ForegroundColor Cyan
& $checker -NewApk $apkPath
if ($LASTEXITCODE -ne 0) {
    throw "Safety check failed; no installation attempted."
}
Write-Host "Performing IN-PLACE install only. No uninstall, no data clearing." -ForegroundColor Cyan
# Capture every ADB error message, never follow a signature mismatch with an uninstall.
& adb install -r $apkPath
if ($LASTEXITCODE -ne 0) {
    throw "Android rejected the in-place update. EXISTING DATA IS UNTOUCHED. Read the exact INSTALL_FAILED_... error above. Do NOT uninstall."
}
Write-Host "Update installed in place. Verify People names, cover photos and scan progress." -ForegroundColor Green
