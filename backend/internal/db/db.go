// Package db provides a thin, injectable wrapper around a pgx connection pool.
//
// The pool is created explicitly via New (no init(), no package-level globals)
// and is injected into the layers that need it. Connecting is an explicit
// action performed by the caller during startup — never at import time.
package db

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

// DB wraps a configured pgx pool.
type DB struct {
	pool *pgxpool.Pool
}

// config holds tunables applied by functional options.
type config struct {
	maxConns          int32
	minConns          int32
	maxConnLifetime   time.Duration
	maxConnIdleTime   time.Duration
	healthCheckPeriod time.Duration
	connectTimeout    time.Duration
}

// Option configures the pool. Options are applied in order over sane defaults.
type Option func(*config)

// WithMaxConns caps the total number of connections in the pool.
func WithMaxConns(n int32) Option {
	return func(c *config) { c.maxConns = n }
}

// WithMinConns sets the number of warm connections kept ready.
func WithMinConns(n int32) Option {
	return func(c *config) { c.minConns = n }
}

// WithMaxConnLifetime recycles connections older than d.
func WithMaxConnLifetime(d time.Duration) Option {
	return func(c *config) { c.maxConnLifetime = d }
}

// WithMaxConnIdleTime closes connections idle longer than d.
func WithMaxConnIdleTime(d time.Duration) Option {
	return func(c *config) { c.maxConnIdleTime = d }
}

// WithConnectTimeout bounds the initial connect-and-ping during New.
func WithConnectTimeout(d time.Duration) Option {
	return func(c *config) { c.connectTimeout = d }
}

const (
	defaultMaxConns          = 25
	defaultMinConns          = 2
	defaultMaxConnLifetime   = 5 * time.Minute
	defaultMaxConnIdleTime   = 1 * time.Minute
	defaultHealthCheckPeriod = 1 * time.Minute
	defaultConnectTimeout    = 10 * time.Second
)

// New parses the DSN, builds a configured pgx pool, and verifies connectivity
// with a bounded Ping. The caller owns the returned *DB and MUST Close it.
func New(ctx context.Context, dsn string, opts ...Option) (*DB, error) {
	if dsn == "" {
		return nil, fmt.Errorf("db: empty DSN")
	}

	cfg := &config{
		maxConns:          defaultMaxConns,
		minConns:          defaultMinConns,
		maxConnLifetime:   defaultMaxConnLifetime,
		maxConnIdleTime:   defaultMaxConnIdleTime,
		healthCheckPeriod: defaultHealthCheckPeriod,
		connectTimeout:    defaultConnectTimeout,
	}
	for _, opt := range opts {
		opt(cfg)
	}

	poolCfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		// Avoid echoing the DSN (it may contain credentials).
		return nil, fmt.Errorf("db: parsing pool config: %w", err)
	}
	poolCfg.MaxConns = cfg.maxConns
	poolCfg.MinConns = cfg.minConns
	poolCfg.MaxConnLifetime = cfg.maxConnLifetime
	poolCfg.MaxConnIdleTime = cfg.maxConnIdleTime
	poolCfg.HealthCheckPeriod = cfg.healthCheckPeriod

	pool, err := pgxpool.NewWithConfig(ctx, poolCfg)
	if err != nil {
		return nil, fmt.Errorf("db: creating pool: %w", err)
	}

	pingCtx, cancel := context.WithTimeout(ctx, cfg.connectTimeout)
	defer cancel()
	if err := pool.Ping(pingCtx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("db: pinging database: %w", err)
	}

	return &DB{pool: pool}, nil
}

// Pool returns the underlying pgx pool for query execution by repositories.
func (d *DB) Pool() *pgxpool.Pool { return d.pool }

// Ping verifies connectivity within the provided context's deadline.
func (d *DB) Ping(ctx context.Context) error {
	if err := d.pool.Ping(ctx); err != nil {
		return fmt.Errorf("db: ping: %w", err)
	}
	return nil
}

// Close releases all pooled connections. Safe to call once during shutdown.
func (d *DB) Close() {
	if d.pool != nil {
		d.pool.Close()
	}
}
