// Package backup persists a single JSON snapshot per account — the offline
// client's whole-account backup of planner data (buckets, action items, plans,
// sub-actions). It is intentionally opaque: the server stores and returns the
// client's JSON payload as-is (validated only as well-formed JSON by the
// handler), so the backup format can evolve client-side without schema churn.
// All queries are parameterized.
package backup

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

// Querier is the minimal pgx surface this repository needs.
type Querier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

// Repository persists account backups over an injected pgx pool.
type Repository struct {
	pool Querier
}

// NewRepository builds a backup Repository over the given pool.
func NewRepository(pool Querier) *Repository { return &Repository{pool: pool} }

// Put stores (or replaces) the account's snapshot. deviceID is recorded for
// diagnostics and may be empty.
func (r *Repository) Put(ctx context.Context, accountID string, payload []byte, deviceID string) error {
	var device any
	if deviceID != "" {
		device = deviceID
	}
	if _, err := r.pool.Exec(ctx,
		`INSERT INTO account_backups (account_id, payload, device_id, updated_at)
		 VALUES ($1, $2, $3, now())
		 ON CONFLICT (account_id) DO UPDATE SET
		     payload    = EXCLUDED.payload,
		     device_id  = EXCLUDED.device_id,
		     updated_at = now()`,
		accountID, payload, device,
	); err != nil {
		return fmt.Errorf("backup: put: %w", err)
	}
	return nil
}

// Get returns the account's stored snapshot. found is false when none exists
// yet (a fresh account that has never backed up).
func (r *Repository) Get(ctx context.Context, accountID string) ([]byte, bool, error) {
	var payload []byte
	err := r.pool.QueryRow(ctx,
		`SELECT payload FROM account_backups WHERE account_id = $1`, accountID,
	).Scan(&payload)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, fmt.Errorf("backup: get: %w", err)
	}
	return payload, true, nil
}
