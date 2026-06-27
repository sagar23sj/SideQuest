# Dev scripts

Local orchestration for the SideQuest / Action Tracker stack on Windows
(PowerShell).

## What the app needs to run

| Component | What it is | Provided by |
| --- | --- | --- |
| **Postgres** | Backend database (matches `backend/.env` DSN) | Podman/Docker container `sidequest-postgres` |
| **migrate** | Schema migration CLI ([golang-migrate]) | `go install` (auto, if missing) |
| **Migrations** | `backend/migrations/*.sql` applied to the DB | `migrate ... up` |
| **Backend** | Go 1.26 HTTP service | `go run ./cmd/server` |
| **Android app** | The Compose client | `gradlew :app:installDebug` (needs emulator/device) |

## Prerequisites

- **Docker Desktop or Podman** — for Postgres (the script auto-detects either, preferring Podman; or run your own Postgres matching the DSN)
- **Go 1.26+** — for the backend and the migrate CLI
- **Android SDK** — for building/installing the app (already wired via `local.properties`)
- **backend/.env** — copy from `backend/.env.example` if missing

## Usage

```powershell
# Full backend stack: Postgres -> migrations -> backend server (foreground)
./scripts/dev-up.ps1

# Everything including the Android app
./scripts/dev-up.ps1 -All

# Individual pieces
./scripts/dev-up.ps1 -Database           # just start Postgres
./scripts/dev-up.ps1 -Database -Migrate  # Postgres + apply schema, then exit
./scripts/dev-up.ps1 -Backend            # just the Go server (DB must be up)
./scripts/dev-up.ps1 -Android            # build + install the app

# Skip migrations on the default flow
./scripts/dev-up.ps1 -SkipMigrate
```

Stop the background database when done:

```powershell
./scripts/dev-down.ps1            # stop (keeps data)
./scripts/dev-down.ps1 -Purge     # stop + delete (fresh DB next time)
```

## Notes

- Uses **Docker** by default. The script never touches Podman unless you pass
  `-Engine podman` (Podman's Hyper-V backend on Windows needs an elevated shell;
  run the script as Administrator in that case).
- The backend runs in the **foreground**; stop it with `Ctrl+C`. The script
  starts it last so shutdown is clean.
- The Postgres container's user/password/db/port are derived from
  `AT_DATABASE_DSN` in `backend/.env`, so the two never drift.
- Provider keys (`AT_LLM_PROVIDER_KEY`, `AT_STT_PROVIDER_KEY`) are optional for
  local dev — the proxy endpoints fail soft (503) when they're blank.
- If no emulator/device is connected, `-Android` builds the APK
  (`app/build/outputs/apk/debug/app-debug.apk`) instead of installing.

## Running the Android app on an emulator

1. Open Android Studio → **Device Manager** → start (or create) a virtual device.
2. `./scripts/dev-up.ps1 -Android` to build and install.
3. The backend on `127.0.0.1:8080` is reachable from the emulator at
   `http://10.0.2.2:8080` (the emulator's alias for the host loopback).

[golang-migrate]: https://github.com/golang-migrate/migrate
