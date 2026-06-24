package server_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/actiontracker/backend/internal/accounts"
	"github.com/actiontracker/backend/internal/auth"
	"github.com/actiontracker/backend/internal/domain"
	"github.com/actiontracker/backend/internal/server"
)

// fakeAccountService is an injectable AccountService double. It records the
// input passed to CreateAccount so the signup test can assert the handler wired
// the optional organization choice through to the service (Req 13.1, 13.2).
type fakeAccountService struct {
	gotInput accounts.CreateAccountInput
	account  domain.Account
	err      error
}

func (f *fakeAccountService) CreateAccount(_ context.Context, in accounts.CreateAccountInput) (domain.Account, error) {
	f.gotInput = in
	if f.err != nil {
		return domain.Account{}, f.err
	}
	return f.account, nil
}

func (f *fakeAccountService) Authenticate(_ context.Context, _, _ string) (domain.Account, error) {
	return domain.Account{}, accounts.ErrInvalidCredentials
}

// newTestTokenIssuer builds a real TokenService so the signup response carries
// a genuine, verifiable token pair.
func newTestTokenIssuer(t *testing.T) server.TokenIssuer {
	t.Helper()
	ts, err := auth.NewTokenService("test-signing-secret-value")
	if err != nil {
		t.Fatalf("NewTokenService: %v", err)
	}
	return ts
}

// TestHandleCreateAccount_Signup is an example test for the signup HTTP flow:
// plain account creation and the optional organization join during signup. It
// asserts the handler returns 201 with the created account plus an initial
// token pair, and that the org choice is forwarded to the service.
//
// _Requirements: 13.1, 13.2_
func TestHandleCreateAccount_Signup(t *testing.T) {
	t.Parallel()

	orgID := "org-42"

	tests := []struct {
		name          string
		body          string
		account       domain.Account
		wantJoinOrgID string
		wantNewOrg    string
		wantOrgID     *string
	}{
		{
			name:    "plain account creation without org",
			body:    `{"email":"ada@example.com","password":"correct horse battery","displayName":"Ada"}`,
			account: domain.Account{ID: "acct-1", Email: "ada@example.com", DisplayName: "Ada", CreatedAt: time.Unix(0, 0).UTC()},
		},
		{
			name:          "signup joining an existing org",
			body:          `{"email":"grace@example.com","password":"correct horse battery","displayName":"Grace","joinOrgId":"org-42"}`,
			account:       domain.Account{ID: "acct-2", Email: "grace@example.com", DisplayName: "Grace", OrgID: &orgID, CreatedAt: time.Unix(0, 0).UTC()},
			wantJoinOrgID: "org-42",
			wantOrgID:     &orgID,
		},
		{
			name:       "signup creating a new org",
			body:       `{"email":"alan@example.com","password":"correct horse battery","displayName":"Alan","newOrgName":"Acme"}`,
			account:    domain.Account{ID: "acct-3", Email: "alan@example.com", DisplayName: "Alan", CreatedAt: time.Unix(0, 0).UTC()},
			wantNewOrg: "Acme",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			svc := &fakeAccountService{account: tt.account}
			srv := server.New("127.0.0.1:0",
				server.WithAccountService(svc),
				server.WithTokenIssuer(newTestTokenIssuer(t)),
			)

			req := httptest.NewRequest(http.MethodPost, "/accounts", strings.NewReader(tt.body))
			rec := httptest.NewRecorder()
			srv.Handler().ServeHTTP(rec, req)

			if rec.Code != http.StatusCreated {
				t.Fatalf("status = %d, want %d (body: %s)", rec.Code, http.StatusCreated, rec.Body.String())
			}

			// The org choice from the request is forwarded to the service.
			if svc.gotInput.Org.JoinOrgID != tt.wantJoinOrgID {
				t.Errorf("forwarded JoinOrgID = %q, want %q", svc.gotInput.Org.JoinOrgID, tt.wantJoinOrgID)
			}
			if svc.gotInput.Org.NewOrgName != tt.wantNewOrg {
				t.Errorf("forwarded NewOrgName = %q, want %q", svc.gotInput.Org.NewOrgName, tt.wantNewOrg)
			}

			var resp struct {
				Account struct {
					ID    string  `json:"id"`
					Email string  `json:"email"`
					OrgID *string `json:"orgId"`
				} `json:"account"`
				Tokens struct {
					AccessToken  string `json:"accessToken"`
					RefreshToken string `json:"refreshToken"`
				} `json:"tokens"`
			}
			if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
				t.Fatalf("decoding body: %v", err)
			}

			if resp.Account.ID != tt.account.ID {
				t.Errorf("account id = %q, want %q", resp.Account.ID, tt.account.ID)
			}
			// Signup signs the user in immediately: a token pair is returned.
			if resp.Tokens.AccessToken == "" || resp.Tokens.RefreshToken == "" {
				t.Error("expected a non-empty access + refresh token pair after signup")
			}
			if tt.wantOrgID != nil {
				if resp.Account.OrgID == nil || *resp.Account.OrgID != *tt.wantOrgID {
					t.Errorf("account orgId = %v, want %q", resp.Account.OrgID, *tt.wantOrgID)
				}
			}
		})
	}
}
