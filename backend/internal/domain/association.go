package domain

// Account association rule (Req 13.3 / Property 29).
//
// While a user is signed in as account A, every entity they create
// (Action_Items, Buckets, Voice_Journal_Entries, Game results) must be
// associated with A. On the server the authoritative account id comes from the
// validated access token (the request principal), never from a client-supplied
// field — a client must not be able to write data attributed to another
// account.
//
// The helpers below are pure and framework-free so they can be exercised by the
// Property 29 test (task 23.2) and reused by the sync persistence layer
// (task 24) when it stamps account_id from the authenticated principal.

// AssociatedWith reports whether an entity's account id matches the
// authenticated account id. This is the invariant Property 29 checks.
func AssociatedWith(entityAccountID, authenticatedAccountID string) bool {
	return entityAccountID == authenticatedAccountID
}

// StampActionItem returns a copy of item whose AccountID is set to the
// authenticated account, enforcing the association rule regardless of any
// client-supplied AccountID.
func StampActionItem(item ActionItem, authenticatedAccountID string) ActionItem {
	item.AccountID = authenticatedAccountID
	return item
}

// StampBucket returns a copy of b associated with the authenticated account.
func StampBucket(b Bucket, authenticatedAccountID string) Bucket {
	b.AccountID = authenticatedAccountID
	return b
}

// StampVoiceJournalEntry returns a copy of e associated with the authenticated
// account.
func StampVoiceJournalEntry(e VoiceJournalEntry, authenticatedAccountID string) VoiceJournalEntry {
	e.AccountID = authenticatedAccountID
	return e
}

// StampGameResult returns a copy of r associated with the authenticated
// account.
func StampGameResult(r GameResult, authenticatedAccountID string) GameResult {
	r.AccountID = authenticatedAccountID
	return r
}
