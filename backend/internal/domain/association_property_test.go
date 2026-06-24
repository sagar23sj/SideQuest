package domain

import (
	"testing"
	"testing/quick"
)

// Feature: action-tracker-app, Property 29: Data created while signed in is associated with the current account
//
// Property 29 (Req 13.3): for any Action_Item, Bucket, Voice_Journal_Entry, or
// Game result created while signed in as account A, the entity's accountId
// equals A. The Stamp* helpers must set AccountID to the authenticated account
// regardless of any client-supplied value, so a client can never attribute
// data to another account.
//
// testing/quick supplies arbitrary string inputs. We deliberately generate the
// entity's pre-existing (possibly attacker-supplied) AccountID and the
// authenticated account id A independently so the property covers the case
// where they differ. Only AccountID is relevant to this property; all other
// fields are left as zero values.
func TestAccountAssociation_Property29(t *testing.T) {
	t.Parallel()

	const minIterations = 100

	cfg := &quick.Config{MaxCount: minIterations}

	t.Run("ActionItem is associated with the authenticated account", func(t *testing.T) {
		t.Parallel()

		stampsAuthenticatedAccount := func(preExistingAccountID, authenticatedAccountID string) bool {
			item := ActionItem{AccountID: preExistingAccountID}

			stamped := StampActionItem(item, authenticatedAccountID)

			return stamped.AccountID == authenticatedAccountID &&
				AssociatedWith(stamped.AccountID, authenticatedAccountID)
		}

		if err := quick.Check(stampsAuthenticatedAccount, cfg); err != nil {
			t.Errorf("StampActionItem did not associate the item with the authenticated account: %v", err)
		}
	})

	t.Run("Bucket is associated with the authenticated account", func(t *testing.T) {
		t.Parallel()

		stampsAuthenticatedAccount := func(preExistingAccountID, authenticatedAccountID string) bool {
			bucket := Bucket{AccountID: preExistingAccountID}

			stamped := StampBucket(bucket, authenticatedAccountID)

			return stamped.AccountID == authenticatedAccountID &&
				AssociatedWith(stamped.AccountID, authenticatedAccountID)
		}

		if err := quick.Check(stampsAuthenticatedAccount, cfg); err != nil {
			t.Errorf("StampBucket did not associate the bucket with the authenticated account: %v", err)
		}
	})

	t.Run("VoiceJournalEntry is associated with the authenticated account", func(t *testing.T) {
		t.Parallel()

		stampsAuthenticatedAccount := func(preExistingAccountID, authenticatedAccountID string) bool {
			entry := VoiceJournalEntry{AccountID: preExistingAccountID}

			stamped := StampVoiceJournalEntry(entry, authenticatedAccountID)

			return stamped.AccountID == authenticatedAccountID &&
				AssociatedWith(stamped.AccountID, authenticatedAccountID)
		}

		if err := quick.Check(stampsAuthenticatedAccount, cfg); err != nil {
			t.Errorf("StampVoiceJournalEntry did not associate the entry with the authenticated account: %v", err)
		}
	})

	t.Run("GameResult is associated with the authenticated account", func(t *testing.T) {
		t.Parallel()

		stampsAuthenticatedAccount := func(preExistingAccountID, authenticatedAccountID string) bool {
			result := GameResult{AccountID: preExistingAccountID}

			stamped := StampGameResult(result, authenticatedAccountID)

			return stamped.AccountID == authenticatedAccountID &&
				AssociatedWith(stamped.AccountID, authenticatedAccountID)
		}

		if err := quick.Check(stampsAuthenticatedAccount, cfg); err != nil {
			t.Errorf("StampGameResult did not associate the result with the authenticated account: %v", err)
		}
	})
}
