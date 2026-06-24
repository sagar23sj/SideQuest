package auth

import (
	"context"
	"net/http"
	"strings"
)

// contextKey is an unexported type for context keys defined in this package so
// they never collide with keys from other packages.
type contextKey int

const principalKey contextKey = iota

// TokenVerifier is the capability the middleware needs: validate an access
// token and return the authenticated account id. *TokenService satisfies it.
// Accepting the interface keeps the middleware testable.
type TokenVerifier interface {
	VerifyAccessToken(raw string) (string, error)
}

// ErrorWriter writes a generic client-facing error. The server provides one so
// the middleware reuses the same structured error format and slog logging as
// the rest of the API.
type ErrorWriter func(w http.ResponseWriter, status int, message string)

// RequireAccount returns middleware that extracts and validates the bearer
// access token, then stores the authenticated account id in the request
// context. Downstream handlers read it with AccountIDFromContext and stamp
// account_id from it (never from a client-supplied field) per Req 13.3.
//
// On a missing or invalid token it responds 401 with a generic message; token
// details are not echoed to the client.
func RequireAccount(verifier TokenVerifier, writeErr ErrorWriter) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			raw, ok := bearerToken(r)
			if !ok {
				writeErr(w, http.StatusUnauthorized, "authentication required")
				return
			}
			accountID, err := verifier.VerifyAccessToken(raw)
			if err != nil {
				writeErr(w, http.StatusUnauthorized, "authentication required")
				return
			}
			ctx := WithAccountID(r.Context(), accountID)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// WithAccountID returns a copy of ctx carrying the authenticated account id.
// Exposed so non-HTTP callers (e.g. tests, background jobs) can establish a
// principal.
func WithAccountID(ctx context.Context, accountID string) context.Context {
	return context.WithValue(ctx, principalKey, accountID)
}

// AccountIDFromContext returns the authenticated account id stored by the
// middleware, or ("", false) if the request is unauthenticated.
func AccountIDFromContext(ctx context.Context) (string, bool) {
	accountID, ok := ctx.Value(principalKey).(string)
	if !ok || accountID == "" {
		return "", false
	}
	return accountID, true
}

// bearerToken extracts the token from an "Authorization: Bearer <token>"
// header. The scheme match is case-insensitive per RFC 7235.
func bearerToken(r *http.Request) (string, bool) {
	const prefix = "bearer "
	header := r.Header.Get("Authorization")
	if len(header) <= len(prefix) || !strings.EqualFold(header[:len(prefix)], prefix) {
		return "", false
	}
	token := strings.TrimSpace(header[len(prefix):])
	if token == "" {
		return "", false
	}
	return token, true
}
