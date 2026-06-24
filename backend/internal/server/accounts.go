package server

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"time"

	"github.com/actiontracker/backend/internal/accounts"
	"github.com/actiontracker/backend/internal/auth"
	"github.com/actiontracker/backend/internal/domain"
)

// AccountService is the account use cases the handlers depend on. The server
// accepts this interface (rather than the concrete *accounts.Service) so it can
// be tested with a fake and stays decoupled from the persistence layer.
type AccountService interface {
	CreateAccount(ctx context.Context, in accounts.CreateAccountInput) (domain.Account, error)
	Authenticate(ctx context.Context, email, password string) (domain.Account, error)
}

// TokenIssuer mints and refreshes JWT pairs and verifies access tokens.
// *auth.TokenService satisfies it. VerifyAccessToken backs the auth middleware
// that protects the sync endpoints.
type TokenIssuer interface {
	IssuePair(accountID string) (auth.TokenPair, error)
	VerifyRefreshToken(raw string) (string, error)
	VerifyAccessToken(raw string) (string, error)
}

// --- request/response payloads ---

type createAccountRequest struct {
	Email       string `json:"email"`
	Password    string `json:"password"`
	DisplayName string `json:"displayName"`
	// Optional organization choice (Req 13.2): supply at most one.
	JoinOrgID  string `json:"joinOrgId,omitempty"`
	NewOrgName string `json:"newOrgName,omitempty"`
}

type accountResponse struct {
	ID          string  `json:"id"`
	Email       string  `json:"email"`
	DisplayName string  `json:"displayName"`
	OrgID       *string `json:"orgId,omitempty"`
	CreatedAt   string  `json:"createdAt"`
}

type tokenResponse struct {
	AccessToken      string `json:"accessToken"`
	RefreshToken     string `json:"refreshToken"`
	AccessExpiresAt  string `json:"accessExpiresAt"`
	RefreshExpiresAt string `json:"refreshExpiresAt"`
}

// createAccountResponse is returned by POST /accounts: the new account plus an
// initial token pair so the client is signed in immediately after signup.
type createAccountResponse struct {
	Account accountResponse `json:"account"`
	Tokens  tokenResponse   `json:"tokens"`
}

type loginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type loginResponse struct {
	Account accountResponse `json:"account"`
	Tokens  tokenResponse   `json:"tokens"`
}

type refreshRequest struct {
	RefreshToken string `json:"refreshToken"`
}

// handleCreateAccount creates an account and (optionally) joins/creates an
// organization, then issues an initial token pair (Req 13.1, 13.2). The handler
// is thin: decode, call the service, map errors to generic responses, log
// details server-side.
func (s *Server) handleCreateAccount(w http.ResponseWriter, r *http.Request) {
	if s.accounts == nil || s.tokens == nil {
		writeError(w, s.logger, http.StatusNotImplemented, "accounts are not available")
		return
	}

	var req createAccountRequest
	if !decodeJSON(w, r, s.logger, &req) {
		return
	}

	ctx, cancel := contextWithTimeout(r, s.dbCallTimeout)
	defer cancel()

	account, err := s.accounts.CreateAccount(ctx, accounts.CreateAccountInput{
		Email:       req.Email,
		Password:    req.Password,
		DisplayName: req.DisplayName,
		Org: accounts.OrgIntent{
			JoinOrgID:  req.JoinOrgID,
			NewOrgName: req.NewOrgName,
		},
	})
	if err != nil {
		s.writeAccountError(w, "create-account", err)
		return
	}

	tokens, err := s.tokens.IssuePair(account.ID)
	if err != nil {
		s.logger.Error("issuing tokens after signup", slog.Any("error", err))
		writeError(w, s.logger, http.StatusInternalServerError, "could not complete sign-up")
		return
	}

	writeJSON(w, s.logger, http.StatusCreated, createAccountResponse{
		Account: toAccountResponse(account),
		Tokens:  toTokenResponse(tokens),
	})
}

