// Package auth mints and verifies the JWT access/refresh tokens that
// authenticate clients to the backend.
//
// Tokens are signed with HMAC-SHA256 using the server's JWT signing secret
// (held server-side, sourced from config, never logged or returned in a
// response body). Access tokens are short-lived; refresh tokens are
// longer-lived and carry an opaque, crypto/rand token id so they can be
// distinguished and (later) revoked. The TokenService is constructed
// explicitly with functional options (no init, no globals) and injected into
// the layers that need it.
package auth

import (
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// Token type claim values. The "typ" custom claim distinguishes an access
// token from a refresh token so a refresh token cannot be replayed as an
// access token (and vice versa).
const (
	tokenTypeAccess  = "access"
	tokenTypeRefresh = "refresh"
)

// Default token lifetimes. Short-lived access tokens limit the blast radius of
// a leaked token; the longer refresh lifetime backs silent refresh.
const (
	defaultAccessTokenTTL  = 15 * time.Minute
	defaultRefreshTokenTTL = 30 * 24 * time.Hour
	defaultIssuer          = "action-tracker"
)

// jtiBytes is the number of random bytes in an opaque token id.
const jtiBytes = 16

// Errors returned by the token service. Callers map these to generic
// client-facing auth errors and log details server-side.
var (
	// ErrEmptySecret indicates the service was constructed without a signing
	// secret. Constructing with an empty secret is a configuration bug.
	ErrEmptySecret = errors.New("auth: empty signing secret")

	// ErrInvalidToken indicates a token failed signature/expiry/type
	// validation.
	ErrInvalidToken = errors.New("auth: invalid token")
)

// Claims is the JWT payload. Subject (registered claim) carries the account id.
type Claims struct {
	TokenType string `json:"typ"`
	jwt.RegisteredClaims
}

// TokenPair is the access + refresh token result returned at login/refresh.
type TokenPair struct {
	AccessToken      string    `json:"accessToken"`
	RefreshToken     string    `json:"refreshToken"`
	AccessExpiresAt  time.Time `json:"accessExpiresAt"`
	RefreshExpiresAt time.Time `json:"refreshExpiresAt"`
}

// TokenService issues and verifies JWTs. It holds the signing secret and is
// safe for concurrent use.
type TokenService struct {
	secret     []byte
	issuer     string
	accessTTL  time.Duration
	refreshTTL time.Duration
	now        func() time.Time
}

// Option configures the TokenService.
type Option func(*TokenService)

// WithAccessTTL overrides the access-token lifetime.
func WithAccessTTL(d time.Duration) Option {
	return func(s *TokenService) {
		if d > 0 {
			s.accessTTL = d
		}
	}
}

// WithRefreshTTL overrides the refresh-token lifetime.
func WithRefreshTTL(d time.Duration) Option {
	return func(s *TokenService) {
		if d > 0 {
			s.refreshTTL = d
		}
	}
}

// WithIssuer overrides the token issuer ("iss") claim.
func WithIssuer(iss string) Option {
	return func(s *TokenService) {
		if iss != "" {
			s.issuer = iss
		}
	}
}

// WithClock injects a time source for deterministic tests.
func WithClock(now func() time.Time) Option {
	return func(s *TokenService) {
		if now != nil {
			s.now = now
		}
	}
}

// NewTokenService builds a TokenService signed with secret. It returns an error
// if the secret is empty so misconfiguration fails fast at startup rather than
// silently issuing unsigned-equivalent tokens.
func NewTokenService(secret string, opts ...Option) (*TokenService, error) {
	if secret == "" {
		return nil, ErrEmptySecret
	}
	s := &TokenService{
		secret:     []byte(secret),
		issuer:     defaultIssuer,
		accessTTL:  defaultAccessTokenTTL,
		refreshTTL: defaultRefreshTokenTTL,
		now:        time.Now,
	}
	for _, opt := range opts {
		opt(s)
	}
	return s, nil
}

// IssuePair mints a fresh access + refresh token pair for the account.
func (s *TokenService) IssuePair(accountID string) (TokenPair, error) {
	now := s.now()

	access, accessExp, err := s.signToken(accountID, tokenTypeAccess, s.accessTTL, now)
	if err != nil {
		return TokenPair{}, err
	}
	refresh, refreshExp, err := s.signToken(accountID, tokenTypeRefresh, s.refreshTTL, now)
	if err != nil {
		return TokenPair{}, err
	}

	return TokenPair{
		AccessToken:      access,
		RefreshToken:     refresh,
		AccessExpiresAt:  accessExp,
		RefreshExpiresAt: refreshExp,
	}, nil
}

// signToken builds and signs one token of the given type.
func (s *TokenService) signToken(accountID, tokenType string, ttl time.Duration, now time.Time) (string, time.Time, error) {
	jti, err := newTokenID()
	if err != nil {
		return "", time.Time{}, err
	}
	expiresAt := now.Add(ttl)

	claims := Claims{
		TokenType: tokenType,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    s.issuer,
			Subject:   accountID,
			ID:        jti,
			IssuedAt:  jwt.NewNumericDate(now),
			NotBefore: jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(expiresAt),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, err := token.SignedString(s.secret)
	if err != nil {
		return "", time.Time{}, fmt.Errorf("auth: signing token: %w", err)
	}
	return signed, expiresAt, nil
}

// VerifyAccessToken validates an access token and returns the account id (the
// token subject). It rejects tokens of the wrong type, bad signature, or
// expired/not-yet-valid windows.
func (s *TokenService) VerifyAccessToken(raw string) (string, error) {
	return s.verify(raw, tokenTypeAccess)
}

// VerifyRefreshToken validates a refresh token and returns the account id. Used
// by the silent-refresh endpoint to mint a new pair.
func (s *TokenService) VerifyRefreshToken(raw string) (string, error) {
	return s.verify(raw, tokenTypeRefresh)
}

// verify parses, checks the signing method, validates standard claims, and
// confirms the token type matches wantType.
func (s *TokenService) verify(raw, wantType string) (string, error) {
	claims := &Claims{}
	parser := jwt.NewParser(
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
		jwt.WithIssuer(s.issuer),
		jwt.WithTimeFunc(s.now),
	)

	token, err := parser.ParseWithClaims(raw, claims, func(*jwt.Token) (any, error) {
		return s.secret, nil
	})
	if err != nil || !token.Valid {
		return "", ErrInvalidToken
	}
	if claims.TokenType != wantType {
		return "", ErrInvalidToken
	}
	if claims.Subject == "" {
		return "", ErrInvalidToken
	}
	return claims.Subject, nil
}

// newTokenID returns a URL-safe, base64-encoded random token id using
// crypto/rand (never math/rand) per golang-security.
func newTokenID() (string, error) {
	b := make([]byte, jtiBytes)
	if _, err := rand.Read(b); err != nil {
		return "", fmt.Errorf("auth: generating token id: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}
