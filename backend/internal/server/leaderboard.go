package server

import (
	"context"
	"errors"
	"log/slog"
	"net/http"

	"github.com/actiontracker/backend/internal/auth"
	"github.com/actiontracker/backend/internal/domain"
)

// LeaderboardStore is the read capability the leaderboard endpoint depends on.
// Given an org and game type it returns the three period boards (day/week/
// month) for the current period keys. The server accepts this interface so the
// handler can be tested with a fake and stays decoupled from PostgreSQL.
type LeaderboardStore interface {
	Boards(ctx context.Context, orgID string, gameType domain.GameType) ([]domain.Leaderboard, error)
}

type leaderboardResponse struct {
	Boards []domain.Leaderboard `json:"boards"`
}

// handleLeaderboards returns the day/week/month leaderboards for an org
// (Req 12.1, 12.x). The caller must be authenticated; a signed-in user with no
// organization receives 409 with a join prompt (Req 13.5) — the no-org case is
// surfaced via ErrNoOrganization from the store/membership check.
func (s *Server) handleLeaderboards(w http.ResponseWriter, r *http.Request) {
	if s.leaderboards == nil {
		writeError(w, s.logger, http.StatusNotImplemented, "leaderboards are not available")
		return
	}

	if _, ok := auth.AccountIDFromContext(r.Context()); !ok {
		writeError(w, s.logger, http.StatusUnauthorized, "authentication required")
		return
	}

	orgID := r.URL.Query().Get("orgId")
	if orgID == "" {
		// A signed-in user with no organization sees a join prompt rather than
		// an empty board (Req 13.5).
		writeError(w, s.logger, http.StatusConflict, "join an organization to see leaderboards")
		return
	}

	gameType, err := parseGameType(r.URL.Query().Get("gameType"))
	if err != nil {
		writeError(w, s.logger, http.StatusBadRequest, "invalid game type")
		return
	}

	ctx, cancel := contextWithTimeout(r, s.dbCallTimeout)
	defer cancel()

	boards, err := s.leaderboards.Boards(ctx, orgID, gameType)
	if err != nil {
		s.logger.Error("leaderboard read failed", slog.Any("error", err))
		writeError(w, s.logger, http.StatusInternalServerError, "could not load leaderboards")
		return
	}

	writeJSON(w, s.logger, http.StatusOK, leaderboardResponse{Boards: boards})
}

// parseGameType maps the query value to a GameType. Empty defaults to
// Spelling_Bee so a board is always selectable; unknown values are rejected.
func parseGameType(raw string) (domain.GameType, error) {
	switch raw {
	case "", "spelling_bee":
		return domain.GameTypeSpellingBee, nil
	case "word_guess":
		return domain.GameTypeWordGuess, nil
	default:
		return domain.GameTypeUnknown, errors.New("invalid game type")
	}
}