// handleLogin authenticates a credential pair and returns a token pair.
func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	if s.accounts == nil || s.tokens == nil {
		writeError(w, s.logger, http.StatusNotImplemented, "accounts are not available")
		return
	}

	var req loginRequest
	if !decodeJSON(w, r, s.logger, &req) {
		return
	}

	ctx, cancel := contextWithTimeout(r, s.dbCallTimeout)
	defer cancel()

	account, err := s.accounts.Authenticate(ctx, req.Email, req.Password)
	if err != nil {
		s.writeAccountError(w, "login", err)
		return
	}

	tokens, err := s.tokens.IssuePair(account.ID)
	if err != nil {
		s.logger.Error("issuing tokens at login", slog.Any("error", err))
		writeError(w, s.logger, http.StatusInternalServerError, "could not complete sign-in")
		return
	}

	writeJSON(w, s.logger, http.StatusOK, loginResponse{
		Account: toAccountResponse(account),
		Tokens:  toTokenResponse(tokens),
	})
}

// handleRefresh validates a refresh token and issues a fresh token pair
// (server-side silent refresh).
func (s *Server) handleRefresh(w http.ResponseWriter, r *http.Request) {
	if s.tokens == nil {
		writeError(w, s.logger, http.StatusNotImplemented, "accounts are not available")
		return
	}

	var req refreshRequest
	if !decodeJSON(w, r, s.logger, &req) {
		return
	}
	if req.RefreshToken == "" {
		writeError(w, s.logger, http.StatusBadRequest, "invalid request body")
		return
	}

	accountID, err := s.tokens.VerifyRefreshToken(req.RefreshToken)
	if err != nil {
		s.logger.Warn("refresh token rejected", slog.Any("error", err))
		writeError(w, s.logger, http.StatusUnauthorized, "authentication required")
		return
	}

	tokens, err := s.tokens.IssuePair(accountID)
	if err != nil {
		s.logger.Error("issuing tokens on refresh", slog.Any("error", err))
		writeError(w, s.logger, http.StatusInternalServerError, "could not refresh session")
		return
	}

	writeJSON(w, s.logger, http.StatusOK, toTokenResponse(tokens))
}

// writeAccountError maps account-domain errors to generic client responses.
// Client-facing messages never reveal whether an email exists or which half of
// a credential pair was wrong; full details are logged server-side.
func (s *Server) writeAccountError(w http.ResponseWriter, op string, err error) {
	switch {
	case errors.Is(err, accounts.ErrInvalidCredentials):
		s.logger.Info("authentication failed", slog.String("op", op))
		writeError(w, s.logger, http.StatusUnauthorized, "invalid email or password")
	case errors.Is(err, accounts.ErrEmailInUse):
		// Generic 409: do not confirm which email is taken beyond the conflict.
		s.logger.Info("account email conflict", slog.String("op", op))
		writeError(w, s.logger, http.StatusConflict, "could not create the account")
	case errors.Is(err, accounts.ErrOrganizationNotFound):
		s.logger.Info("organization not found at signup", slog.String("op", op))
		writeError(w, s.logger, http.StatusBadRequest, "the selected organization is not available")
	case errors.Is(err, accounts.ErrInvalidInput):
		s.logger.Info("invalid account input", slog.String("op", op))
		writeError(w, s.logger, http.StatusBadRequest, "the submitted details are invalid")
	default:
		s.logger.Error("account operation failed", slog.String("op", op), slog.Any("error", err))
		writeError(w, s.logger, http.StatusInternalServerError, "the request could not be completed")
	}
}

func toAccountResponse(a domain.Account) accountResponse {
	return accountResponse{
		ID:          a.ID,
		Email:       a.Email,
		DisplayName: a.DisplayName,
		OrgID:       a.OrgID,
		CreatedAt:   a.CreatedAt.UTC().Format(time.RFC3339),
	}
}

func toTokenResponse(t auth.TokenPair) tokenResponse {
	return tokenResponse{
		AccessToken:      t.AccessToken,
		RefreshToken:     t.RefreshToken,
		AccessExpiresAt:  t.AccessExpiresAt.UTC().Format(time.RFC3339),
		RefreshExpiresAt: t.RefreshExpiresAt.UTC().Format(time.RFC3339),
	}
}
