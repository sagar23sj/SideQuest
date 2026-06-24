package games

import (
	"testing"
	"testing/quick"
	"time"

	"github.com/actiontracker/backend/internal/domain"
)

// Feature: action-tracker-app, Property 22: Completing a game records exactly one score
//
// Property 22 (Req 11.3): for any completed game play, exactly one Game result
// is recorded with the matching game type and date and the score computed from
// the final game state.
//
// We model "completing" as a single RecordScore call with no pre-existing
// result for the day. The property asserts the call records exactly one score
// (recorded == true) carrying the score derived from the final state, and that
// applying the same completion again (now with the recorded result present)
// does not record a second score.
func TestRecordScore_Property22(t *testing.T) {
	t.Parallel()

	cfg := &quick.Config{MaxCount: minIterations}

	recordsExactlyOne := func(score int, gtSel uint8) bool {
		gt := pickGameType(gtSel)
		date := "2025-06-14"

		newResult := domain.GameResult{
			ID:          "result-1",
			AccountID:   "acct-1",
			OrgID:       "org-1",
			GameType:    gt,
			Date:        date,
			Score:       score,
			CompletedAt: time.Unix(1_700_000_000, 0).UTC(),
		}

		// First completion: nothing recorded yet.
		stored, recorded := RecordScore(nil, newResult)
		if !recorded {
			return false
		}
		// The recorded result carries the matching game type, date, and score.
		if stored.GameType != gt || stored.Date != date || stored.Score != score {
			return false
		}

		// Second completion attempt with the now-existing result records
		// nothing new and returns the same result (one score total).
		second, recordedAgain := RecordScore(&stored, newResult)
		return !recordedAgain && second == stored
	}

	if err := quick.Check(recordsExactlyOne, cfg); err != nil {
		t.Errorf("completion did not record exactly one score: %v", err)
	}
}

// Feature: action-tracker-app, Property 23: Replaying a completed daily game does not rescore
//
// Property 23 (Req 11.4): for any user, game, and date that is already
// completed, a second completion attempt leaves both the number of recorded
// scores and the recorded score value unchanged and returns the existing
// result.
func TestRecordScore_Property23(t *testing.T) {
	t.Parallel()

	cfg := &quick.Config{MaxCount: minIterations}

	replayDoesNotRescore := func(originalScore, replayScore int) bool {
		existing := domain.GameResult{
			ID:        "result-1",
			AccountID: "acct-1",
			GameType:  domain.GameTypeWordGuess,
			Date:      "2025-06-14",
			Score:     originalScore,
		}
		// A replay completion arrives with a possibly different score.
		replay := existing
		replay.Score = replayScore

		got, recorded := RecordScore(&existing, replay)

		// No new score recorded, and the value is unchanged from the original.
		return !recorded && got == existing && got.Score == originalScore
	}

	if err := quick.Check(replayDoesNotRescore, cfg); err != nil {
		t.Errorf("replay rescored a completed game: %v", err)
	}
}

// TestWordGuessState_Score is an example test pinning the scoring/completion
// semantics RecordScore relies on.
func TestWordGuessState_Score(t *testing.T) {
	t.Parallel()

	spec := WordGuessSpec{Answer: "crane", MaxAttempts: 6}

	tests := []struct {
		name      string
		guesses   []string
		complete  bool
		scorePred func(int) bool
	}{
		{
			name:      "first-try solve scores highest",
			guesses:   []string{"crane"},
			complete:  true,
			scorePred: func(s int) bool { return s == 60 }, // (6-1+1)*10
		},
		{
			name:      "unsolved at attempt limit scores zero",
			guesses:   []string{"plane", "blame", "shame", "frame", "grace", "trace"},
			complete:  true,
			scorePred: func(s int) bool { return s == 0 },
		},
		{
			name:      "in progress is not complete",
			guesses:   []string{"plane"},
			complete:  false,
			scorePred: func(s int) bool { return s == 0 },
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			st := WordGuessState{Spec: spec, Guesses: tt.guesses}
			if st.IsComplete() != tt.complete {
				t.Errorf("IsComplete() = %v, want %v", st.IsComplete(), tt.complete)
			}
			if !tt.scorePred(st.Score()) {
				t.Errorf("Score() = %d failed predicate", st.Score())
			}
		})
	}
}
