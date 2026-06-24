// Package domain holds the pure domain entities shared across the backend.
//
// These types mirror the shared OpenAPI 3 schema in api/openapi.yaml (the
// single source of truth from which Kotlin/Swift client models and Go server
// types are generated). Until oapi-codegen is run, the types needed now are
// hand-written here and kept in sync with the contract.
//
// This package is intentionally framework-free: no HTTP, no database, no
// provider SDKs. Later tasks (sync conflict resolution, puzzle generation,
// leaderboard aggregation) add pure logic here and are validated with the
// same correctness properties used on the Kotlin client.
package domain

import "time"

// ActionStatus is the state of an ActionItem.
//
// The zero value is ActionStatusUnknown so an uninitialized status is never
// silently treated as a valid state.
type ActionStatus int

const (
	ActionStatusUnknown    ActionStatus = iota // 0 = invalid/unset
	ActionStatusNotStarted                     // 1
	ActionStatusInProgress                     // 2
	ActionStatusCompleted                      // 3
)

// ContentType classifies the source content of an ActionItem.
type ContentType int

const (
	ContentTypeUnknown  ContentType = iota // 0 = invalid/unset (also "unsupported")
	ContentTypeLink                        // 1
	ContentTypeText                        // 2
	ContentTypeImage                       // 3
	ContentTypeVideoRef                    // 4
)

// TimeframeKind is the discriminator for a Timeframe (OpenAPI oneOf).
type TimeframeKind int

const (
	TimeframeKindUnknown      TimeframeKind = iota // 0 = invalid/unset
	TimeframeKindToday                             // 1
	TimeframeKindWithinADay                        // 2
	TimeframeKindWithinAWeek                       // 3
	TimeframeKindSpecificDate                      // 4
)

// Timeframe is the target window for acting on an ActionItem. It is modeled as
// a tagged union (discriminator + optional payload) matching the OpenAPI
// oneOf/discriminator schema. SpecificDate is set only when Kind is
// TimeframeKindSpecificDate.
type Timeframe struct {
	Kind         TimeframeKind `json:"kind"`
	SpecificDate *time.Time    `json:"specificDate,omitempty"` // nil unless Kind == SpecificDate
}

// SyncMeta is the sync metadata embedded in every syncable entity.
//
// UpdatedAt is server-authoritative after a push ack. Version increments per
// update for concurrency detection. Deleted is the tombstone flag. The
// client-only Dirty flag is not part of the server representation and is
// therefore omitted here.
type SyncMeta struct {
	UpdatedAt time.Time `json:"updatedAt"`
	Version   int64     `json:"version"`
	Deleted   bool      `json:"deleted"`
}

// LinkPreview is metadata fetched for a shared link. Resolved == false means
// the client should display RawURL instead of the title/thumbnail.
type LinkPreview struct {
	Title        *string `json:"title,omitempty"`
	ThumbnailURL *string `json:"thumbnailUrl,omitempty"`
	SourceName   *string `json:"sourceName,omitempty"`
	RawURL       string  `json:"rawUrl"`
	Resolved     bool    `json:"resolved"`
}

// WishlistFields are present when an ActionItem lives in a shopping bucket.
type WishlistFields struct {
	ProductName string  `json:"productName"`
	SourceLink  *string `json:"sourceLink,omitempty"`
	Purchased   bool    `json:"purchased"`
}

// ActionItem is a tracked task. IDs are client-generated UUIDs so records can
// be created offline.
type ActionItem struct {
	ID             string          `json:"id"`
	AccountID      string          `json:"accountId"`
	BucketID       string          `json:"bucketId"`
	Title          string          `json:"title"`
	Description    *string         `json:"description,omitempty"`
	ContentType    ContentType     `json:"contentType"`
	SourceContent  *string         `json:"sourceContent,omitempty"`
	Preview        *LinkPreview    `json:"preview,omitempty"`
	Timeframe      Timeframe       `json:"timeframe"`
	Status         ActionStatus    `json:"status"`
	CreatedAt      time.Time       `json:"createdAt"`
	IsWishlistItem bool            `json:"isWishlistItem"`
	Wishlist       *WishlistFields `json:"wishlist,omitempty"`
	Sync           SyncMeta        `json:"sync"`
}

