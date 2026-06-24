package domain

import (
	"math/rand"
	"reflect"
	"testing"
	"testing/quick"
	"time"
)

// Feature: action-tracker-app, Property 32: Conflict resolution is deterministic last-writer-wins
//
// Property 32 (Req 14.4): for any two concurrent versions of the same record,
// the merged result equals the version with the greater updatedAt; the merge is
// deterministic and order-independent (commutative).
//
// We generate pairs of ActionItem versions sharing an ID but with arbitrary
// SyncMeta (UpdatedAt/Version) and arbitrary content, then assert:
//   - the winner is the version with the greater UpdatedAt (when they differ);
//   - resolving (a, b) and (b, a) yields the same winner (commutativity);
//   - resolving is deterministic (repeating yields the identical result).
//
// testing/quick drives the generation via a custom Generate so the two versions
// always share an ID (the realistic conflict shape) while still exercising
// equal-UpdatedAt ties.
func TestResolveConflict_Property32(t *testing.T) {
	t.Parallel()

	const minIterations = 100
	cfg := &quick.Config{MaxCount: minIterations}

	t.Run("winner has the greater updatedAt", func(t *testing.T) {
		t.Parallel()

		greaterUpdatedAtWins := func(p conflictPair) bool {
			c := ResolveActionItem(p.A, p.B)
			// When the timestamps differ, the winner must be the later one.
			switch {
			case p.A.Sync.UpdatedAt.After(p.B.Sync.UpdatedAt):
				return c.Winner.Sync.UpdatedAt.Equal(p.A.Sync.UpdatedAt) && c.Winner.ID == p.A.ID
			case p.B.Sync.UpdatedAt.After(p.A.Sync.UpdatedAt):
				return c.Winner.Sync.UpdatedAt.Equal(p.B.Sync.UpdatedAt) && c.Winner.ID == p.B.ID
			default:
				// Equal timestamps: winner must be one of the two inputs and
				// its UpdatedAt equals the shared timestamp.
				return c.Winner.Sync.UpdatedAt.Equal(p.A.Sync.UpdatedAt)
			}
		}

		if err := quick.Check(greaterUpdatedAtWins, cfg); err != nil {
			t.Errorf("winner is not the greater-updatedAt version: %v", err)
		}
	})

	t.Run("resolution is order-independent (commutative)", func(t *testing.T) {
		t.Parallel()

		commutative := func(p conflictPair) bool {
			ab := ResolveActionItem(p.A, p.B)
			ba := ResolveActionItem(p.B, p.A)
			// Same winner and same loser regardless of argument order.
			return reflect.DeepEqual(ab.Winner, ba.Winner) &&
				reflect.DeepEqual(ab.Loser, ba.Loser)
		}

		if err := quick.Check(commutative, cfg); err != nil {
			t.Errorf("conflict resolution is not commutative: %v", err)
		}
	})

	t.Run("resolution is deterministic (repeatable)", func(t *testing.T) {
		t.Parallel()

		deterministic := func(p conflictPair) bool {
			first := ResolveActionItem(p.A, p.B)
			second := ResolveActionItem(p.A, p.B)
			return reflect.DeepEqual(first, second)
		}

		if err := quick.Check(deterministic, cfg); err != nil {
			t.Errorf("conflict resolution is not deterministic: %v", err)
		}
	})

	t.Run("winner and loser are exactly the two inputs", func(t *testing.T) {
		t.Parallel()

		bothPreserved := func(p conflictPair) bool {
			c := ResolveActionItem(p.A, p.B)
			// The pair {winner, loser} is exactly {A, B} (no value invented).
			matchesAB := reflect.DeepEqual(c.Winner, p.A) && reflect.DeepEqual(c.Loser, p.B)
			matchesBA := reflect.DeepEqual(c.Winner, p.B) && reflect.DeepEqual(c.Loser, p.A)
			return matchesAB || matchesBA
		}

		if err := quick.Check(bothPreserved, cfg); err != nil {
			t.Errorf("winner/loser are not the original inputs: %v", err)
		}
	})
}

// conflictPair is two concurrent versions of the same record (same ID) used by
// the Property 32 test. It implements quick.Generator to produce realistic
// conflict shapes, including a meaningful fraction of equal-UpdatedAt ties.
type conflictPair struct {
	A ActionItem
	B ActionItem
}

// Generate implements quick.Generator. The two versions share an ID. UpdatedAt
// values are drawn from a small window of timestamps so equal-UpdatedAt ties
// occur often enough to exercise the tie-break path; Version and content also
// vary independently.
func (conflictPair) Generate(rnd *rand.Rand, _ int) reflect.Value {
	id := randID(rnd)

	// Base time plus a small jitter (0..3 seconds) so collisions are common.
	base := time.Unix(1_700_000_000, 0).UTC()

	mk := func() ActionItem {
		return ActionItem{
			ID:        id,
			AccountID: randID(rnd),
			BucketID:  randID(rnd),
			Title:     randID(rnd),
			Status:    ActionStatus(rnd.Intn(4)),
			Sync: SyncMeta{
				UpdatedAt: base.Add(time.Duration(rnd.Intn(4)) * time.Second),
				Version:   int64(rnd.Intn(3)),
				Deleted:   rnd.Intn(2) == 0,
			},
		}
	}

	return reflect.ValueOf(conflictPair{A: mk(), B: mk()})
}

// randID returns a short random alphanumeric identifier.
func randID(rnd *rand.Rand) string {
	const alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
	n := 1 + rnd.Intn(8)
	b := make([]byte, n)
	for i := range b {
		b[i] = alphabet[rnd.Intn(len(alphabet))]
	}
	return string(b)
}
