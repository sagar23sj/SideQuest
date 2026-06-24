package accounts

import "errors"

// Sentinel errors for the accounts domain. Handlers map these to generic,
// client-safe responses (golang-security: never reveal whether an email exists
// or whether the password was the wrong part of a credential pair).
var (
	// ErrEmailInUse indicates an account already exists for the email. It is
	// derived from a unique-constraint (SQLSTATE 23505) violation.
	ErrEmailInUse = errors.New("accounts: email already in use")

	// ErrInvalidCredentials is returned by Authenticate for any failure to
	// verify a login (unknown email OR wrong password). The single,
	// indistinguishable error prevents account enumeration.
	ErrInvalidCredentials = errors.New("accounts: invalid credentials")

	// ErrInvalidInput indicates the supplied account data failed validation
	// (empty/oversized email, weak password, etc.).
	ErrInvalidInput = errors.New("accounts: invalid input")

	// ErrOrganizationNotFound indicates a join was requested for an org id
	// that does not exist.
	ErrOrganizationNotFound = errors.New("accounts: organization not found")
)
