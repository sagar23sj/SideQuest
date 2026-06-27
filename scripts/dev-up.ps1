<#
.SYNOPSIS
    Brings up the local SideQuest / Action Tracker development stack.

.DESCRIPTION
    Orchestrates every component the app needs to run locally, in order:

      1. Postgres        - a Docker container matching the backend's .env DSN
      2. migrate CLI     - installed via `go install` if missing
      3. DB migrations   - applied with golang-migrate (`up`)
      4. Backend server  - the Go service (`go run ./cmd/server`)
      5. Android app     - optional: assemble the debug APK and install it on a
                           running emulator / connected device

    With no switches it runs the full backend stack (Postgres -> migrate ->
    migrations -> backend server). Use the switches below to run only parts.

.PARAMETER Database
    Start (or reuse) the Postgres container only.

.PARAMETER Migrate
    Apply database migrations only (assumes Postgres is up).

.PARAMETER Backend
    Run the Go backend server only (assumes Postgres + migrations are ready).

.PARAMETER Android
    Build the debug APK and install it on a running emulator / device.

.PARAMETER All
    Backend stack + Android (everything).

.PARAMETER SkipMigrate
    When running the default/backend flow, do not apply migrations.

.PARAMETER Engine
    Container engine to use: 'docker' or 'podman'. Defaults to 'docker'
    (Podman's Hyper-V backend on Windows requires an elevated shell). Pass
    '-Engine podman' only if you run this script as Administrator.

.EXAMPLE
    ./scripts/dev-up.ps1
    # Postgres + migrations + backend server (foreground; Ctrl+C to stop).

.EXAMPLE
    ./scripts/dev-up.ps1 -Database -Migrate
    # Just stand up the database and apply the schema, then exit.

.EXAMPLE
    ./scripts/dev-up.ps1 -Android
    # Build + install the Android app onto an emulator/device.

.NOTES
    Windows / PowerShell. Stop the database afterwards with scripts/dev-down.ps1.
#>
[CmdletBinding()]
param(
    [switch]$Database,
    [switch]$Migrate,
    [switch]$Backend,
    [switch]$Android,
    [switch]$All,
    [switch]$SkipMigrate,
    [ValidateSet('docker', 'podman')]
    [string]$Engine = 'docker'
)

$ErrorActionPreference = 'Stop'

# --- Paths -----------------------------------------------------------------
$RepoRoot   = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $RepoRoot 'backend'
$EnvFile    = Join-Path $BackendDir '.env'

# --- Postgres container settings (kept in sync with backend/.env DSN) ------
$PgContainer = 'sidequest-postgres'
$PgImage     = 'postgres:16-alpine'

# --- Pretty output ---------------------------------------------------------
function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "  [ok] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "  [warn] $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "  [error] $msg" -ForegroundColor Red }

function Test-Command($name) {
    return [bool](Get-Command $name -ErrorAction SilentlyContinue)
}

# Resolves a container engine. Honors an explicit choice ($Engine param); else
# prefers Docker (confirmed working without elevation on Windows), then Podman.
# Returns the executable to invoke (name if on PATH, otherwise a full path), or
# $null when the requested/any engine is unavailable. Podman is Docker-CLI
# compatible, so the same subcommands work against whichever is returned.
function Resolve-ContainerEngine($preferred) {
    $docker = if (Test-Command 'docker') { 'docker' } else { $null }

    $podman = $null
    if (Test-Command 'podman') {
        $podman = 'podman'
    } else {
        foreach ($p in @(
            "$env:ProgramFiles\RedHat\Podman\podman.exe",
            "$env:LOCALAPPDATA\Programs\RedHat\Podman\podman.exe",
            "$env:ProgramFiles\Podman\podman.exe"
        )) {
            if ($p -and (Test-Path $p)) { $podman = $p; break }
        }
    }

    switch ($preferred) {
        'docker' {
            if (-not $docker) { throw "Requested -Engine docker but Docker was not found on PATH." }
            return $docker
        }
        'podman' {
            if (-not $podman) { throw "Requested -Engine podman but Podman was not found." }
            return $podman
        }
        default {
            # Auto: prefer Docker (no elevation needed), fall back to Podman.
            if ($docker) { return $docker }
            if ($podman) { return $podman }
            return $null
        }
    }
}

# The resolved engine executable, set once during orchestration.
$script:ContainerEngine = $null

