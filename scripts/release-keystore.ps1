<#
.SYNOPSIS
    Creates the release signing keystore for SideQuest and writes a gitignored
    keystore.properties that the Gradle build reads to sign release artifacts.

.DESCRIPTION
    Play Store apps must be signed with a private upload key that you reuse for
    every future update — losing it means you can no longer update the app under
    the same listing. This script:

      1. Generates an RSA-2048 keystore (`sidequest-release.jks`) via `keytool`,
         valid ~27 years (10000 days), unless one already exists.
      2. Writes `keystore.properties` at the repo root with the store path,
         passwords, and alias. Both files are gitignored.

    Run this ONCE. Back up the .jks file and the passwords somewhere safe
    (a password manager); they are not recoverable.

.PARAMETER Alias
    Key alias inside the keystore. Default: 'sidequest'.

.PARAMETER KeystoreName
    Keystore file name (created at the repo root). Default: 'sidequest-release.jks'.

.PARAMETER Force
    Overwrite an existing keystore.properties (does NOT delete an existing .jks).

.EXAMPLE
    ./scripts/release-keystore.ps1
    # Prompts for passwords + certificate details, then creates the keystore
    # and keystore.properties.

.NOTES
    Windows / PowerShell. Requires `keytool` (ships with the JDK; if it's not on
    PATH, it lives under your Android Studio JBR: ...\jbr\bin\keytool.exe).
    After running this, build with scripts/release-build.ps1.
#>
[CmdletBinding()]
param(
    [string]$Alias = 'sidequest',
    [string]$KeystoreName = 'sidequest-release.jks',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "  [ok] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "  [warn] $msg" -ForegroundColor Yellow }

function Test-Command($name) {
    return [bool](Get-Command $name -ErrorAction SilentlyContinue)
}

# Resolve keytool: PATH first, then common Android Studio JBR locations.
function Resolve-Keytool {
    if (Test-Command 'keytool') { return 'keytool' }
    $candidates = @(
        "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe",
        "$env:JAVA_HOME\bin\keytool.exe"
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return $c }
    }
    return $null
}

$RepoRoot   = Split-Path -Parent $PSScriptRoot
$KeystorePath = Join-Path $RepoRoot $KeystoreName
$PropsPath    = Join-Path $RepoRoot 'keystore.properties'

Write-Host "SideQuest - release keystore setup" -ForegroundColor Magenta

$keytool = Resolve-Keytool
if (-not $keytool) {
    throw "keytool not found. Install a JDK or Android Studio, or add keytool to PATH."
}
Write-Ok "Using keytool: $keytool"

if ((Test-Path $PropsPath) -and -not $Force) {
    throw "keystore.properties already exists at $PropsPath. Re-run with -Force to overwrite it (the .jks is left untouched)."
}

# --- Collect passwords + certificate identity -----------------------------
Write-Step "Enter signing credentials (kept locally; keystore.properties is gitignored)"

$storePwdSecure = Read-Host "Keystore password (min 6 chars)" -AsSecureString
$keyPwdSecure   = Read-Host "Key password (press Enter to reuse the keystore password)" -AsSecureString

function ConvertFrom-Secure($secure) {
    if (-not $secure -or $secure.Length -eq 0) { return '' }
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

$storePwd = ConvertFrom-Secure $storePwdSecure
$keyPwd   = ConvertFrom-Secure $keyPwdSecure
if ([string]::IsNullOrEmpty($keyPwd)) { $keyPwd = $storePwd }

if ($storePwd.Length -lt 6) { throw "Keystore password must be at least 6 characters." }

$cn   = Read-Host "Your name or organization (certificate CN)"
if ([string]::IsNullOrWhiteSpace($cn)) { $cn = 'SideQuest' }
$org  = Read-Host "Organization (O) [optional]"
$city = Read-Host "City (L) [optional]"
$st   = Read-Host "State/Province (ST) [optional]"
$ccode= Read-Host "Two-letter country code (C) [optional, e.g. US]"

$dnameParts = @("CN=$cn")
if ($org)   { $dnameParts += "O=$org" }
if ($city)  { $dnameParts += "L=$city" }
if ($st)    { $dnameParts += "ST=$st" }
if ($ccode) { $dnameParts += "C=$ccode" }
$dname = $dnameParts -join ', '

# --- Generate keystore (skip if it already exists) -------------------------
if (Test-Path $KeystorePath) {
    Write-Warn "Keystore already exists at $KeystorePath; reusing it (not regenerating)."
} else {
    Write-Step "Generating keystore: $KeystorePath (alias '$Alias')"
    & $keytool -genkeypair -v `
        -keystore $KeystorePath `
        -storepass $storePwd `
        -keypass $keyPwd `
        -alias $Alias `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname $dname
    if ($LASTEXITCODE -ne 0) { throw "keytool failed (exit $LASTEXITCODE)." }
    Write-Ok "Keystore created."
}

# --- Write keystore.properties --------------------------------------------
Write-Step "Writing keystore.properties (gitignored)"

# Gradle reads storeFile relative to the app module, so use an absolute path to
# avoid ambiguity. Backslashes are escaped for the .properties format.
$escapedPath = $KeystorePath -replace '\\', '\\'
$content = @"
# SideQuest release signing — generated by scripts/release-keystore.ps1.
# NEVER commit this file (it is gitignored). Back up these values securely.
storeFile=$escapedPath
storePassword=$storePwd
keyAlias=$Alias
keyPassword=$keyPwd
"@
Set-Content -Path $PropsPath -Value $content -Encoding UTF8
Write-Ok "Wrote $PropsPath"

Write-Host ""
Write-Ok "Done. Build a signed release with: ./scripts/release-build.ps1"
Write-Warn "Back up '$KeystoreName' and these passwords — they cannot be recovered, and you need them for every future update."
