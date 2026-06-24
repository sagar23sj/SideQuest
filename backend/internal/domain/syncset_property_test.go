package domain

import (
	"math/rand"
	"reflect"
	"testing"
	"testing/quick"
	"time"
)

// Feature: action-tracker-app, Property 30: Sync makes data available across devices (round trip)
//
// Property 30 (Req 13.4): for any set of local records on one device, after
// pushing to the backend and pulling on a second device signed in to the same
// account, the second device's non-deleted records equal the first device's
// records.
//
// We model device 1's local store as the source of truth it pushes. The server
// starts (possibly) with prior state, merges the push via last-writer-wins, and
// device 2 pulls the visible (non-deleted) projection. The property asserts
// device 2's pulled records equal device 1's non-deleted records, for the
// records device 1 owns (i.e. where device 1's version wins or is the only one
// present). To keep the round trip well-defined we give device 1's pushed
// versions a strictly greater UpdatedAt than any pre-existing server state, so
// device 1 is the last writer for its records.
func TestSyncRoundTrip_Property30(t *testing.T) {
	t.Parallel()

	cfg := &quick.Config{MaxCount: 100}

	roundTrip := func(rt roundTripCase) bool {
		device1 := rt.device1

		// Server merges device 1's push onto any prior server state.
		serverStore := MergeActionItems(rt.serverPrior, device1)

		// Device 2 pulls the non-deleted projection.
		pulled := VisibleActionItems(serverStore)

		// Device 1's non-deleted records (its own view of what it created).
		device1Visible := map[string]ActionItem{}
		for _, item := range device1 {
			if !item.Sync.Deleted {
				device1Visible[item.ID] = item
			}
		}

		// Every non-deleted record device 1 pushed must appear identically on
		// device 2 (device 1 is the last writer by construction).
		for id, want := range device1Visible {
			got, ok := pulled[id]
			if !ok || !reflect.DeepEqual(got, want) {
				return false
			}
		}
		// Conversely, every pulled record device 1 owns matches device 1.
		for id, got := range pulled {
			if want, owned := device1Visible[id]; owned {
				if !reflect.DeepEqual(got, want) {
					return false
				}
			}
		}
		return true
	}

	if err := quick.Check(roundTrip, cfg); err != nil {
		t.Errorf("sync round trip does not make data available across devices: %v", err)
	}
}

// roundTripCase holds a generated device-1 push set and prior server state.
type roundTripCase struct {
	device1     []ActionItem
	serverPrior map[string]ActionItem
}

// Generate builds a set of device-1 records and prior server state. Device-1
// versions get timestamps strictly later than the server's prior versions so
// device 1 is the deterministic last writer for any shared IDs.
func (roundTripCase) Generate(rnd *rand.Rand, _ int) reflect.Value {
	base := time.Unix(1_700_000_000, 0).UTC()

	n := rnd.Intn(8)
	device1 := make([]ActionItem, 0, n)
	serverPrior := map[string]ActionItem{}

	for i := 0; i < n; i++ {
		id := randID(rnd)

		// Some records also exist on the server with an OLDER timestamp.
		if rnd.Intn(2) == 0 {
			serverPrior[id] = ActionItem{
				ID:    id,
				Title: "old-" + randID(rnd),
				Sync: SyncMeta{
					UpdatedAt: base, // older
					Version:   1,
				},
			}
		}

		device1 = append(device1, ActionItem{
			ID:        id,
			AccountID: "acct-1",
			Title:     "new-" + randID(rnd),
			Status:    ActionStatus(rnd.Intn(4)),
			Sync: SyncMeta{
				UpdatedAt: base.Add(time.Duration(1+rnd.Intn(1000)) * time.Second), // newer
				Version:   2,
				Deleted:   rnd.Intn(4) == 0, // some tombstones
			},
		})
	}

	return reflect.ValueOf(roundTripCase{device1: device1, serverPrior: serverPrior})
}