# Loads backend/.env into the current process environment and returns a
# hashtable of the parsed values. Lines that are blank or comments are skipped.
function Import-DotEnv($path) {
    if (-not (Test-Path $path)) {
        throw "Env file not found: $path. Copy backend/.env.example to backend/.env first."
    }
    $values = @{}
    foreach ($line in Get-Content $path) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        $idx = $trimmed.IndexOf('=')
        if ($idx -lt 1) { continue }
        $key = $trimmed.Substring(0, $idx).Trim()
        $val = $trimmed.Substring($idx + 1).Trim()
        $values[$key] = $val
        Set-Item -Path "Env:$key" -Value $val
    }
    return $values
}

# Parses a postgres:// DSN into its parts so the Docker container can be
# created with matching credentials/db/port.
function ConvertFrom-PostgresDsn($dsn) {
    # postgres://user:password@host:port/dbname?params
    $m = [regex]::Match($dsn, '^postgres(?:ql)?://([^:]+):([^@]+)@([^:/]+):(\d+)/([^?]+)')
    if (-not $m.Success) {
        throw "Could not parse AT_DATABASE_DSN. Expected postgres://user:pass@host:port/db. Got: $dsn"
    }
    return [pscustomobject]@{
        User     = $m.Groups[1].Value
        Password = $m.Groups[2].Value
        Host     = $m.Groups[3].Value
        Port     = [int]$m.Groups[4].Value
        Database = $m.Groups[5].Value
    }
}

# --- Component: Postgres ---------------------------------------------------
function Start-Postgres($pg) {
    Write-Step "Starting Postgres ($PgImage) as container '$PgContainer'"

    $engine = $script:ContainerEngine
    if (-not $engine) {
        throw "No container engine found. Install Podman or Docker, or run your own Postgres matching backend/.env."
    }
    $engineName = Split-Path -Leaf $engine
    Write-Ok "Using container engine: $engineName"

    # Podman on Windows needs a running machine (the Linux VM that hosts
    # containers). Start it on demand if it isn't already up.
    if ($engineName -like 'podman*') {
        $machineState = (& $engine machine list --format '{{.Running}}' 2>$null) | Select-Object -First 1
        if ($machineState -and $machineState -notmatch 'true|Currently running') {
            Write-Host "  Starting Podman machine..." -NoNewline
            & $engine machine start 2>$null | Out-Null
            Write-Host ""
        }
    }

    $existing = (& $engine ps -a --filter "name=^/$PgContainer$" --format '{{.Names}}') 2>$null
    if ($existing -eq $PgContainer) {
        $running = (& $engine ps --filter "name=^/$PgContainer$" --format '{{.Names}}') 2>$null
        if ($running -eq $PgContainer) {
            Write-Ok "Container already running."
        } else {
            & $engine start $PgContainer | Out-Null
            Write-Ok "Started existing container."
        }
    } else {
        & $engine run -d `
            --name $PgContainer `
            -e "POSTGRES_USER=$($pg.User)" `
            -e "POSTGRES_PASSWORD=$($pg.Password)" `
            -e "POSTGRES_DB=$($pg.Database)" `
            -p "$($pg.Port):5432" `
            $PgImage | Out-Null
        Write-Ok "Created and started new container."
    }

    Write-Host "  Waiting for Postgres to accept connections..." -NoNewline
    for ($i = 0; $i -lt 30; $i++) {
        $ready = (& $engine exec $PgContainer pg_isready -U $pg.User -d $pg.Database) 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host ""
            Write-Ok "Postgres is ready on port $($pg.Port)."
            return
        }
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 1
    }
    Write-Host ""
    throw "Postgres did not become ready in time. Check '$engineName logs $PgContainer'."
}

# --- Component: migrate CLI + migrations -----------------------------------
function Resolve-MigrateExe {
    if (Test-Command 'migrate') { return 'migrate' }
    $goBin = Join-Path (& go env GOPATH) 'bin'
    $candidate = Join-Path $goBin 'migrate.exe'
    if (Test-Path $candidate) { return $candidate }
    return $null
}

function Install-Migrate {
    Write-Step "Installing golang-migrate CLI (postgres build tag)"
    if (-not (Test-Command 'go')) {
        throw "Go toolchain not found. Install Go 1.26+, or install golang-migrate manually."
    }
    & go install -tags 'postgres' github.com/golang-migrate/migrate/v4/cmd/migrate@latest
    if ($LASTEXITCODE -ne 0) { throw "go install of migrate failed." }
    $exe = Resolve-MigrateExe
    if (-not $exe) { throw "migrate installed but not found on PATH or in GOPATH/bin." }
    Write-Ok "Installed: $exe"
    return $exe
}

