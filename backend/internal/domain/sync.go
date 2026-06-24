package domain

import "encoding/json"

// Sync conflict resolution (Req 14.4 / Property 32).
//
// When two devices edit the same record offline, a push can surface two
// concurrent versions of one entity. Resolution is deterministic
// last-writer-wins keyed on SyncMeta.UpdatedAt: the version with the greater
// UpdatedAt wins. To keep the merge deterministic AND order-independent
// (commutative) even when two versions carry the exact same UpdatedAt, ties are
// broken by stable secondary keys: greater Version, then a total order over the
// version's canonical (JSON) serialization. Because the canonical form encodes
// the whole value, the comparison is a strict total order — two distinct
// versions always have a well-defined winner that does not depend on argument
// order, and two byte-identical versions resolve to an indistinguishable
// result. The loser is reported separately so callers can record it to a
// conflict log for auditing without it affecting the winning state.
//
// This logic is pure and framework-free so it is validated by the same
// Property 32 test used on the Kotlin client, keeping both implementations
// behaviorally identical.

// Conflict pairs the winning version of a record with the version that lost the
// last-writer-wins resolution. Loser is preserved for the audit/conflict log.
type Conflict[T any] struct {
	Winner T
	Loser  T
}

// canonical returns a deterministic serialization of v used only as the final
// tie-breaker. encoding/json emits struct fields in declaration order, so the
// output is stable for a given value across calls and processes. On the
// unexpected event of a marshal error, an empty string is returned, which still
// yields a consistent (if arbitrary) ordering.
func canonical[T any](v T) string {
	b, err := json.Marshal(v)
	if err != nil {
		return ""
	}
	return string(b)
}

// resolve is the generic core: given two versions it returns the conflict
// (winner + loser) under deterministic last-writer-wins. It never mutates its
// inputs. The full value is used as the final tie-breaker so the result is a
// strict total order and independent of argument order.
func resolve[T any](a, b T, ma, mb SyncMeta) Conflict[T] {
	if firstWins(ma, mb, a, b) {
		return Conflict[T]{Winner: a, Loser: b}
	}
	return Conflict[T]{Winner: b, Loser: a}
}

// firstWins reports whether version a beats version b. UpdatedAt is the primary
// key; Version breaks an UpdatedAt tie; the canonical serialization breaks a
// full metadata tie. When a and b are byte-identical the function returns true,
// but the winner is then indistinguishable from the loser so argument order
// does not matter.
func firstWins[T any](ma, mb SyncMeta, a, b T) bool {
	switch {
	case ma.UpdatedAt.After(mb.UpdatedAt):
		return true
	case ma.UpdatedAt.Before(mb.UpdatedAt):
		return false
	case ma.Version > mb.Version:
		return true
	case ma.Version < mb.Version:
		return false
	default:
		return canonical(a) >= canonical(b)
	}
}

// ResolveActionItem resolves two concurrent versions of the same ActionItem via
// deterministic last-writer-wins. The two versions are expected to share an ID.
func ResolveActionItem(a, b ActionItem) Conflict[ActionItem] {
	return resolve(a, b, a.Sync, b.Sync)
}

// ResolveBucket resolves two concurrent versions of the same Bucket.
func ResolveBucket(a, b Bucket) Conflict[Bucket] {
	return resolve(a, b, a.Sync, b.Sync)
}

// ResolveActionPlan resolves two concurrent versions of the same ActionPlan.
func ResolveActionPlan(a, b ActionPlan) Conflict[ActionPlan] {
	return resolve(a, b, a.Sync, b.Sync)
}

// ResolveVoiceJournalEntry resolves two concurrent versions of the same
// VoiceJournalEntry.
func ResolveVoiceJournalEntry(a, b VoiceJournalEntry) Conflict[VoiceJournalEntry] {
	return resolve(a, b, a.Sync, b.Sync)
}

// ResolveGameResult resolves two concurrent versions of the same GameResult.
func ResolveGameResult(a, b GameResult) Conflict[GameResult] {
	return resolve(a, b, a.Sync, b.Sync)
}
