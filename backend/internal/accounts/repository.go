package accounts

import (
	"context"
	"errors"
	"fmt"

	"github.com/actiontracker/backend/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

// pgUniqueViolation is the PostgreSQL SQLSTATE for a unique-constraint
// violation. A duplicate email insert surfaces with this code and is mapped to
// ErrEmailInUse.
const pgUniqueViolation = "23505"

// Querier is the minimal subset of the pgx pool API the repository needs. It is
// satisfied by both *pgxpool.Pool and pgx.Tx, so the same query helpers run
// inside or outside a transaction. Accepting this narrow interface (rather than
// the concrete pool) keeps the repository decoupled and testable.
type Querier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

// Beginner starts a transaction. *pgxpool.Pool satisfies it.
type Beginner interface {
	Begin(ctx context.Context) (pgx.Tx, error)
}

// Pool combines the query and transaction capabilities the repository depends
// on. *pgxpool.Pool satisfies this interface.
type Pool interface {
	Querier
	Beginner
}

// Repository persists accounts and organizations using parameterized queries
// over an injected pgx pool. All methods take a context so callers bound the
// database round trip.
type Repository struct {
	pool Pool
}

// NewRepository builds a Repository over the given pool. The pool is injected
// (accept interfaces, inject dependencies) so the repository can be unit tested
// against a fake and reused with the production *pgxpool.Pool.
func NewRepository(pool Pool) *Repository {
	return &Repository{pool: pool}
}

// createParams carries the validated, hashed values for a new account plus the
// optional organization intent.
type createParams struct {
	accountID    string
	email        string
	displayName  string
	passwordHash string

	// Exactly one of the following org intents may be set:
	joinOrgID  string // join an existing organization by id
	newOrgID   string // create a new organization with newOrgName
	newOrgName string
}

// createAccountTx inserts the account and (optionally) creates or validates an
// organization in a single transaction so account creation + org join are
// atomic (Req 13.2). On any error the transaction is rolled back and no partial
// state is persisted.
func (r *Repository) createAccountTx(ctx context.Context, p createParams) (domain.Account, error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return domain.Account{}, fmt.Errorf("accounts: begin tx: %w", err)
	}
	// Rollback is a no-op after a successful Commit; deferring it guarantees
	// cleanup on every early return path.
	defer func() { _ = tx.Rollback(ctx) }()

	var orgID *string
	switch {
	case p.newOrgName != "":
		if err := insertOrganization(ctx, tx, p.newOrgID, p.newOrgName); err != nil {
			return domain.Account{}, err
		}
		orgID = &p.newOrgID
	case p.joinOrgID != "":
		exists, err := organizationExists(ctx, tx, p.joinOrgID)
		if err != nil {
			return domain.Account{}, err
		}
		if !exists {
			return domain.Account{}, ErrOrganizationNotFound
		}
		orgID = &p.joinOrgID
	}

	account, err := insertAccount(ctx, tx, p, orgID)
	if err != nil {
		return domain.Account{}, err
	}

	if err := tx.Commit(ctx); err != nil {
		return domain.Account{}, fmt.Errorf("accounts: commit tx: %w", err)
	}
	return account, nil
}

// insertOrganization creates a new organization row and returns its created_at
// via RETURNING so no second round trip is needed.
func insertOrganization(ctx context.Context, q Querier, id, name string) error {
	const query = `
		INSERT INTO organizations (id, name)
		VALUES ($1, $2)`
	if _, err := q.Exec(ctx, query, id, name); err != nil {
		return fmt.Errorf("accounts: inserting organization: %w", err)
	}
	return nil
}

// organizationExists reports whether an organization id is present.
func organizationExists(ctx context.Context, q Querier, id string) (bool, error) {
	const query = `SELECT EXISTS (SELECT 1 FROM organizations WHERE id = $1)`
	var exists bool
	if err := q.QueryRow(ctx, query, id).Scan(&exists); err != nil {
		return false, fmt.Errorf("accounts: checking organization: %w", err)
	}
	return exists, nil
}

// insertAccount inserts the account row (parameterized) and returns the created
// Account. The password hash is written but never returned in the domain model.
// A duplicate email is mapped to ErrEmailInUse via the unique-constraint code.
func insertAccount(ctx context.Context, q Querier, p createParams, orgID *string) (domain.Account, error) {
	const query = `
		INSERT INTO accounts (id, email, display_name, password_hash, org_id)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING created_at`

	var account domain.Account
	err := q.QueryRow(ctx, query, p.accountID, p.email, p.displayName, p.passwordHash, orgID).
		Scan(&account.CreatedAt)
	if err != nil {
		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == pgUniqueViolation {
			return domain.Account{}, ErrEmailInUse
		}
		return domain.Account{}, fmt.Errorf("accounts: inserting account: %w", err)
	}

	account.ID = p.accountID
	account.Email = p.email
	account.DisplayName = p.displayName
	account.OrgID = orgID
	return account, nil
}

// credentials is the internal projection used by Authenticate: the account plus
// its stored password hash (which never leaves this package).
type credentials struct {
	account      domain.Account
	passwordHash string
}

// findCredentialsByEmail loads the account and its password hash for login.
// pgx.ErrNoRows is mapped to ErrInvalidCredentials so an unknown email is
// indistinguishable from a wrong password (golang-security: no enumeration).
func (r *Repository) findCredentialsByEmail(ctx context.Context, email string) (credentials, error) {
	const query = `
		SELECT id, email, display_name, password_hash, org_id, created_at
		FROM accounts
		WHERE email = $1`

	var (
		c            credentials
		passwordHash *string
		orgID        *string
	)
	err := r.pool.QueryRow(ctx, query, email).Scan(
		&c.account.ID,
		&c.account.Email,
		&c.account.DisplayName,
		&passwordHash,
		&orgID,
		&c.account.CreatedAt,
	)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return credentials{}, ErrInvalidCredentials
		}
		return credentials{}, fmt.Errorf("accounts: querying account by email: %w", err)
	}

	c.account.OrgID = orgID
	if passwordHash != nil {
		c.passwordHash = *passwordHash
	}
	return c, nil
}