function Invoke-Migrations($dsn) {
    Write-Step "Applying database migrations"
    $exe = Resolve-MigrateExe
    if (-not $exe) { $exe = Install-Migrate }

    $migrationsDir = Join-Path $BackendDir 'migrations'
    Push-Location $BackendDir
    try {
        & $exe -path 'migrations' -database $dsn up
        if ($LASTEXITCODE -ne 0) { throw "migrate up failed (exit $LASTEXITCODE)." }
    } finally {
        Pop-Location
    }
    Write-Ok "Migrations applied."
}

# --- Component: Backend server ---------------------------------------------
function Start-Backend {
    Write-Step "Starting backend server (go run ./cmd/server)"
    if (-not (Test-Command 'go')) {
        throw "Go toolchain not found. Install Go 1.26+."
    }
    Write-Ok "Listening on $($env:AT_LISTEN_ADDR). Press Ctrl+C to stop."
    Write-Host ""
    Push-Location $BackendDir
    try {
        & go run ./cmd/server
    } finally {
        Pop-Location
    }
}

# --- Component: Android app ------------------------------------------------
function Resolve-Adb {
    if (Test-Command 'adb') { return 'adb' }
    foreach ($base in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")) {
        if ($base) {
            $candidate = Join-Path $base 'platform-tools\adb.exe'
            if (Test-Path $candidate) { return $candidate }
        }
    }
    return $null
}

function Invoke-AndroidInstall {
    Write-Step "Building and installing the Android app (debug)"
    $gradlew = Join-Path $RepoRoot 'gradlew.bat'
    if (-not (Test-Path $gradlew)) { throw "gradlew.bat not found at repo root." }

    $adb = Resolve-Adb
    $deviceConnected = $false
    if ($adb) {
        $devices = (& $adb devices) 2>$null | Select-String -Pattern '\tdevice$'
        $deviceConnected = [bool]$devices
    }

    Push-Location $RepoRoot
    try {
        if ($deviceConnected) {
            Write-Ok "Device/emulator detected; running installDebug."
            & $gradlew :app:installDebug --console=plain
            if ($LASTEXITCODE -ne 0) { throw "installDebug failed." }
            Write-Ok "Installed. Launch 'Action Tracker' from the launcher."
        } else {
            Write-Warn "No emulator/device detected; building the APK only (assembleDebug)."
            & $gradlew :app:assembleDebug --console=plain
            if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed." }
            $apk = Join-Path $RepoRoot 'app\build\outputs\apk\debug\app-debug.apk'
            Write-Ok "APK built: $apk"
            Write-Warn "Start an emulator (Android Studio > Device Manager) or plug in a device, then re-run with -Android to install."
        }
    } finally {
        Pop-Location
    }
}

# ===========================================================================
# Orchestration
# ===========================================================================
Write-Host "SideQuest / Action Tracker - dev stack" -ForegroundColor Magenta

# Decide which components to run. With no switches, run the backend stack.
$runDb       = $Database -or $All
$runMigrate  = $Migrate -or $All
$runBackend  = $Backend -or $All
$runAndroid  = $Android -or $All

$noSwitches = -not ($Database -or $Migrate -or $Backend -or $Android -or $All)
if ($noSwitches) {
    $runDb = $true
    $runMigrate = $true
    $runBackend = $true
}
if ($SkipMigrate) { $runMigrate = $false }

# .env + DSN are needed by every backend-side component.
$pg = $null
$dsn = $null
if ($runDb -or $runMigrate -or $runBackend) {
    Write-Step "Loading backend environment from $EnvFile"
    $envValues = Import-DotEnv $EnvFile
    $dsn = $envValues['AT_DATABASE_DSN']
    if (-not $dsn) { throw "AT_DATABASE_DSN missing from backend/.env." }
    $pg = ConvertFrom-PostgresDsn $dsn
    Write-Ok "Target DB: $($pg.Database) @ $($pg.Host):$($pg.Port) (user '$($pg.User)')"

    if ($envValues['AT_JWT_SIGNING_SECRET'] -eq 'change-me-use-a-long-random-value') {
        Write-Warn "AT_JWT_SIGNING_SECRET is still the example value. Fine for local dev; change it before any real deployment."
    }
}

if ($runDb)      { $script:ContainerEngine = Resolve-ContainerEngine $Engine; Start-Postgres $pg }
if ($runMigrate) { Invoke-Migrations $dsn }
if ($runAndroid) { Invoke-AndroidInstall }
if ($runBackend) { Start-Backend }   # foreground; keep last so Ctrl+C is clean.

if (-not $runBackend) {
    Write-Host ""
    Write-Ok "Done."
}
