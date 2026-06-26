// Package sync implements the server-side persistence for offline-first sync:
// push (merge client changes with deterministic last-writer-wins) and pull
// (return changes newer than a sync token), scoped to one account (Req 13.4,
// 14.4).
//
// Conflict resolution itself is the pure domain.ResolveActionItem logic; this
// package is the thin persistence adapter that loads the current row, asks the
// domain who wins, and writes the winner. Deletes propagate as tombstones
// (deleted = true) so a delete on one device is not resurrected by a stale
// record from another. All queries are parameterized.
package sync

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/actiontracker/backend/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

// Querier is the minimal pgx surface the repository needs; satisfied by both
// *pgxpool.Pool and pgx.Tx so the same helpers run inside or outside a tx.
type Querier interface {
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

// Beginner starts a transaction. *pgxpool.Pool satisfies it.
type Beginner interface {
	Begin(ctx context.Context) (pgx.Tx, error)
}

// Pool combines query + transaction capabilities. *pgxpool.Pool satisfies it.
type Pool interface {
	Querier
	Beginner
}

// Repository persists ActionItem sync state over an injected pgx pool.
type Repository struct {
	pool Pool
}

// NewRepository builds a sync Repository over the given pool.
func NewRepository(pool Pool) *Repository {
	return &Repository{pool: pool}
}

// PushActionItems merges a batch of client changes for one account using
// deterministic last-writer-wins, in a single transaction. It returns the new
// high-water sync token (the max sync_seq after the merge). On any error the
// transaction rolls back so a partial batch is never persisted.
func (r *Repository) PushActionItems(ctx context.Context, accountID string, changes []domain.ActionItem) (int64, error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return 0, fmt.Errorf("sync: begin tx: %w", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()

	for _, incoming := range changes {
		// The account is authoritative; never trust a client-supplied id.
		incoming.AccountID = accountID

		existing, found, err := loadActionItem(ctx, tx, accountID, incoming.ID)
		if err != nil {
			return 0, err
		}

		winner := incoming
		if found {
			// Deterministic last-writer-wins over the stored vs incoming row.
			winner = domain.ResolveActionItem(existing, incoming).Winner
		}

		if err := upsertActionItem(ctx, tx, winner); err != nil {
			return 0, err
		}
	}

	token, err := maxSyncSeq(ctx, tx, accountID)
	if err != nil {
		return 0, err
	}

	if err := tx.Commit(ctx); err != nil {
		return 0, fmt.Errorf("sync: commit tx: %w", err)
	}
	return token, nil
}

// PullActionItems returns the account's ActionItems whose sync_seq is greater
// than since (including tombstones), ordered by sync_seq, plus the new
// high-water token. When there are no newer changes the token is unchanged
// (equal to since), so a client can safely persist it.
func (r *Repository) PullActionItems(ctx context.Context, accountID string, since int64) ([]domain.ActionItem, int64, error) {
	const query = `
		SELECT id, account_id, bucket_id, title, status, created_at,
		       timeframe_kind, timeframe_date,
		       updated_at, version, deleted, sync_seq
		FROM action_items
		WHERE account_id = $1 AND sync_seq > $2
		ORDER BY sync_seq ASC`

	rows, err := r.pool.Query(ctx, query, accountID, since)
	if err != nil {
		return nil, 0, fmt.Errorf("sync: querying changes: %w", err)
	}
	defer rows.Close()

	token := since
	var changes []domain.ActionItem
	for rows.Next() {
		var (
			item    domain.ActionItem
			tfKind  int16
			tfDate  *time.Time
			syncSeq int64
		)
		if err := rows.Scan(
			&item.ID, &item.AccountID, &item.BucketID, &item.Title, &item.Status,
			&item.CreatedAt, &tfKind, &tfDate,
			&item.Sync.UpdatedAt, &item.Sync.Version, &item.Sync.Deleted, &syncSeq,
		); err != nil {
			return nil, 0, fmt.Errorf("sync: scanning change: %w", err)
		}
		item.Timeframe = domain.Timeframe{Kind: domain.TimeframeKind(tfKind), SpecificDate: tfDate}
		changes = append(changes, item)
		if syncSeq > token {
			token = syncSeq
		}
	}
	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("sync: iterating changes: %w", err)
	}
	return changes, token, nil
}

// loadActionItem loads the current stored version of an item for LWW
// comparison. found is false when no row exists yet.
func loadActionItem(ctx context.Context, q Querier, accountID, id string) (domain.ActionItem, bool, error) {
	const query = `
		SELECT id, account_id, bucket_id, title, status, created_at,
		       timeframe_kind, timeframe_date,
		       updated_at, version, deleted
		FROM action_items
		WHERE account_id = $1 AND id = $2`

	var (
		item   domain.ActionItem
		tfKind int16
		tfDate *time.Time
	)
	err := q.QueryRow(ctx, query, accountID, id).Scan(
		&item.ID, &item.AccountID, &item.BucketID, &item.Title, &item.Status,
		&item.CreatedAt, &tfKind, &tfDate,
		&item.Sync.UpdatedAt, &item.Sync.Version, &item.Sync.Deleted,
	)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.ActionItem{}, false, nil
		}
		return domain.ActionItem{}, false, fmt.Errorf("sync: loading item: %w", err)
	}
	item.Timeframe = domain.Timeframe{Kind: domain.TimeframeKind(tfKind), SpecificDate: tfDate}
	return item, true, nil
}

// upsertActionItem writes the winning version, bumping sync_seq to the next
// sequence value so the change surfaces in subsequent pulls.
func upsertActionItem(ctx context.Context, q Querier, item domain.ActionItem) error {
	const query = `
		INSERT INTO action_items (
			id, account_id, bucket_id, title, content_type, status, created_at,
			timeframe_kind, timeframe_date,
			updated_at, version, deleted, sync_seq
		) VALUES (
			$1, $2, $3, $4, $5, $6, $7,
			$8, $9,
			$10, $11, $12, nextval('action_items_sync_seq')
		)
		ON CONFLICT (id) DO UPDATE SET
			bucket_id      = EXCLUDED.bucket_id,
			title          = EXCLUDED.title,
			status         = EXCLUDED.status,
			timeframe_kind = EXCLUDED.timeframe_kind,
			timeframe_date = EXCLUDED.timeframe_date,
			updated_at     = EXCLUDED.updated_at,
			version        = EXCLUDED.version,
			deleted        = EXCLUDED.deleted,
			sync_seq       = nextval('action_items_sync_seq')`

	var tfDate *time.Time
	if item.Timeframe.Kind == domain.TimeframeKindSpecificDate {
		tfDate = item.Timeframe.SpecificDate
	}

	_, err := q.Exec(ctx, query,
		item.ID, item.AccountID, item.BucketID, item.Title, int16(item.ContentType),
		int16(item.Status), item.CreatedAt,
		int16(item.Timeframe.Kind), tfDate,
		item.Sync.UpdatedAt, item.Sync.Version, item.Sync.Deleted,
	)
	if err != nil {
		return fmt.Errorf("sync: upserting item: %w", err)
	}
	return nil
}

// maxSyncSeq returns the highest sync_seq for an account, or 0 if it has no
// rows. Used as the push response's new sync token.
func maxSyncSeq(ctx context.Context, q Querier, accountID string) (int64, error) {
	const query = `SELECT COALESCE(MAX(sync_seq), 0) FROM action_items WHERE account_id = $1`
	var token int64
	if err := q.QueryRow(ctx, query, accountID).Scan(&token); err != nil {
		return 0, fmt.Errorf("sync: reading high-water token: %w", err)
	}
	return token, nil
}
