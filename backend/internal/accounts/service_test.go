package accounts

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/actiontracker/backend/internal/domain"
)

// fakeStore is a hand-written test double implementing the unexported
// accountStore interface. Because accountStore is consumer-side and unexported,
// the test lives in package accounts (white-box) so it can implement it.
//
// It records the createParams it receives and returns a configured account or
// error, letting the tests assert what the service handed the persistence layer
// (normalized email, trimmed display name, a real password hash, and the org
// intent) without touching a database.
type fakeStore struct {
	// gotParams captures the params passed to createAccountTx for assertions.
	gotParams createParams
	// createCalled records whether createAccountTx was invoked, so validation
	// tests can assert the store is never reached.
	createCalled int

	// account is returned from createAccountTx when createErr is nil.
	account domain.Account
	// createErr, when non-nil, is returned from createAccountTx.
	createErr error
}

func (f *fakeStore) createAccountTx(_ context.Context, p createParams) (domain.Account, error) {
	f.createCalled++
	f.gotParams = p
	if f.createErr != nil {
		return domain.Account{}, f.createErr
	}
	return f.account, nil
}

func (f *fakeStore) findCredentialsByEmail(_ context.Context, _ string) (credentials, error) {
	// Not exercised by the signup tests.
	return credentials{}, ErrInvalidCredentials
}

// validPassword is comfortably above minPasswordLength so only the behavior
// under test (not length validation) drives each case.
const validPassword = "correct horse battery staple"

// TestService_CreateAccount_Signup covers the signup flow: plain account
// creation and the optional organization join/create at signup. These are
// example/unit tests (not property tests).
//
// _Requirements: 13.1, 13.2_
func TestService_CreateAccount_Signup(t *testing.T) {
	t.Parallel()

	t.Run("plain account creation without org", func(t *testing.T) {
		t.Parallel()

		want := domain.Account{
			ID:          "acct-1",
			Email:       "ada@example.com",
			DisplayName: "Ada Lovelace",
			CreatedAt:   time.Unix(0, 0).UTC(),
		}
		store := &fakeStore{account: want}
		svc := NewService(store)

		got, err := svc.CreateAccount(context.Background(), CreateAccountInput{
			Email:       "  Ada@Example.com  ",
			Password:    validPassword,
			DisplayName: "  Ada Lovelace  ",
		})
		if err != nil {
			t.Fatalf("CreateAccount returned error: %v", err)
		}

		if store.createCalled != 1 {
			t.Fatalf("createAccountTx called %d times, want 1", store.createCalled)
		}

		p := store.gotParams
		// Email is normalized (trimmed + lowercased) before persistence.
		if p.email != "ada@example.com" {
			t.Errorf("stored email = %q, want %q", p.email, "ada@example.com")
		}
		// Display name is trimmed before persistence.
		if p.displayName != "Ada Lovelace" {
			t.Errorf("stored displayName = %q, want %q", p.displayName, "Ada Lovelace")
		}
		// Password is hashed: the stored value must be non-empty and must never
		// equal the plaintext (Req 13.1). Hashing internals are out of scope.
		if p.passwordHash == "" {
			t.Error("stored passwordHash is empty, want a hash")
		}
		if p.passwordHash == validPassword {
			t.Error("stored passwordHash equals the plaintext password")
		}
		// No org intent for a plain signup.
		if p.joinOrgID != "" {
			t.Errorf("joinOrgID = %q, want empty", p.joinOrgID)
		}
		if p.newOrgName != "" {
			t.Errorf("newOrgName = %q, want empty", p.newOrgName)
		}
		if p.newOrgID != "" {
			t.Errorf("newOrgID = %q, want empty", p.newOrgID)
		}

		// The returned account mirrors what the store produced; the domain
		// Account type carries no password hash to expose.
		if got != want {
			t.Errorf("returned account = %+v, want %+v", got, want)
		}
	})

	t.Run("signup joining an existing org", func(t *testing.T) {
		t.Parallel()

		store := &fakeStore{account: domain.Account{ID: "acct-2"}}
		svc := NewService(store)

		_, err := svc.CreateAccount(context.Background(), CreateAccountInput{
			Email:       "grace@example.com",
			Password:    validPassword,
			DisplayName: "Grace Hopper",
			Org:         OrgIntent{JoinOrgID: "org-123"},
		})
		if err != nil {
			t.Fatalf("CreateAccount returned error: %v", err)
		}

		p := store.gotParams
		if p.joinOrgID != "org-123" {
			t.Errorf("joinOrgID = %q, want %q", p.joinOrgID, "org-123")
		}
		// Joining must not also request a new org.
		if p.newOrgName != "" {
			t.Errorf("newOrgName = %q, want empty", p.newOrgName)
		}
		if p.newOrgID != "" {
			t.Errorf("newOrgID = %q, want empty", p.newOrgID)
		}
	})

	t.Run("signup creating a new org", func(t *testing.T) {
		t.Parallel()

		store := &fakeStore{account: domain.Account{ID: "acct-3"}}
		svc := NewService(store)

		_, err := svc.CreateAccount(context.Background(), CreateAccountInput{
			Email:       "alan@example.com",
			Password:    validPassword,
			DisplayName: "Alan Turing",
			Org:         OrgIntent{NewOrgName: "  Acme  "},
		})
		if err != nil {
			t.Fatalf("CreateAccount returned error: %v", err)
		}

		p := store.gotParams
		// New org name is trimmed and a server-generated id is supplied.
		if p.newOrgName != "Acme" {
			t.Errorf("newOrgName = %q, want %q", p.newOrgName, "Acme")
		}
		if p.newOrgID == "" {
			t.Error("newOrgID is empty, want a generated id")
		}
		// Creating a new org must not also set a join target.
		if p.joinOrgID != "" {
			t.Errorf("joinOrgID = %q, want empty", p.joinOrgID)
		}
	})
}

