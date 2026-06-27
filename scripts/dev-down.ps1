<#
.SYNOPSIS
    Tears down the local SideQuest / Action Tracker development stack.

.DESCRIPTION
    Stops the Postgres container started by dev-up.ps1. The Go backend runs in
    the foreground, so it is stopped with Ctrl+C in its own terminal — this
    script only handles the background container.

.PARAMETER Purge
    Also remove the container (and its data volume) instead of just stopping it,
    giving a clean database on the next dev-up.

.EXAMPLE
    ./scripts/dev-down.ps1
    # Stop Postgres (data preserved for next run).

.EXAMPLE
    ./scripts/dev-down.ps1 -Purge
    # Stop and delete the Postgres container (fresh DB next time).
#>
[CmdletBinding()]
param(
    [switch]$Purge
)

$ErrorActionPreference = 'Stop'
$PgContainer = 'sidequest-postgres'

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "  [ok] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "  [warn] $msg" -ForegroundColor Yellow }

# Resolves a container engine, defaulting to Docker (Podman's Hyper-V backend on
# Windows requires elevation). Falls back to Podman only if Docker is absent.
function Resolve-ContainerEngine {
    if (Get-Command 'docker' -ErrorAction SilentlyContinue) { return 'docker' }
    if (Get-Command 'podman' -ErrorAction SilentlyContinue) { return 'podman' }
    foreach ($p in @(
        "$env:ProgramFiles\RedHat\Podman\podman.exe",
        "$env:LOCALAPPDATA\Programs\RedHat\Podman\podman.exe",
        "$env:ProgramFiles\Podman\podman.exe"
    )) {
        if ($p -and (Test-Path $p)) { return $p }
    }
    return $null
}

$engine = Resolve-ContainerEngine
if (-not $engine) {
    Write-Warn "No container engine (Podman/Docker) found; nothing to stop."
    return
}

$exists = (& $engine ps -a --filter "name=^/$PgContainer$" --format '{{.Names}}') 2>$null
if ($exists -ne $PgContainer) {
    Write-Warn "No '$PgContainer' container found; nothing to do."
    return
}

if ($Purge) {
    Write-Step "Removing Postgres container '$PgContainer' (and its data)"
    & $engine rm -f $PgContainer | Out-Null
    Write-Ok "Container removed. Next dev-up starts from a clean database."
} else {
    Write-Step "Stopping Postgres container '$PgContainer'"
    & $engine stop $PgContainer | Out-Null
    Write-Ok "Stopped. Data preserved; restart with scripts/dev-up.ps1."
}