// Bucket is a user-defined category. Name is unique per account.
type Bucket struct {
	ID              string   `json:"id"`
	AccountID       string   `json:"accountId"`
	Name            string   `json:"name"`
	IsShopping      bool     `json:"isShopping"`
	NotStartedColor string   `json:"notStartedColor"`
	InProgressColor string   `json:"inProgressColor"`
	CompletedColor  string   `json:"completedColor"`
	Sync            SyncMeta `json:"sync"`
}

// SubAction is one ordered step within an ActionPlan.
type SubAction struct {
	ID        string `json:"id"`
	Text      string `json:"text"`
	Order     int    `json:"order"`
	Completed bool   `json:"completed"`
}

// ActionPlan is an ordered set of sub-actions attached to an ActionItem.
type ActionPlan struct {
	ID           string      `json:"id"`
	ActionItemID string      `json:"actionItemId"`
	SubActions   []SubAction `json:"subActions"`
	Sync         SyncMeta    `json:"sync"`
}

// VoiceJournalEntry holds an audio reference and its transcript. Transcript is
// nil and TranscriptionFailed is true when transcription failed.
type VoiceJournalEntry struct {
	ID                     string   `json:"id"`
	AccountID              string   `json:"accountId"`
	AudioRef               string   `json:"audioRef"`
	Transcript             *string  `json:"transcript,omitempty"`
	TranscriptionFailed    bool     `json:"transcriptionFailed"`
	CreatedAt              time.Time `json:"createdAt"`
	ExtractedActionItemIDs []string `json:"extractedActionItemIds"`
	Sync                   SyncMeta `json:"sync"`
}

// GameType identifies an in-app memory game.
type GameType int

const (
	GameTypeUnknown     GameType = iota // 0 = invalid/unset
	GameTypeSpellingBee                 // 1
	GameTypeWordGuess                   // 2
)

// GameResult is a recorded score for a user, game, and date.
type GameResult struct {
	ID          string    `json:"id"`
	AccountID   string    `json:"accountId"`
	OrgID       string    `json:"orgId"`
	GameType    GameType  `json:"gameType"`
	Date        string    `json:"date"` // ISO calendar date, e.g. 2025-06-14
	Score       int       `json:"score"`
	CompletedAt time.Time `json:"completedAt"`
	Sync        SyncMeta  `json:"sync"`
}

// LeaderboardPeriod is the aggregation window for a leaderboard.
type LeaderboardPeriod int

const (
	LeaderboardPeriodUnknown LeaderboardPeriod = iota // 0 = invalid/unset
	LeaderboardPeriodDay                              // 1
	LeaderboardPeriodWeek                             // 2
	LeaderboardPeriodMonth                            // 3
)

// LeaderboardEntry is one ranked user on a leaderboard.
type LeaderboardEntry struct {
	AccountID   string `json:"accountId"`
	DisplayName string `json:"displayName"`
	TotalScore  int    `json:"totalScore"`
	Rank        int    `json:"rank"`
}

// Leaderboard is a ranked list of users for an org and period. Entries are
// sorted descending by TotalScore.
type Leaderboard struct {
	OrgID     string             `json:"orgId"`
	Period    LeaderboardPeriod  `json:"period"`
	PeriodKey string             `json:"periodKey"` // e.g. 2025-06-14, 2025-W24, 2025-06
	Entries   []LeaderboardEntry `json:"entries"`
}

// Account is a user's authenticated identity. OrgID is nil when the user is
// not a member of any organization.
type Account struct {
	ID          string    `json:"id"`
	Email       string    `json:"email"`
	DisplayName string    `json:"displayName"`
	OrgID       *string   `json:"orgId,omitempty"`
	CreatedAt   time.Time `json:"createdAt"`
}

// Organization is a group of users whose game scores share leaderboards.
type Organization struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	CreatedAt time.Time `json:"createdAt"`
}
