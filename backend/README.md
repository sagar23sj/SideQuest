# Action Tracker Backend (Go 1.26)

Standalone Go backend for the Action Tracker App. Clients (Android/Kotlin and,
later, iOS/Swift) talk to it over the REST/JSON contract defined in
[`api/openapi.yaml`](api/openapi.yaml) — the single source of truth from which
both client models and server types are generated.

This module is the **scaffold** delivered by task 22. Feature endpoints arrive
in later tasks: accounts (23), sync (24), games (25), leaderboards (26).

## Layout

```
backend/
  cmd/server/        # main entry point (config load, DB, providers, run)
  internal/
    config/          # explicit env-based configuration (no init, no globals)
    db/              # pgx pool wrapper (functional options, injectable)
    domain/          # pure domain entities mirroring the OpenAPI schema
    provider/        # LLM + Transcription provider interfaces + HTTP adapters
    server/          # net/http ServeMux routing, timeouts, graceful shutdown,
                     #   /healthz, and the LLM/transcription proxy handlers
  api/
    openapi.yaml     # shared OpenAPI 3 contract (source of truth)
    oapi-codegen.yaml# config for generating Go server types from openapi.yaml
  migrations/        # golang-migrate SQL (REVIEW REQUIRED before production)
```

## Configuration

All configuration is read from the environment (no hardcoded secrets). See
[`.env.example`](.env.example). Required: `AT_DATABASE_DSN`,
`AT_JWT_SIGNING_SECRET`. Provider keys (`AT_LLM_PROVIDER_KEY`,
`AT_STT_PROVIDER_KEY`) are held server-side and never returned to clients.

`AT_LISTEN_ADDR` defaults to `127.0.0.1:8080` (binds to a specific interface to
limit attack surface). Set it explicitly for your deployment.

## Build & run (requires a Go 1.26 toolchain)

> The Go toolchain was not available in the environment where this scaffold was
> generated, so the commands below were **not executed here**. Run them locally.

```bash
# Resolve dependencies and produce go.sum:
go mod tidy

go build ./...
go vet ./...
go test ./...

# Run (with env configured):
go run ./cmd/server
```

## Migrations

Applied with [golang-migrate](https://github.com/golang-migrate/migrate):

```bash
migrate -path migrations -database "$AT_DATABASE_DSN" up
```

The initial schema in `migrations/0001_init.*.sql` mirrors the domain entities
but carries a **REVIEW REQUIRED** header — a human DBA must validate
indexes/types/volumes before production use.

## OpenAPI code generation (not run as part of this task)

```bash
# Server types:
go tool oapi-codegen -config api/oapi-codegen.yaml api/openapi.yaml
# Kotlin/Swift client models are generated from the same api/openapi.yaml.
```
