// Command server is the Action Tracker backend HTTP service entry point.
//
// It loads configuration explicitly from the environment, opens a pgx pool,
// wires the LLM/transcription provider adapters (keys held server-side), and
// runs the HTTP server until SIGINT/SIGTERM triggers a graceful shutdown.
package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/actiontracker/backend/internal/accounts"
	"github.com/actiontracker/backend/internal/auth"
	"github.com/actiontracker/backend/internal/config"
	"github.com/actiontracker/backend/internal/db"
	"github.com/actiontracker/backend/internal/provider"
	"github.com/actiontracker/backend/internal/server"
	syncrepo "github.com/actiontracker/backend/internal/sync"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))

	if err := run(logger); err != nil {
		logger.Error("server exited with error", slog.Any("error", err))
		os.Exit(1)
	}
}

func run(logger *slog.Logger) error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	// Cancel the root context on SIGINT/SIGTERM for graceful shutdown.
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	database, err := db.New(ctx, cfg.DatabaseDSN,
		db.WithMaxConns(cfg.DBMaxConns),
	)
	if err != nil {
		return err
	}
	defer database.Close()

	// Provider adapters hold the API keys server-side; they are never
	// exposed to clients.
	llm := provider.NewHTTPLLMProvider(cfg.LLMProviderKey, cfg.LLMProviderBaseURL)
	stt := provider.NewHTTPTranscriptionProvider(cfg.STTProviderKey, cfg.STTProviderBaseURL)

	// Accounts + auth (Req 13.1–13.3). The repository runs parameterized
	// queries over the pgx pool; the token service signs short-lived access
	// tokens + longer-lived refresh tokens with the server-side JWT secret.
	accountRepo := accounts.NewRepository(database.Pool())
	accountSvc := accounts.NewService(accountRepo)
	tokenSvc, err := auth.NewTokenService(cfg.JWTSigningSecret,
		auth.WithIssuer(cfg.JWTIssuer),
		auth.WithAccessTTL(cfg.AccessTokenTTL),
		auth.WithRefreshTTL(cfg.RefreshTokenTTL),
	)
	if err != nil {
		return err
	}

	// Offline-first sync (Req 13.4, 14.4): parameterized push/pull over the
	// pgx pool, with deterministic last-writer-wins from the domain package.
	syncStore := syncrepo.NewRepository(database.Pool())

	srv := server.New(cfg.ListenAddr,
		server.WithLogger(logger),
		server.WithDB(database),
		server.WithLLMProvider(llm),
		server.WithTranscriptionProvider(stt),
		server.WithAccountService(accountSvc),
		server.WithTokenIssuer(tokenSvc),
		server.WithSyncStore(syncStore),
		server.WithReadHeaderTimeout(cfg.ReadHeaderTimeout),
		server.WithReadTimeout(cfg.ReadTimeout),
		server.WithWriteTimeout(cfg.WriteTimeout),
		server.WithIdleTimeout(cfg.IdleTimeout),
		server.WithExternalCallTimeout(cfg.ExternalCallTimeout),
	)

	return srv.Run(ctx, cfg.ShutdownTimeout)
}
