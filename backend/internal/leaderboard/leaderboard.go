// Package leaderboard implements the server-side pure logic for organization
// leaderboards: mapping a score's date to its day/week/month period keys,
// aggregating per-user totals within a period, and ranking users by descending
// total (Req 12.2–12.6).
//
// The logic is deterministic and framework-free so the same Correctness
// Properties (26–28) validate it. Period isolation falls naturally out of the
// period-key mapping: a score only contributes to the board whose period key
// its date maps to, so new days/weeks/months start empty and the monthly board
// resets at month start.
package leaderboard

import (
	"fmt"
	"sort"
	"time"

	"github.com/actiontracker/backend/internal/domain"
)

// Score is the minimal input the aggregator needs: who scored, how much, and on
// what date. OrgID scopes the board; DisplayName is carried for ranked output.
type Score struct {
	OrgID       string
	AccountID   string
	DisplayName string
	Date        time.Time
	Points      int
}

// PeriodKey returns the canonical key for date under the given period:
//
//   - Day:   "2006-01-02"
//   - Week:  ISO year + week, e.g. "2025-W24"
//   - Month: "2006-01"
//
// Scores are isolated by this key, so two dates that map to different keys
// never share a board (Property 28). The date is normalized to UTC first so the
// mapping is stable regardless of the input location.
func PeriodKey(period domain.LeaderboardPeriod, date time.Time) string {
	d := date.UTC()
	switch period {
	case domain.LeaderboardPeriodDay:
		return d.Format("2006-01-02")
	case domain.LeaderboardPeriodWeek:
		year, week := d.ISOWeek()
		return fmt.Sprintf("%04d-W%02d", year, week)
	case domain.LeaderboardPeriodMonth:
		return d.Format("2006-01")
	default:
		return ""
	}
}

// Aggregate builds the leaderboard for one org, period, and period key from a
// set of scores (Req 12.2, 12.3). It includes only scores whose date maps to
// periodKey and whose OrgID matches orgID (Property 28), sums each user's
// points (Property 26), and ranks descending by total (Property 27).
//
// Ranking is stable and deterministic: ties on total score are broken by
// AccountID so the output ordering and rank assignment are reproducible. Ranks
// are dense over the sorted order (1-based); tied totals receive the same rank.
func Aggregate(period domain.LeaderboardPeriod, orgID, periodKey string, scores []Score) domain.Leaderboard {
	totals := map[string]int{}
	names := map[string]string{}

	for _, s := range scores {
		if s.OrgID != orgID {
			continue
		}
		if PeriodKey(period, s.Date) != periodKey {
			continue
		}
		totals[s.AccountID] += s.Points
		// Last writer for a display name wins; names are expected stable per
		// account so this is incidental.
		names[s.AccountID] = s.DisplayName
	}

	entries := make([]domain.LeaderboardEntry, 0, len(totals))
	for accountID, total := range totals {
		entries = append(entries, domain.LeaderboardEntry{
			AccountID:   accountID,
			DisplayName: names[accountID],
			TotalScore:  total,
		})
	}

	// Descending by total; deterministic tie-break by AccountID ascending.
	sort.Slice(entries, func(i, j int) bool {
		if entries[i].TotalScore != entries[j].TotalScore {
			return entries[i].TotalScore > entries[j].TotalScore
		}
		return entries[i].AccountID < entries[j].AccountID
	})

	// Assign dense ranks: equal totals share a rank, the next distinct total
	// takes the following rank.
	for i := range entries {
		if i == 0 {
			entries[i].Rank = 1
			continue
		}
		if entries[i].TotalScore == entries[i-1].TotalScore {
			entries[i].Rank = entries[i-1].Rank
		} else {
			entries[i].Rank = entries[i-1].Rank + 1
		}
	}

	return domain.Leaderboard{
		OrgID:     orgID,
		Period:    period,
		PeriodKey: periodKey,
		Entries:   entries,
	}
}

// AllPeriods returns the three period boards a single score contributes to.
// Recording a score updates all of them (Req 12.2).
func AllPeriods() []domain.LeaderboardPeriod {
	return []domain.LeaderboardPeriod{
		domain.LeaderboardPeriodDay,
		domain.LeaderboardPeriodWeek,
		domain.LeaderboardPeriodMonth,
	}
}
