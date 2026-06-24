package leaderboard

import (
	"math/rand"
	"reflect"
	"testing"
	"testing/quick"
	"time"

	"github.com/actiontracker/backend/internal/domain"
)

const minIterations = 100

// testOrg is the single organization all generated scores belong to so the
// aggregation properties exercise non-empty boards.
const testOrg = "org-1"

// Feature: action-tracker-app, Property 26: Recording a score updates all three period leaderboards by that score
//
// Property 26 (Req 12.2): for any sequence of recorded scores, each
// leaderboard's per-user total equals the sum of that user's scores falling
// within the board's period, for the daily, weekly, and monthly boards of the
// user's organization.
func TestAggregate_Property26(t *testing.T) {
	t.Parallel()

	cfg := &quick.Config{MaxCount: minIterations}

	totalsMatchPeriodSums := func(ss scoreSeq) bool {
		scores := ss.scores

		for _, period := range AllPeriods() {
			// Group scores by the period key they map to.
			byKey := map[string][]Score{}
			for _, s := range scores {
				key := PeriodKey(period, s.Date)
				byKey[key] = append(byKey[key], s)
			}

			for key, scoped := range byKey {
				board := Aggregate(period, testOrg, key, scores)

				// Reference: sum each user's points among scores mapping to
				// this key.
				want := map[string]int{}
				for _, s := range scoped {
					want[s.AccountID] += s.Points
				}

				got := map[string]int{}
				for _, e := range board.Entries {
					got[e.AccountID] = e.TotalScore
				}

				if !reflect.DeepEqual(got, want) {
					return false
				}
			}
		}
		return true
	}

	if err := quick.Check(totalsMatchPeriodSums, cfg); err != nil {
		t.Errorf("per-user period totals are wrong: %v", err)
	}
}

// Feature: action-tracker-app, Property 27: Leaderboards are ranked by descending total score
//
// Property 27 (Req 12.3): for any set of recorded scores, leaderboard entries
// are ordered by non-increasing total score and each entry's rank is consistent
// with that ordering.
func TestAggregate_Property27(t *testing.T) {
	t.Parallel()

	cfg := &quick.Config{MaxCount: minIterations}

	rankedDescending := func(ss scoreSeq) bool {
		scores := ss.scores
		period := domain.LeaderboardPeriodMonth

		// Pick any present period key; if none, the board is trivially ordered.
		var key string
		for _, s := range scores {
			key = PeriodKey(period, s.Date)
			break
		}

		board := Aggregate(period, testOrg, key, scores)
		entries := board.Entries

		for i := 1; i < len(entries); i++ {
			// Non-increasing total score.
			if entries[i-1].TotalScore < entries[i].TotalScore {
				return false
			}
			// Rank consistency: equal totals share a rank; a lower total has a
			// strictly greater rank.
			if entries[i].TotalScore == entries[i-1].TotalScore {
				if entries[i].Rank != entries[i-1].Rank {
					return false
				}
			} else if entries[i].Rank <= entries[i-1].Rank {
				return false
			}
		}
		// First entry (if any) ranks 1.
		if len(entries) > 0 && entries[0].Rank != 1 {
			return false
		}
		return true
	}

	if err := quick.Check(rankedDescending, cfg); err != nil {
		t.Errorf("leaderboard ranking is not descending/consistent: %v", err)
	}
}

// Feature: action-tracker-app, Property 28: Leaderboards isolate scores by period key
//
// Property 28 (Req 12.4, 12.5, 12.6): for any set of scores, a leaderboard for a
// given period key contains only scores whose date maps to that period key, so
// a new day, week, or month starts with no carried-over scores.
func TestAggregate_Property28(t *testing.T) {
	t.Parallel()

	cfg := &quick.Config{MaxCount: minIterations}

	isolatedByKey := func(ss scoreSeq) bool {
		scores := ss.scores

		for _, period := range AllPeriods() {
			// Collect all distinct keys present.
			keys := map[string]struct{}{}
			for _, s := range scores {
				keys[PeriodKey(period, s.Date)] = struct{}{}
			}

			for key := range keys {
				board := Aggregate(period, testOrg, key, scores)

				// Every entry's total must equal the sum of ONLY the scores
				// that map to this exact key — never any from another key.
				want := map[string]int{}
				for _, s := range scores {
					if PeriodKey(period, s.Date) == key {
						want[s.AccountID] += s.Points
					}
				}
				for _, e := range board.Entries {
					if e.TotalScore != want[e.AccountID] {
						return false
					}
				}
			}
		}
		return true
	}

	if err := quick.Check(isolatedByKey, cfg); err != nil {
		t.Errorf("leaderboard does not isolate scores by period key: %v", err)
	}

	t.Run("period boundary examples carry nothing over", func(t *testing.T) {
		t.Parallel()
		// Two dates in different months/weeks/days for one user.
		jan31 := time.Date(2025, 1, 31, 12, 0, 0, 0, time.UTC)
		feb1 := time.Date(2025, 2, 1, 12, 0, 0, 0, time.UTC)
		scores := []Score{
			{OrgID: testOrg, AccountID: "u1", DisplayName: "U1", Date: jan31, Points: 10},
			{OrgID: testOrg, AccountID: "u1", DisplayName: "U1", Date: feb1, Points: 5},
		}

		febBoard := Aggregate(domain.LeaderboardPeriodMonth, testOrg, "2025-02", scores)
		if len(febBoard.Entries) != 1 || febBoard.Entries[0].TotalScore != 5 {
			t.Errorf("February board = %+v, want only the 5-point Feb score", febBoard.Entries)
		}
		janBoard := Aggregate(domain.LeaderboardPeriodMonth, testOrg, "2025-01", scores)
		if len(janBoard.Entries) != 1 || janBoard.Entries[0].TotalScore != 10 {
			t.Errorf("January board = %+v, want only the 10-point Jan score", janBoard.Entries)
		}
	})
}

// scoreSeq is a generated sequence of scores for one org, drawn from a small
// set of users and dates spanning day/week/month boundaries. It implements
// quick.Generator.
type scoreSeq struct {
	scores []Score
}

// Generate produces 0..20 scores. Dates are chosen from a fixed set that spans
// multiple days, ISO weeks, and months so period isolation is exercised.
func (scoreSeq) Generate(rnd *rand.Rand, _ int) reflect.Value {
	users := []string{"u1", "u2", "u3"}
	dates := []time.Time{
		time.Date(2025, 1, 1, 9, 0, 0, 0, time.UTC),
		time.Date(2025, 1, 2, 9, 0, 0, 0, time.UTC),
		time.Date(2025, 1, 8, 9, 0, 0, 0, time.UTC),  // next ISO week
		time.Date(2025, 1, 31, 9, 0, 0, 0, time.UTC), // end of January
		time.Date(2025, 2, 1, 9, 0, 0, 0, time.UTC),  // next month
		time.Date(2025, 2, 15, 9, 0, 0, 0, time.UTC),
	}

	n := rnd.Intn(21)
	scores := make([]Score, 0, n)
	for i := 0; i < n; i++ {
		u := users[rnd.Intn(len(users))]
		scores = append(scores, Score{
			OrgID:       testOrg,
			AccountID:   u,
			DisplayName: u,
			Date:        dates[rnd.Intn(len(dates))],
			Points:      rnd.Intn(50),
		})
	}
	return reflect.ValueOf(scoreSeq{scores: scores})
}
