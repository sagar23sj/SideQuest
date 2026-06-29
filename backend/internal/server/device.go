package server

import (
	"context"
	"log/slog"
	"net/http"
	"strings"
)

// DeviceAccountStore provisions silent, password-less device accounts. The
// server depends on this interface so it can be tested with a fake.
type DeviceAccountStore interface {
	EnsureAccount(ctx context.Context, deviceID string) (accountID string, err error)
}

// maxDeviceIDLength bounds the device identifier at the trust boundary.
const maxDeviceIDLength = 200

type deviceAccountRequest struct {
	DeviceID string `json:"deviceId"`
}

type deviceAccountResponse struct {
	AccountID string        `json:"accountId"`
	Tokens    tokenResponse `json:"tokens"`
}

// handleDeviceAccount provisions (or re-attaches to) the anonymous account for
// a client device and returns a token pair, so a brand-new install is "signed
// in" silently with zero user friction (Req 13.x, offline-first). The account
// can later be hardened by attaching an email/password.
func (s *Server) handleDeviceAccount(w http.ResponseWriter, r *http.Request) {
	if s.deviceAccounts == nil || s.tokens == nil {
		writeError(w, s.logger, http.StatusNotImplemented, "accounts are not available")
		return
	}

	var req deviceAccountRequest
	if !decodeJSON(w, r, s.logger, &req) {
		return
	}
	deviceID := strings.TrimSpace(req.DeviceID)
	if deviceID == "" || len(deviceID) > maxDeviceIDLength {
		writeError(w, s.logger, http.StatusBadRequest, "invalid device id")
		return
	}

	ctx, cancel := contextWithTimeout(r, s.dbCallTimeout)
	defer cancel()

	accountID, err := s.deviceAccounts.EnsureAccount(ctx, deviceID)
	if err != nil {
		s.logger.Error("device account provisioning failed", slog.Any("error", err))
		writeError(w, s.logger, http.StatusInternalServerError, "could not prepare your account")
		return
	}

	tokens, err := s.tokens.IssuePair(accountID)
	if err != nil {
		s.logger.Error("issuing tokens for device account", slog.Any("error", err))
		writeError(w, s.logger, http.StatusInternalServerError, "could not prepare your account")
		return
	}

	writeJSON(w, s.logger, http.StatusOK, deviceAccountResponse{
		AccountID: accountID,
		Tokens:    toTokenResponse(tokens),
	})
}
