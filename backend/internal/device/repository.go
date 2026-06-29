// Package device implements silent, password-less "device accounts": a stable
// account provisioned from a client device identifier so a user gets cloud
// backup with zero sign-up friction. The account can later be hardened by
// attaching an email/password (handled by the accounts package).
//
// Provisioning is deterministic by device id (encoded into a reserved internal
// email), so a reinstall or clear-data on the same device recovers the same
// account — which is what lets the client restore its backup. All queries are
// parameterized.
package device

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

// Querier is the minimal pgx surface this repository needs; *pgxpool.Pool
// satisfies it.
type Querier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

// Repository provisions device accounts over an injected pgx pool.
type Repository struct {
	pool Querier
}

// NewRepository builds a device-account Repository over the given pool.
func NewRepository(pool Querier) *Repository { return &Repository{pool: pool} }

// EnsureAccount returns the account id for deviceID, creating an anonymous
// (password-less, org-less) account on first contact. It is idempotent: the
// same device id always maps to the same account, so a wiped/reinstalled device
// re-attaches to its existing data.
func (r *Repository) EnsureAccount(ctx context.Context, deviceID string) (string, error) {
	email := deviceEmail(deviceID)

	var id string
	err := r.pool.QueryRow(ctx, `SELECT id FROM accounts WHERE email = $1`, email).Scan(&id)
	if err == nil {
		return id, nil
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		return "", fmt.Errorf("device: lookup account: %w", err)
	}

	newID, err := newUUID()
	if err != nil {
		return "", err
	}
	if _, err := r.pool.Exec(ctx,
		`INSERT INTO accounts (id, email, display_name, password_hash, org_id)
		 VALUES ($1, $2, $3, NULL, NULL)
		 ON CONFLICT (email) DO NOTHING`,
		newID, email, "Adventurer",
	); err != nil {
		return "", fmt.Errorf("device: insert account: %w", err)
	}

	// Re-read so a concurrent insert (ON CONFLICT DO NOTHING) still yields the
	// winning row's id.
	if err := r.pool.QueryRow(ctx, `SELECT id FROM accounts WHERE email = $1`, email).Scan(&id); err != nil {
		return "", fmt.Errorf("device: reload account: %w", err)
	}
	return id, nil
}

// deviceEmail encodes a device id into a reserved internal email so the unique
// email constraint also enforces "one account per device".
func deviceEmail(deviceID string) string {
	clean := strings.ToLower(strings.TrimSpace(deviceID))
	return "device-" + clean + "@device.sidequest.local"
}

// newUUID returns a random RFC 4122 v4 UUID (crypto/rand, never math/rand).
func newUUID() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", fmt.Errorf("device: generating uuid: %w", err)
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	var dst [36]byte
	hex.Encode(dst[0:8], b[0:4])
	dst[8] = '-'
	hex.Encode(dst[9:13], b[4:6])
	dst[13] = '-'
	hex.Encode(dst[14:18], b[6:8])
	dst[18] = '-'
	hex.Encode(dst[19:23], b[8:10])
	dst[23] = '-'
	hex.Encode(dst[24:36], b[10:16])
	return string(dst[:]), nil
}