// TestService_CreateAccount_Errors covers the validation and conflict paths of
// signup: ambiguous/invalid input is rejected before the store is reached, and
// a duplicate email surfaces as a client error.
//
// _Requirements: 13.1, 13.2_
func TestService_CreateAccount_Errors(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name  string
		input CreateAccountInput
		// storeErr configures the fake's createAccountTx error (zero value =
		// nil). Only used by cases that expect the store to be reached.
		storeErr error
		wantErr  error
		// wantStoreCalled is the expected number of createAccountTx calls.
		wantStoreCalled int
	}{
		{
			name: "both join and new org rejected before store",
			input: CreateAccountInput{
				Email:       "x@example.com",
				Password:    validPassword,
				DisplayName: "X",
				Org:         OrgIntent{JoinOrgID: "org-1", NewOrgName: "Acme"},
			},
			wantErr:         ErrInvalidInput,
			wantStoreCalled: 0,
		},
		{
			name: "invalid email rejected before store",
			input: CreateAccountInput{
				Email:       "not-an-email",
				Password:    validPassword,
				DisplayName: "X",
			},
			wantErr:         ErrInvalidInput,
			wantStoreCalled: 0,
		},
		{
			name: "short password rejected before store",
			input: CreateAccountInput{
				Email:       "x@example.com",
				Password:    "short",
				DisplayName: "X",
			},
			wantErr:         ErrInvalidInput,
			wantStoreCalled: 0,
		},
		{
			name: "email already in use surfaced from store",
			input: CreateAccountInput{
				Email:       "dup@example.com",
				Password:    validPassword,
				DisplayName: "Dup",
			},
			storeErr:        ErrEmailInUse,
			wantErr:         ErrEmailInUse,
			wantStoreCalled: 1,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			store := &fakeStore{createErr: tt.storeErr}
			svc := NewService(store)

			_, err := svc.CreateAccount(context.Background(), tt.input)
			if !errors.Is(err, tt.wantErr) {
				t.Fatalf("CreateAccount error = %v, want %v", err, tt.wantErr)
			}
			// All of these are client-facing (4xx) errors.
			if !IsClientError(err) {
				t.Errorf("IsClientError(%v) = false, want true", err)
			}
			if store.createCalled != tt.wantStoreCalled {
				t.Errorf("createAccountTx called %d times, want %d",
					store.createCalled, tt.wantStoreCalled)
			}
		})
	}
}
