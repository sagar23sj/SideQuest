package domain

// Sync round-trip core (Req 13.4 / Property 30).
//
// This models the server-authoritative merge of a push and the projection a
// pull returns, as pure logic over ActionItems keyed by ID. A device pushes its
// local records; the server merges each into its store using deterministic
// last-writer-wins (see sync.go); a second device pulls the merged set. The
// pull projection excludes tombstoned (deleted) records, so after a push+pull a
// second device's visible records equal the first device's non-deleted records.
//
// Keeping this pure lets Property 30 validate the round trip without a database
// or network, mirroring the Kotlin client's sync logic.

// MergeActionItems applies a batch of incoming (pushed) ActionItems onto an
// existing server store, keyed by ID, using last-writer-wins per record. The
// returned map is a new store; inputs are not mutated. Records not present in
// store are inserted; records already present are resolved against the incoming
// version with ResolveActionItem (the winner is kept).
func MergeActionItems(store map[string]ActionItem, incoming []ActionItem) map[string]ActionItem {
	merged := make(map[string]ActionItem, len(store)+len(incoming))
	for id, item := range store {
		merged[id] = item
	}
	for _, in := range incoming {
		existing, ok := merged[in.ID]
		if !ok {
			merged[in.ID] = in
			continue
		}
		merged[in.ID] = ResolveActionItem(existing, in).Winner
	}
	return merged
}

// VisibleActionItems returns the non-deleted records of a store. This is the
// projection a pull exposes to clients: tombstones are withheld so a delete on
// one device removes the record everywhere rather than resurrecting it.
func VisibleActionItems(store map[string]ActionItem) map[string]ActionItem {
	visible := make(map[string]ActionItem, len(store))
	for id, item := range store {
		if item.Sync.Deleted {
			continue
		}
		visible[id] = item
	}
	return visible
}
