// Package config loads runtime configuration from environment variables.
//
// Configuration is loaded explicitly via Load (no init(), no mutable
// package-level globals) so it is testable and injectable. Secrets are read
// from the environment only and are never hardcoded.
package config

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"time"
)

// Config holds all runtime configuration for the backend service.
//
// Secret fields (DatabaseDSN, JWTSigningSecret, LLMProviderKey,
// STTProviderKey) are sourced exclusively from the environment and must never
// be logged or returned to clients.
type Config struct {
	// ListenAddr is the host:port the HTTP server binds to. Binding to a
	// specific interface (rather than 0.0.0.0) limits the attack surface;
	// callers should set this explicitly in production.
	ListenAddr string

	// DatabaseDSN is the PostgreSQL connection string (secret).
	DatabaseDSN string

	// JWTSigningSecret signs/verifies JWT access tokens (secret).
	JWTSigningSecret string

	// JWTIssuer is the "iss" claim stamped into issued tokens.
	JWTIssuer string

	// AccessTokenTTL is the lifetime of short-lived access tokens.
	AccessTokenTTL time.Duration

	// RefreshTokenTTL is the lifetime of longer-lived refresh tokens that
	// back silent refresh.
	RefreshTokenTTL time.Duration

	// LLMProviderKey authenticates the backend to the LLM provider (secret,
	// server-side only — never returned to clients).
	LLMProviderKey string

	// LLMProviderBaseURL is the base URL of the LLM provider API.
	LLMProviderBaseURL string

	// STTProviderKey authenticates the backend to the speech-to-text
	// provider (secret, server-side only — never returned to clients).
	STTProviderKey string

	// STTProviderBaseURL is the base URL of the transcription provider API.
	STTProviderBaseURL string

	// Server timeouts (see net/http.Server). Defaults are applied in Load
	// when the corresponding env vars are unset.
	ReadHeaderTimeout time.Duration
	ReadTimeout       time.Duration
	WriteTimeout      time.Duration
	IdleTimeout       time.Duration

	// ShutdownTimeout bounds graceful shutdown.
	ShutdownTimeout time.Duration

	// ExternalCallTimeout bounds outbound provider (LLM/STT) calls.
	ExternalCallTimeout time.Duration

	// DBMaxConns caps the pgx connection pool size.
	DBMaxConns int32
}

// Default values applied when an environment variable is unset.
const (
	defaultListenAddr          = "127.0.0.1:8080"
	defaultJWTIssuer           = "action-tracker"
	defaultAccessTokenTTL      = 15 * time.Minute
	defaultRefreshTokenTTL     = 30 * 24 * time.Hour
	defaultReadHeaderTimeout   = 5 * time.Second
	defaultReadTimeout         = 15 * time.Second
	defaultWriteTimeout        = 30 * time.Second
	defaultIdleTimeout         = 120 * time.Second
	defaultShutdownTimeout     = 20 * time.Second
	defaultExternalCallTimeout = 30 * time.Second
	defaultDBMaxConns          = 25
)

// Environment variable names.
const (
	envListenAddr          = "AT_LISTEN_ADDR"
	envDatabaseDSN         = "AT_DATABASE_DSN"
	envJWTSigningSecret    = "AT_JWT_SIGNING_SECRET"
	envJWTIssuer           = "AT_JWT_ISSUER"
	envAccessTokenTTL      = "AT_ACCESS_TOKEN_TTL"
	envRefreshTokenTTL     = "AT_REFRESH_TOKEN_TTL"
	envLLMProviderKey      = "AT_LLM_PROVIDER_KEY"
	envLLMProviderBaseURL  = "AT_LLM_PROVIDER_BASE_URL"
	envSTTProviderKey      = "AT_STT_PROVIDER_KEY"
	envSTTProviderBaseURL  = "AT_STT_PROVIDER_BASE_URL"
	envReadHeaderTimeout   = "AT_READ_HEADER_TIMEOUT"
	envReadTimeout         = "AT_READ_TIMEOUT"
	envWriteTimeout        = "AT_WRITE_TIMEOUT"
	envIdleTimeout         = "AT_IDLE_TIMEOUT"
	envShutdownTimeout     = "AT_SHUTDOWN_TIMEOUT"
	envExternalCallTimeout = "AT_EXTERNAL_CALL_TIMEOUT"
	envDBMaxConns          = "AT_DB_MAX_CONNS"
)

