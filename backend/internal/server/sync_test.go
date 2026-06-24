package server_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/actiontracker/backend/internal/auth"
	"github.com/actiontracker/backend/internal/domain"
	"github.com/actiontracker/backend/internal/server"
)

// fakeSyncStore records pushed changes and returns canned pull results so the
// sync handlers can be exercised without a database.
type fakeSyncStore struct {
	pushedAccountID string
	pushed          []domain.ActionItem
	pushToken       int64

	pullSince   int64
	pullChanges []domain.ActionItem
	pullToken   int64
}

func (f *fakeSyncStore) PushActionItems(_ context.Context, accountID string, changes []domain.ActionItem) (int64, error) {
	f.pushedAccountID = accountID
	f.pushed = changes
	return f.pushToken, nil
}

func (f *fakeSyncStore) PullActionItems(_ context.Context, _ string, since int64) ([]domain.ActionItem, int64, error) {
	f.pullSince = since
	return f.pullChanges, f.pullToken, nil
}

// bearerFor mints a valid access token for accountID using the same secret the
// test server's token issuer uses.
func bearerFor(t *testing.T, accountID string) (string, server.TokenIssuer) {
	t.Helper()
	ts, err := auth.NewTokenService("test-signing-secret-value")
	if err != nil {
		t.Fatalf("NewTokenService: %v", err)
	}
	pair, err := ts.IssuePair(accountID)
	if err != nil {
		t.Fatalf("IssuePair: %v", err)
	}
	return pair.AccessToken, ts
}

// TestSyncPush_StampsAuthenticatedAccount verifies the push handler authorizes
// the request and stamps every change with the token's account id, never a
// body-supplied one (Req 13.3, 13.4).
func TestSyncPush_StampsAuthenticatedAccount(t *testing.T) {
	t.Parallel()

	token, issuer := bearerFor(t, "acct-owner")
	store := &fakeSyncStore{pushToken: 42}
	srv := server.New("127.0.0.1:0",
		server.WithSyncStore(store),
		server.WithTokenIssuer(issuer),
	)

	// Body claims a different account; the handler must override it.
	body := `{"changes":[{"id":"item-1","accountId":"attacker","title":"x"}],"lastSyncToken":0}`
	req := httptest.NewRequest(http.MethodPost, "/sync/push", strings.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	rec := httptest.NewRecorder()
	srv.Handler().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200 (body: %s)", rec.Code, rec.Body.String())
	}
	if store.pushedAccountID != "acct-owner" {
		t.Errorf("pushed account = %q, want %q", store.pushedAccountID, "acct-owner")
	}
	if len(store.pushed) != 1 || store.pushed[0].AccountID != "acct-owner" {
		t.Errorf("change not stamped with authenticated account: %+v", store.pushed)
	}

	var resp struct {
		Applied      int   `json:"applied"`
		NewSyncToken int64 `json:"newSyncToken"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decoding body: %v", err)
	}
	if resp.Applied != 1 || resp.NewSyncToken != 42 {
		t.Errorf("response = %+v, want applied=1 token=42", resp)
	}
}

// TestSyncEndpoints_RequireAuth verifies both endpoints reject unauthenticated
// requests with 401.
func TestSyncEndpoints_RequireAuth(t *testing.T) {
	t.Parallel()

	_, issuer := bearerFor(t, "acct-owner")
	srv := server.New("127.0.0.1:0",
		server.WithSyncStore(&fakeSyncStore{}),
		server.WithTokenIssuer(issuer),
	)

	tests := []struct {
		name   string
		method string
		path   string
	}{
		{"push without token", http.MethodPost, "/sync/push"},
		{"pull without token", http.MethodGet, "/sync/pull"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			req := httptest.NewRequest(tt.method, tt.path, strings.NewReader(`{}`))
			rec := httptest.NewRecorder()
			srv.Handler().ServeHTTP(rec, req)
			if rec.Code != http.StatusUnauthorized {
				t.Errorf("status = %d, want 401", rec.Code)
			}
		})
	}
}

// TestSyncPull_ForwardsSinceToken verifies the pull handler parses the "since"
// query parameter and returns the store's changes + new token.
func TestSyncPull_ForwardsSinceToken(t *testing.T) {
	t.Parallel()

	token, issuer := bearerFor(t, "acct-owner")
	store := &fakeSyncStore{
		pullChanges: []domain.ActionItem{{ID: "item-1", AccountID: "acct-owner", Title: "hello"}},
		pullToken:   99,
	}
	srv := server.New("127.0.0.1:0",
		server.WithSyncStore(store),
		server.WithTokenIssuer(issuer),
	)

	req := httptest.NewRequest(http.MethodGet, "/sync/pull?since=7", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	rec := httptest.NewRecorder()
	srv.Handler().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want 200 (body: %s)", rec.Code, rec.Body.String())
	}
	if store.pullSince != 7 {
		t.Errorf("since = %d, want 7", store.pullSince)
	}

	var resp struct {
		Changes      []domain.ActionItem `json:"changes"`
		NewSyncToken int64               `json:"newSyncToken"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("decoding body: %v", err)
	}
	if len(resp.Changes) != 1 || resp.NewSyncToken != 99 {
		t.Errorf("response = %+v, want 1 change and token 99", resp)
	}
}
