import Foundation

// MARK: - SyncStore (local-store seam for SyncService)
//
// `SyncService` (task 16.1) drives the merge/idempotency/retry logic against
// this small protocol rather than against GRDB directly, mirroring the
// `AuthTransport`/`HTTPTransport` seams elsewhere in the package. That keeps the
// pure sync logic — pending-change collection, last-writer-wins merge,
// push-acknowledgment, tombstone application — unit-testable on any host
// (including the Windows/Linux dev box) with an in-memory fake, while the
// GRDB-backed conformance (`ActionItemRepository`) is the only Apple-target
// SQLite code.
//
// The store side is deliberately scoped to the `ActionItem` entity the contract
// sync payload exchanges (`/sync/push`, `/sync/pull` carry `ActionItem`s).

/// The local-store operations ``SyncService`` needs to push and pull changes.
///
/// Implemented by ``ActionItemRepository`` over the GRDB store; fakes implement
/// it in tests.
public protocol SyncStore {

    /// All locally-dirty `ActionItem`s awaiting a push — creates, edits, **and
    /// tombstoned deletes** (so deletes propagate, Req 6.3). These are the
    /// `changes` submitted to `/sync/push`.
    func pendingPushItems() throws -> [ActionItem]

    /// The currently-persisted version of the item with `id`, **including a
    /// tombstone** if it has been deleted locally, or `nil` when absent. Used to
    /// resolve a pulled remote change against the local version (Req 6.2).
    func localItem(id: String) throws -> ActionItem?

    /// Writes a server-authoritative change (a pull result or the winner of a
    /// last-writer-wins merge) into the local store with its `dirty` flag
    /// cleared, since it is now reconciled with the server. A change whose
    /// `sync.deleted` is `true` is stored as a tombstone (Req 6.3).
    func applyRemoteChange(_ item: ActionItem) throws

    /// Clears the `dirty` flag for the item with `id` after a successful push
    /// acknowledgment for `version` (Req 5.6). Must be a no-op when the item is
    /// absent or its persisted version has moved past `version`, so an edit made
    /// after the push was sent stays dirty (Property 5).
    func acknowledgePush(id: String, version: Int64) throws

    /// Imports a full first-sign-in pull into the local store **atomically**:
    /// either every item in `items` is written or — if any single write fails —
    /// none is, leaving the store exactly as it was (Req 6.7, 6.10).
    ///
    /// This is the seam that makes ``SyncService/fullPullForFirstSignIn()``
    /// all-or-nothing: the service hands the entire pulled set to this one call
    /// rather than applying changes one at a time, so a mid-import failure can
    /// never leave a partial import behind (Property 7). Each item is stored with
    /// its `dirty` flag cleared (it is server-authoritative) and a
    /// `sync.deleted` item is stored as a tombstone (Req 6.3). Conformers MUST
    /// implement this as a single durable transaction that rolls back on any
    /// error. Throws when the import could not be committed in full, leaving the
    /// store unchanged (Req 5.8).
    func importAllAtomically(_ items: [ActionItem]) throws
}