// ErrMissingRequired is returned when a required configuration value is unset.
var ErrMissingRequired = errors.New("config: missing required value")

// Load reads configuration from the process environment, applies defaults for
// optional values, and validates that required secrets are present.
//
// Load takes no implicit action (no DB connection, no logging side effects);
// it returns a fully-formed Config or an error.
func Load() (*Config, error) {
	cfg := &Config{
		ListenAddr:          getenvDefault(envListenAddr, defaultListenAddr),
		DatabaseDSN:         os.Getenv(envDatabaseDSN),
		JWTSigningSecret:    os.Getenv(envJWTSigningSecret),
		JWTIssuer:           getenvDefault(envJWTIssuer, defaultJWTIssuer),
		AccessTokenTTL:      defaultAccessTokenTTL,
		RefreshTokenTTL:     defaultRefreshTokenTTL,
		LLMProviderKey:      os.Getenv(envLLMProviderKey),
		LLMProviderBaseURL:  os.Getenv(envLLMProviderBaseURL),
		STTProviderKey:      os.Getenv(envSTTProviderKey),
		STTProviderBaseURL:  os.Getenv(envSTTProviderBaseURL),
		ReadHeaderTimeout:   defaultReadHeaderTimeout,
		ReadTimeout:         defaultReadTimeout,
		WriteTimeout:        defaultWriteTimeout,
		IdleTimeout:         defaultIdleTimeout,
		ShutdownTimeout:     defaultShutdownTimeout,
		ExternalCallTimeout: defaultExternalCallTimeout,
		DBMaxConns:          defaultDBMaxConns,
	}

	var err error
	if cfg.AccessTokenTTL, err = durationEnv(envAccessTokenTTL, defaultAccessTokenTTL); err != nil {
		return nil, err
	}
	if cfg.RefreshTokenTTL, err = durationEnv(envRefreshTokenTTL, defaultRefreshTokenTTL); err != nil {
		return nil, err
	}
	if cfg.ReadHeaderTimeout, err = durationEnv(envReadHeaderTimeout, defaultReadHeaderTimeout); err != nil {
		return nil, err
	}
	if cfg.ReadTimeout, err = durationEnv(envReadTimeout, defaultReadTimeout); err != nil {
		return nil, err
	}
	if cfg.WriteTimeout, err = durationEnv(envWriteTimeout, defaultWriteTimeout); err != nil {
		return nil, err
	}
	if cfg.IdleTimeout, err = durationEnv(envIdleTimeout, defaultIdleTimeout); err != nil {
		return nil, err
	}
	if cfg.ShutdownTimeout, err = durationEnv(envShutdownTimeout, defaultShutdownTimeout); err != nil {
		return nil, err
	}
	if cfg.ExternalCallTimeout, err = durationEnv(envExternalCallTimeout, defaultExternalCallTimeout); err != nil {
		return nil, err
	}
	if cfg.DBMaxConns, err = int32Env(envDBMaxConns, defaultDBMaxConns); err != nil {
		return nil, err
	}

	if err := cfg.validate(); err != nil {
		return nil, err
	}
	return cfg, nil
}

// validate ensures required secrets are present. It returns errors that
// reference the missing key name only — never the (absent) value.
func (c *Config) validate() error {
	missing := make([]string, 0, 2)
	if c.DatabaseDSN == "" {
		missing = append(missing, envDatabaseDSN)
	}
	if c.JWTSigningSecret == "" {
		missing = append(missing, envJWTSigningSecret)
	}
	if len(missing) > 0 {
		return fmt.Errorf("%w: %v", ErrMissingRequired, missing)
	}
	return nil
}

func getenvDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func durationEnv(key string, fallback time.Duration) (time.Duration, error) {
	raw := os.Getenv(key)
	if raw == "" {
		return fallback, nil
	}
	d, err := time.ParseDuration(raw)
	if err != nil {
		return 0, fmt.Errorf("config: parsing %s: %w", key, err)
	}
	return d, nil
}

func int32Env(key string, fallback int32) (int32, error) {
	raw := os.Getenv(key)
	if raw == "" {
		return fallback, nil
	}
	n, err := strconv.ParseInt(raw, 10, 32)
	if err != nil {
		return 0, fmt.Errorf("config: parsing %s: %w", key, err)
	}
	return int32(n), nil
}
