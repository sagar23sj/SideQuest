<#
.SYNOPSIS
    Builds a signed release artifact for SideQuest — an App Bundle (.aab) for the
    Play Store, or an APK for direct sideloading.

.DESCRIPTION
    Verifies signing is configured (keystore.properties present), then runs the
    matching Gradle task and reports the output path:

      - Bundle (default): :app:bundleRelease  -> app-release.aab  (Play Store)
      - APK:              :app:assembleRelease -> app-release.apk  (sideload)

    Optionally bumps versionCode / versionName in app/build.gradle.kts before
    building, since Play requires a strictly increasing versionCode per upload.

.PARAMETER Apk
    Build an APK instead of an App Bundle.

.PARAMETER VersionName
    New versionName to write into app/build.gradle.kts (e.g. '1.1').

.PARAMETER VersionCode
    New integer versionCode to write into app/build.gradle.kts (must be greater
    than the currently published value).

.PARAMETER Clean
    Run a clean build (:app:clean) first.

.EXAMPLE
    ./scripts/release-build.ps1
    # Signed App Bundle for the Play Store.

.EXAMPLE
    ./scripts/release-build.ps1 -Apk
    # Signed APK for sideloading.

.EXAMPLE
    ./scripts/release-build.ps1 -VersionName '1.1' -VersionCode 2 -Clean
    # Bump version, clean, then build a signed bundle.

.NOTES
    Windows / PowerShell. Run scripts/release-keystore.ps1 once first.
    Reminder: the release build's API_BASE_URL in app/build.gradle.kts is a
    placeholder — set your real backend URL before shipping if you use it.
#>
[CmdletBinding()]
param(
    [switch]$Apk,
    [string]$VersionName,
    [int]$VersionCode,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "  [ok] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "  [warn] $msg" -ForegroundColor Yellow }

$RepoRoot     = Split-Path -Parent $PSScriptRoot
$Gradlew      = Join-Path $RepoRoot 'gradlew.bat'
$PropsPath    = Join-Path $RepoRoot 'keystore.properties'
$BuildGradle  = Join-Path $RepoRoot 'app\build.gradle.kts'

Write-Host "SideQuest - release build" -ForegroundColor Magenta

if (-not (Test-Path $Gradlew)) { throw "gradlew.bat not found at repo root." }
if (-not (Test-Path $PropsPath)) {
    throw "keystore.properties not found. Run scripts/release-keystore.ps1 first to set up signing."
}
Write-Ok "Signing config found (keystore.properties)."

# --- Optional version bump -------------------------------------------------
if ($VersionName -or $PSBoundParameters.ContainsKey('VersionCode')) {
    Write-Step "Updating version in app/build.gradle.kts"
    $gradleText = Get-Content $BuildGradle -Raw

    if ($PSBoundParameters.ContainsKey('VersionCode')) {
        $gradleText = [regex]::Replace($gradleText, 'versionCode\s*=\s*\d+', "versionCode = $VersionCode")
        Write-Ok "versionCode -> $VersionCode"
    }
    if ($VersionName) {
        $gradleText = [regex]::Replace($gradleText, 'versionName\s*=\s*"[^"]*"', "versionName = `"$VersionName`"")
        Write-Ok "versionName -> $VersionName"
    }
    Set-Content -Path $BuildGradle -Value $gradleText -Encoding UTF8
}

Push-Location $RepoRoot
try {
    if ($Clean) {
        Write-Step "Cleaning"
        & $Gradlew :app:clean --console=plain
        if ($LASTEXITCODE -ne 0) { throw "clean failed." }
    }

    if ($Apk) {
        Write-Step "Building signed APK (:app:assembleRelease)"
        & $Gradlew :app:assembleRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw "assembleRelease failed." }
        $out = Join-Path $RepoRoot 'app\build\outputs\apk\release\app-release.apk'
    } else {
        Write-Step "Building signed App Bundle (:app:bundleRelease)"
        & $Gradlew :app:bundleRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw "bundleRelease failed." }
        $out = Join-Path $RepoRoot 'app\build\outputs\bundle\release\app-release.aab'
    }
}
finally {
    Pop-Location
}

Write-Host ""
if (Test-Path $out) {
    $size = [math]::Round((Get-Item $out).Length / 1MB, 1)
    Write-Ok "Built: $out  ($size MB)"
} else {
    Write-Warn "Build finished but expected output not found at $out. Check the Gradle log above."
}
Write-Warn "Confirm app/build.gradle.kts release API_BASE_URL points at your real backend before publishing."
