package config_test

import (
	"errors"
	"testing"
	"time"

	"github.com/actiontracker/backend/internal/config"
)

func TestLoad(t *testing.T) {
	tests := []struct {
		name    string
		env     map[string]string
		wantErr error
		check   func(t *testing.T, c *config.Config)
	}{
		{
			name: "minimal required values populate defaults",
			env: map[string]string{
				"AT_DATABASE_DSN":       "postgres://localhost/at",
				"AT_JWT_SIGNING_SECRET": "s3cret",
			},
			check: func(t *testing.T, c *config.Config) {
				if c.ListenAddr != "127.0.0.1:8080" {
					t.Errorf("ListenAddr = %q, want default", c.ListenAddr)
				}
				if c.ReadHeaderTimeout != 5*time.Second {
					t.Errorf("ReadHeaderTimeout = %v, want 5s", c.ReadHeaderTimeout)
				}
				if c.DBMaxConns != 25 {
					t.Errorf("DBMaxConns = %d, want 25", c.DBMaxConns)
				}
			},
		},
		{
			name: "overrides are applied",
			env: map[string]string{
				"AT_DATABASE_DSN":       "postgres://localhost/at",
				"AT_JWT_SIGNING_SECRET": "s3cret",
				"AT_LISTEN_ADDR":        "0.0.0.0:9090",
				"AT_READ_TIMEOUT":       "42s",
				"AT_DB_MAX_CONNS":       "7",
			},
			check: func(t *testing.T, c *config.Config) {
				if c.ListenAddr != "0.0.0.0:9090" {
					t.Errorf("ListenAddr = %q", c.ListenAddr)
				}
				if c.ReadTimeout != 42*time.Second {
					t.Errorf("ReadTimeout = %v", c.ReadTimeout)
				}
				if c.DBMaxConns != 7 {
					t.Errorf("DBMaxConns = %d", c.DBMaxConns)
				}
			},
		},
		{
			name:    "missing required DSN is rejected",
			env:     map[string]string{"AT_JWT_SIGNING_SECRET": "s3cret"},
			wantErr: config.ErrMissingRequired,
		},
		{
			name:    "missing required JWT secret is rejected",
			env:     map[string]string{"AT_DATABASE_DSN": "postgres://localhost/at"},
			wantErr: config.ErrMissingRequired,
		},
		{
			name: "invalid duration is rejected",
			env: map[string]string{
				"AT_DATABASE_DSN":       "postgres://localhost/at",
				"AT_JWT_SIGNING_SECRET": "s3cret",
				"AT_READ_TIMEOUT":       "not-a-duration",
			},
			wantErr: nil, // a non-sentinel parse error; checked below
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Cannot use t.Parallel(): t.Setenv forbids it.
			for k, v := range tt.env {
				t.Setenv(k, v)
			}

			cfg, err := config.Load()

			if tt.name == "invalid duration is rejected" {
				if err == nil {
					t.Fatal("expected parse error, got nil")
				}
				return
			}

			if tt.wantErr != nil {
				if !errors.Is(err, tt.wantErr) {
					t.Fatalf("err = %v, want %v", err, tt.wantErr)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if tt.check != nil {
				tt.check(t, cfg)
			}
		})
	}
}
