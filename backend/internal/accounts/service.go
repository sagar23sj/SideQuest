// Package accounts implements account creation, optional organization join at
// signup, and credential authentication (Req 13.1, 13.2, 13.3).
//
// Passwords are hashed with argon2id and never stored or logged in plaintext.
// Account creation and an optional organization join happen in a single
// transaction so the two-step signup is atomic. The service depends on a narrow
// repository interface (accept interfaces, inject dependencies) and never
// returns the stored password hash to callers.
package accounts

import (
	"context"
	"errors"
	"fmt"
	"regexp"
	"strings"

	"github.com/actiontracker/backend/internal/domain"
)

// Validation bounds. These guard the trust boundary before any value reaches
// the database or the hashing function.
const (
	maxEmailLength       = 254 // RFC 5321 practical maximum
	maxDisplayNameLength = 100
	minPasswordLength    = 8
	maxPasswordLength    = 1024 // bound work for the memory-hard hash
	maxOrgNameLength     = 100
)

// emailRegex is a pragmatic email shape check compiled once at package level.
// It is intentionally permissive — the authoritative check is deliverability,
// which is out of scope here.
var emailRegex = regexp.MustCompile(`^[^@\s]+@[^@\s]+\.[^@\s]+$`)

// accountStore is the repository capability the service depends on. Defining it
// here (consumer side) keeps the service testable with a fake.
type accountStore interface {
	createAccountTx(ctx context.Context, p createParams) (domain.Account, error)
	findCredentialsByEmail(ctx context.Context, email string) (credentials, error)
}

// Service orchestrates account use cases over an injected store.
type Service struct {
	store accountStore
}

// NewService builds the account Service. The repository is injected so the
// service can be unit tested against a fake store.
func NewService(store accountStore) *Service {
	return &Service{store: store}
}

// OrgIntent expresses the optional organization choice at signup (Req 13.2).
// At most one of JoinOrgID / NewOrgName should be set; NewOrgName takes
// precedence if both are provided is rejected by validation.
type OrgIntent struct {
	// JoinOrgID joins an existing organization by id.
	JoinOrgID string
	// NewOrgName creates a new organization with this name and joins it.
	NewOrgName string
}

// CreateAccountInput is the validated input to CreateAccount.
type CreateAccountInput struct {
	Email       string
	Password    string
	DisplayName string
	Org         OrgIntent
}

// CreateAccount validates the input, hashes the password with argon2id, and
// persists the account — optionally creating/joining an organization in the
// same transaction (Req 13.1, 13.2). The returned Account never includes the
// password hash.
func (s *Service) CreateAccount(ctx context.Context, in CreateAccountInput) (domain.Account, error) {
	email := normalizeEmail(in.Email)
	displayName := strings.TrimSpace(in.DisplayName)

	if err := validateCreate(email, in.Password, displayName, in.Org); err != nil {
		return domain.Account{}, err
	}

	passwordHash, err := hashPassword(in.Password)
	if err != nil {
		return domain.Account{}, err
	}

	accountID, err := newUUID()
	if err != nil {
		return domain.Account{}, err
	}

	p := createParams{
		accountID:    accountID,
		email:        email,
		displayName:  displayName,
		passwordHash: passwordHash,
		joinOrgID:    in.Org.JoinOrgID,
	}
	if name := strings.TrimSpace(in.Org.NewOrgName); name != "" {
		newOrgID, err := newUUID()
		if err != nil {
			return domain.Account{}, err
		}
		p.newOrgID = newOrgID
		p.newOrgName = name
	}

	account, err := s.store.createAccountTx(ctx, p)
	if err != nil {
		return domain.Account{}, err
	}
	return account, nil
}

// Authenticate verifies an email/password pair and returns the account on
// success. Any failure (unknown email or wrong password) returns the single
// ErrInvalidCredentials so callers cannot distinguish the two cases.
func (s *Service) Authenticate(ctx context.Context, email, password string) (domain.Account, error) {
	normalized := normalizeEmail(email)
	if normalized == "" || password == "" {
		return domain.Account{}, ErrInvalidCredentials
	}

	creds, err := s.store.findCredentialsByEmail(ctx, normalized)
	if err != nil {
		return domain.Account{}, err
	}
	if creds.passwordHash == "" {
		// Account has no password set (e.g. future federated sign-in). Treat
		// as an invalid password attempt without revealing the distinction.
		return domain.Account{}, ErrInvalidCredentials
	}

	ok, err := verifyPassword(password, creds.passwordHash)
	if err != nil {
		return domain.Account{}, fmt.Errorf("accounts: verifying password: %w", err)
	}
	if !ok {
		return domain.Account{}, ErrInvalidCredentials
	}
	return creds.account, nil
}

// normalizeEmail trims surrounding whitespace and lowercases the address so
// uniqueness and lookups are case-insensitive.
func normalizeEmail(email string) string {
	return strings.ToLower(strings.TrimSpace(email))
}

// validateCreate enforces the input bounds at the trust boundary. It returns
// ErrInvalidInput (wrapped with a short reason) so callers can map it to a
// generic 400 without leaking specifics to the client.
func validateCreate(email, password, displayName string, org OrgIntent) error {
	switch {
	case email == "" || len(email) > maxEmailLength || !emailRegex.MatchString(email):
		return fmt.Errorf("%w: email", ErrInvalidInput)
	case displayName == "" || len(displayName) > maxDisplayNameLength:
		return fmt.Errorf("%w: display name", ErrInvalidInput)
	case len(password) < minPasswordLength || len(password) > maxPasswordLength:
		return fmt.Errorf("%w: password", ErrInvalidInput)
	}

	if org.JoinOrgID != "" && strings.TrimSpace(org.NewOrgName) != "" {
		return fmt.Errorf("%w: choose either an existing organization or a new one", ErrInvalidInput)
	}
	if name := strings.TrimSpace(org.NewOrgName); len(name) > maxOrgNameLength {
		return fmt.Errorf("%w: organization name", ErrInvalidInput)
	}
	return nil
}

// IsClientError reports whether err is a validation/conflict error that maps to
// a 4xx response (as opposed to an unexpected 5xx). Handlers use this to choose
// the status code while keeping the client message generic.
func IsClientError(err error) bool {
	return errors.Is(err, ErrInvalidInput) ||
		errors.Is(err, ErrEmailInUse) ||
		errors.Is(err, ErrOrganizationNotFound) ||
		errors.Is(err, ErrInvalidCredentials)
}
