package server_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/actiontracker/backend/internal/domain"
	"github.com/actiontracker/backend/internal/server"
)

// fakeLeaderboardStore returns canned boards so the handler can be exercised
// without a database.
type fakeLeaderboardStore struct {
	gotOrgID string
	boards   []domain.Leaderboard
}

func (f *fakeLeaderboardStore) Boards(_ context.Context, orgID string, _ domain.GameType) ([]domain.Leaderboard, error) {
	f.gotOrgID = orgID
	return f.boards, nil
}

// TestLeaderboards verifies auth enforcement, the no-org join prompt (Req 13.5),
// and a successful three-board read (Req 12.1).
func TestLeaderboards(t *testing.T) {
	t.Parallel()

	token, issuer := bearerFor(t, "acct-owner")
	boards := []domain.Leaderboard{
		{OrgID: "org-1", Period: domain.LeaderboardPeriodDay, PeriodKey: "2025-06-14"},
		{OrgID: "org-1", Period: domain.LeaderboardPeriodWeek, PeriodKey: "2025-W24"},
		{OrgID: "org-1", Period: domain.LeaderboardPeriodMonth, PeriodKey: "2025-06"},
	}

	t.Run("unauthenticated request is rejected", func(t *testing.T) {
		t.Parallel()
		srv := server.New("127.0.0.1:0",
			server.WithLeaderboardStore(&fakeLeaderboardStore{}),
			server.WithTokenIssuer(issuer),
		)
		req := httptest.NewRequest(http.MethodGet, "/leaderboards?orgId=org-1", nil)
		rec := httptest.NewRecorder()
		srv.Handler().ServeHTTP(rec, req)
		if rec.Code != http.StatusUnauthorized {
			t.Errorf("status = %d, want 401", rec.Code)
		}
	})

	t.Run("no-org user gets a join prompt", func(t *testing.T) {
		t.Parallel()
		srv := server.New("127.0.0.1:0",
			server.WithLeaderboardStore(&fakeLeaderboardStore{}),
			server.WithTokenIssuer(issuer),
		)
		req := httptest.NewRequest(http.MethodGet, "/leaderboards", nil) // no orgId
		req.Header.Set("Authorization", "Bearer "+token)
		rec := httptest.NewRecorder()
		srv.Handler().ServeHTTP(rec, req)
		if rec.Code != http.StatusConflict {
			t.Fatalf("status = %d, want 409", rec.Code)
		}
	})

	t.Run("returns three period boards for an org", func(t *testing.T) {
		t.Parallel()
		store := &fakeLeaderboardStore{boards: boards}
		srv := server.New("127.0.0.1:0",
			server.WithLeaderboardStore(store),
			server.WithTokenIssuer(issuer),
		)
		req := httptest.NewRequest(http.MethodGet, "/leaderboards?orgId=org-1&gameType=word_guess", nil)
		req.Header.Set("Authorization", "Bearer "+token)
		rec := httptest.NewRecorder()
		srv.Handler().ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("status = %d, want 200 (body: %s)", rec.Code, rec.Body.String())
		}
		if store.gotOrgID != "org-1" {
			t.Errorf("store orgId = %q, want org-1", store.gotOrgID)
		}

		var resp struct {
			Boards []domain.Leaderboard `json:"boards"`
		}
		if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
			t.Fatalf("decoding body: %v", err)
		}
		if len(resp.Boards) != 3 {
			t.Errorf("got %d boards, want 3 (day/week/month)", len(resp.Boards))
		}
	})
}
