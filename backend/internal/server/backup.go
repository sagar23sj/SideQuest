package server

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"

	"github.com/actiontracker/backend/internal/auth"
)

// BackupStore persists a single JSON snapshot per account. The server depends
// on this interface so it can be tested with a fake.
type BackupStore interface {
	Put(ctx context.Context, accountID string, payload []byte, deviceID string) error
	Get(ctx context.Context, accountID string) (payload []byte, found bool, err error)
}

// maxBackupBytes caps the snapshot upload size (defensive; planner data is
// small text). 8 MiB leaves generous headroom.
const maxBackupBytes = 8 << 20

// handlePutBackup stores the authenticated account's snapshot. The account id
// comes from the validated access token, never the body, so a client can only
// write its own backup.
func (s *Server) handlePutBackup(w http.ResponseWriter, r *http.Request) {
	if s.backups == nil {
		writeError(w, s.logger, http.StatusNotImplemented, "backup is not available")
		return
	}
	accountID, ok := auth.AccountIDFromContext(r.Context())
	if !ok {
		writeError(w, s.logger, http.StatusUnauthorized, "authentication required")
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, maxBackupBytes)
	payload, err := io.ReadAll(r.Body)
	if err != nil {
		writeError(w, s.logger, http.StatusRequestEntityTooLarge, "backup too large")
		return
	}
	if !json.Valid(payload) {
		writeError(w, s.logger, http.StatusBadRequest, "invalid backup payload")
		return
	}

	deviceID := r.Header.Get("X-Device-Id")

	ctx, cancel := contextWithTimeout(r, s.dbCallTimeout)
	defer cancel()

	if err := s.backups.Put(ctx, accountID, payload, deviceID); err != nil {
		s.logger.Error("backup put failed", slog.Any("error", err))
		writeError(w, s.logger, http.StatusInternalServerError, "could not save your backup")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// handleGetBackup returns the authenticated account's latest snapshot as raw
// JSON, or 204 when none exists yet.
func (s *Server) handleGetBackup(w http.ResponseWriter, r *http.Request) {
	if s.backups == nil {
		writeError(w, s.logger, http.StatusNotImplemented, "backup is not available")
		return
	}
	accountID, ok := auth.AccountIDFromContext(r.Context())
	if !ok {
		writeError(w, s.logger, http.StatusUnauthorized, "authentication required")
		return
	}

	ctx, cancel := contextWithTimeout(r, s.dbCallTimeout)
	defer cancel()

	payload, found, err := s.backups.Get(ctx, accountID)
	if err != nil {
		s.logger.Error("backup get failed", slog.Any("error", err))
		writeError(w, s.logger, http.StatusInternalServerError, "could not load your backup")
		return
	}
	if !found {
		w.WriteHeader(http.StatusNoContent)
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(payload)
}
