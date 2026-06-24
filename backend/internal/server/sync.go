package server

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"strconv"

	"github.com/actiontracker/backend/internal/auth"
	"github.com/actiontracker/backend/internal/domain"
)

// SyncStore is the persistence capability the sync endpoints depend on. The
// server accepts this interface (not a concrete repository) so the handlers can
// be unit tested with a fake and stay decoupled from PostgreSQL.
//
// Push merges a batch of client changes for one account using deterministic
// last-writer-wins (the merge itself lives in the domain package) and returns
// the new sync token. Pull returns every change for the account whose sync
// token is greater than the supplied "since" value, plus the new high-water
// token. Deletes propagate as tombstones (records with Sync.Deleted == true),
// so the caller — not the store — decides visibility.
type SyncStore interface {
	PushActionItems(ctx context.Context, accountID string, changes []domain.ActionItem) (newToken int64, err error)
	PullActionItems(ctx context.Context, accountID string, since int64) (changes []domain.ActionItem, newToken int64, err error)
}

// --- request/response payloads ---

type syncPushRequest struct {
	Changes       []domain.ActionItem `json:"changes"`
	LastSyncToken int64               `json:"lastSyncToken"`
}

type syncPushResponse struct {
	Applied      int   `json:"applied"`
	NewSyncToken int64 `json:"newSyncToken"`
}

type syncPullResponse struct {
	Changes      []domain.ActionItem `json:"changes"`
	NewSyncToken int64               `json:"newSyncToken"`
}

// handleSyncPush applies a client's local changes for the authenticated
// account. The account id comes from the validated access token (never the
// body), so a client cannot push data attributed to another account (Req 13.3).
// Each change is stamped with the authenticated account before merge.
func (s *Server) handleSyncPush(w http.ResponseWriter, r *http.Request) {
	if s.sync == nil {
		writeError(w, s.logger, http.StatusNotImplemented, "sync is not available")
		return
	}

	accountID, ok := auth.AccountIDFromContext(r.Context())
	if !ok {
		writeError(w, s.logger, http.StatusUnauthorized, "authentication required")
		return
	}

	var req syncPushRequest
	if !decodeJSON(w, r, s.logger, &req) {
		return
	}

	// Stamp every change with the authenticated account (Property 29): the
	// server is authoritative for account ownership.
	for i := range req.Changes {
		req.Changes[i] = domain.StampActionItem(req.Changes[i], accountID)
	}

	ctx, cancel := contextWithTimeout(r, s.dbCallTimeout)
	defer cancel()

	newToken, err := s.sync.PushActionItems(ctx, accountID, req.Changes)
	if err != nil {
		s.logger.Error("sync push failed", slog.Any("error", err))
		writeError(w, s.logger, http.StatusInternalServerError, "could not sync changes")
		return
	}

	writeJSON(w, s.logger, http.StatusOK, syncPushResponse{
		Applied:      len(req.Changes),
		NewSyncToken: newToken,
	})
}

// handleSyncPull returns the account's changes newer than the "since" sync
// token (default 0 = full sync). Tombstones are included so deletes propagate
// to the pulling device.
func (s *Server) handleSyncPull(w http.ResponseWriter, r *http.Request) {
	if s.sync == nil {
		writeError(w, s.logger, http.StatusNotImplemented, "sync is not available")
		return
	}

	accountID, ok := auth.AccountIDFromContext(r.Context())
	if !ok {
		writeError(w, s.logger, http.StatusUnauthorized, "authentication required")
		return
	}

	since, err := parseSyncToken(r.URL.Query().Get("since"))
	if err != nil {
		writeError(w, s.logger, http.StatusBadRequest, "invalid sync token")
		return
	}

	ctx, cancel := contextWithTimeout(r, s.dbCallTimeout)
	defer cancel()

	changes, newToken, err := s.sync.PullActionItems(ctx, accountID, since)
	if err != nil {
		s.logger.Error("sync pull failed", slog.Any("error", err))
		writeError(w, s.logger, http.StatusInternalServerError, "could not fetch changes")
		return
	}

	writeJSON(w, s.logger, http.StatusOK, syncPullResponse{
		Changes:      changes,
		NewSyncToken: newToken,
	})
}

// parseSyncToken parses the "since" query value. Empty means a full sync from
// the beginning (0). Negative tokens are rejected.
func parseSyncToken(raw string) (int64, error) {
	if raw == "" {
		return 0, nil
	}
	v, err := strconv.ParseInt(raw, 10, 64)
	if err != nil || v < 0 {
		return 0, errors.New("invalid sync token")
	}
	return v, nil
}
