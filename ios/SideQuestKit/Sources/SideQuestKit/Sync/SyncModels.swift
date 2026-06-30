import Foundation

// MARK: - Sync wire models (Generated_Models)
//
// Swift mirrors of the `/sync/push` and `/sync/pull` request/response bodies in
// the shared OpenAPI contract (`backend/api/openapi.yaml` → paths `/sync/push`,
// `/sync/pull`), encoded/decoded with the shared `SideQuestCoding` coders so the
// JSON is byte-compatible with the Android client and the Go backend (Req 2.1,
// 2.2).
//
// The contract's sync payload carries `ActionItem`s (the syncable entity the
// sync endpoints exchange); `SyncMeta.deleted == true` is a tombstone, so a
// delete is just another change in `changes` (Req 6.3). `SyncMeta.dirty` is a
// client-only flag and is never serialized (see `SyncMeta`), so a pushed item
// never leaks the local pending-push state to the wire.

/// Request body for `POST /sync/push` (contract: object with `changes`,
/// `lastSyncToken`).
///
/// `changes` are the account's locally-dirty `ActionItem`s — creates, edits, and
/// tombstoned deletes alike. The server takes the account from the access token,
/// never the body, and merges with deterministic last-writer-wins. Because every
/// change is keyed on its client-generated `id`, re-sending the same payload (a
/// retry) is idempotent — the server dedupes by account + id, so retries never
/// create duplicates (Req 6.8).
public struct SyncPushRequest: Codable, Equatable {

    /// The locally-dirty changes to merge, keyed on their client-generated ids.
    public var changes: [ActionItem]

    /// The caller's last known sync token (the cursor from the previous pass),
    /// omitted from the JSON when `nil` on a first push.
    public var lastSyncToken: Int64?

    public init(changes: [ActionItem], lastSyncToken: Int64? = nil) {
        self.changes = changes
        self.lastSyncToken = lastSyncToken
    }
}

/// Response body for `POST /sync/push` (contract: object with `applied`,
/// `newSyncToken`).
public struct SyncPushResponse: Codable, Equatable {

    /// The number of changes the server applied.
    public var applied: Int

    /// The sync token to use as `since` on the next pull (advances the cursor).
    public var newSyncToken: Int64

    public init(applied: Int, newSyncToken: Int64) {
        self.applied = applied
        self.newSyncToken = newSyncToken
    }
}

/// Response body for `GET /sync/pull` (contract: object with `changes`,
/// `newSyncToken`).
///
/// `changes` are the account's records whose sync token is greater than the
/// requested `since` (a full set when `since` is omitted), **including
/// tombstones** so deletes propagate to this device (Req 6.3).
public struct SyncPullResponse: Codable, Equatable {

    /// Remote changes since the requested token, including tombstones.
    public var changes: [ActionItem]

    /// The sync token to persist as the new cursor for the next pull.
    public var newSyncToken: Int64

    public init(changes: [ActionItem], newSyncToken: Int64) {
        self.changes = changes
        self.newSyncToken = newSyncToken
    }
}

/// The result of a sync pass, returned by ``SyncService/push()`` and
/// ``SyncService/pull(since:)``.
///
/// A push reports how many changes were sent (``pushedCount``) and how many the
/// server applied (``appliedCount``); a pull reports how many remote changes
/// were received (``pulledCount``) and how many altered the local store after
/// last-writer-wins merge (``mergedCount``). ``newSyncToken`` is the advanced
/// cursor from whichever call produced it.
public struct SyncOutcome: Equatable {

    /// Changes submitted to `/sync/push` (0 for a pull).
    public var pushedCount: Int

    /// Changes the server reported as applied (0 for a pull).
    public var appliedCount: Int

    /// Remote changes received from `/sync/pull` (0 for a push).
    public var pulledCount: Int

    /// Local records changed by the merge — a remote write or tombstone that
    /// won last-writer-wins (0 for a push).
    public var mergedCount: Int

    /// The advanced sync-token cursor, when the call produced one.
    public var newSyncToken: Int64?

    public init(
        pushedCount: Int = 0,
        appliedCount: Int = 0,
        pulledCount: Int = 0,
        mergedCount: Int = 0,
        newSyncToken: Int64? = nil
    ) {
        self.pushedCount = pushedCount
        self.appliedCount = appliedCount
        self.pulledCount = pulledCount
        self.mergedCount = mergedCount
        self.newSyncToken = newSyncToken
    }
}
