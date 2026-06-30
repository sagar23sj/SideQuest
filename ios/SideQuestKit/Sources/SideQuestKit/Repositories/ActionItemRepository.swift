import Foundation
import GRDB

/// Repository for `ActionItem`s over the local GRDB store (task 6.1).
///
/// Provides create/edit/delete that read and write the local store (Req 5.3),
/// exposes a `ValueObservation` stream of the live (non-tombstoned) items so
/// views update reactively (Req 5.2), marks every mutation `dirty` and keeps it
/// dirty until a push is acknowledged (Req 5.6), and surfaces a
/// ``RepositoryError/notSaved(underlying:)`` on commit failure while leaving the
/// prior persisted state intact (Req 5.8).
///
/// Deletes are **tombstones** (`sync.deleted = true`, `dirty = true`) rather
/// than hard deletes, so the deletion propagates on the next push (Req 6.3) and
/// stays pending until acknowledged (Req 5.6). The reactive stream filters
/// tombstones out, so a deleted item disappears from the UI immediately.
public final class ActionItemRepository {

    private let database: SideQuestDatabase
    private let identifiers: IdentifierGenerator
    private let now: RepositoryClock

    public init(
        database: SideQuestDatabase,
        identifiers: IdentifierGenerator = UUIDIdentifierGenerator(),
        now: @escaping RepositoryClock = Date.init
    ) {
        self.database = database
        self.identifiers = identifiers
        self.now = now
    }

    /// A fresh client-generated identifier (Req 5.7), for callers that build an
    /// `ActionItem` before handing it to ``create(_:)``.
    public func newIdentifier() -> String {
        identifiers.newIdentifier()
    }

    // MARK: - Reactive reads (Req 5.2, 5.3)

    /// A `ValueObservation` of all live (non-tombstoned) action items. Re-emits
    /// whenever the underlying rows change — including writes made by the Share
    /// Extension process to the shared store (capture flow, task 8).
    public func itemsObservation() -> ValueObservation<ValueReducers.Fetch<[ActionItem]>> {
        ValueObservation.tracking { db in
            try ActionItemRecord
                .filter(Column("syncDeleted") == false)
                .fetchAll(db)
                .map { try $0.toActionItem() }
        }
    }

    /// A `ValueObservation` of the live items in a single bucket, used by the
    /// board's per-bucket grouping (Req 8.1). Domain-level ordering is applied
    /// by `Domain.buildBoard`, so this returns the rows unsorted.
    public func itemsObservation(inBucket bucketId: String) -> ValueObservation<ValueReducers.Fetch<[ActionItem]>> {
        ValueObservation.tracking { db in
            try ActionItemRecord
                .filter(Column("bucketId") == bucketId)
                .filter(Column("syncDeleted") == false)
                .fetchAll(db)
                .map { try $0.toActionItem() }
        }
    }

    /// An async stream of the live items, suitable for binding a SwiftUI view
    /// model to the store (Req 5.2). Backed by ``itemsObservation()``.
    public func itemsStream() -> AsyncValueObservation<[ActionItem]> {
        itemsObservation().values(in: database.dbPool)
    }

    /// All persisted live (non-tombstoned) items, read once.
    public func fetchAll() throws -> [ActionItem] {
        try database.read { db in
            try ActionItemRecord
                .filter(Column("syncDeleted") == false)
                .fetchAll(db)
                .map { try $0.toActionItem() }
        }
    }

    // MARK: - Mutations (Req 5.3, 5.6, 5.8)

    /// Persists `item` as a new record, stamping fresh `dirty` sync metadata
    /// (Req 5.6). The caller supplies the `id` (typically from
    /// ``newIdentifier()``) and `bucketId` (which must reference an existing
    /// bucket — foreign key); the provided `sync` is overwritten. Returns the
    /// stored item. Throws ``RepositoryError/notSaved(underlying:)`` on commit
    /// failure, leaving the store unchanged (Req 5.8).
    @discardableResult
    public func create(_ item: ActionItem) throws -> ActionItem {
        var stored = item
        stored.sync = .created(now: now())
        try commit { try ActionItemRecord(stored).insert($0) }
        return stored
    }

    /// Persists an edit to `item`, bumping its version and re-marking it
    /// `dirty` (Req 5.6). The new version is derived from the currently
    /// persisted row inside the same transaction. Returns the stored item.
    /// Throws ``RepositoryError/notSaved(underlying:)`` on commit failure,
    /// leaving the store unchanged (Req 5.8).
    @discardableResult
    public func update(_ item: ActionItem) throws -> ActionItem {
        var stored = item
        try commit { db in
            let current = try ActionItemRecord.fetchOne(db, key: item.id)
            stored.sync = .edited(fromVersion: current?.syncVersion ?? item.sync.version, now: now())
            try ActionItemRecord(stored).save(db)
        }
        return stored
    }

    /// Tombstones the action item with `id` (Req 6.3): marks it deleted and
    /// `dirty` and bumps its version, so it disappears from the reactive stream
    /// but remains pending a push. No-op if the item does not exist. Throws
    /// ``RepositoryError/notSaved(underlying:)`` on commit failure, leaving the
    /// store unchanged (Req 5.8).
    public func delete(id: String) throws {
        try commit { db in
            guard var record = try ActionItemRecord.fetchOne(db, key: id) else { return }
            let tombstone = SyncMeta.deleted(fromVersion: record.syncVersion, now: now())
            record.syncUpdatedAt = tombstone.updatedAt
            record.syncVersion = tombstone.version
            record.syncDeleted = tombstone.deleted
            record.syncDirty = tombstone.dirty
            try record.update(db)
        }
    }

    /// Clears the `dirty` flag for the item with `id` after a successful push
    /// acknowledgment for `version` (Req 5.6). The flag is cleared **only** when
    /// the acknowledged `version` matches the currently persisted version, so an
    /// edit made after the push was sent stays dirty (Property 5). No-op if the
    /// item is absent or its version has moved on.
    public func acknowledgePush(id: String, version: Int64) throws {
        try commit { db in
            guard var record = try ActionItemRecord.fetchOne(db, key: id),
                  record.syncVersion == version else { return }
            record.syncDirty = false
            try record.update(db)
        }
    }

    // MARK: - Helpers

    /// Runs `body` in a durable write transaction, re-throwing any failure as
    /// ``RepositoryError/notSaved(underlying:)``. The transaction rolls back on
    /// throw, so the store keeps its prior persisted state (Req 5.8).
    private func commit(_ body: @escaping (Database) throws -> Void) throws {
        do {
            try database.write(body)
        } catch {
            throw RepositoryError.notSaved(underlying: error)
        }
    }
}

// MARK: - SyncStore conformance (task 16.1)
//
// `SyncService` drives `/sync/push` and `/sync/pull` against the ``SyncStore``
// seam; the GRDB-backed repository is its production conformer. The push side
// reads the locally-dirty items and clears `dirty` on acknowledgment
// (``acknowledgePush(id:version:)`` above); the pull side reads the local
// version of a record and writes server-authoritative changes (including
// tombstones) back into the store.

extension ActionItemRepository: SyncStore {

    /// All locally-dirty action items awaiting a push — creates, edits, **and
    /// tombstoned deletes** (`syncDeleted == true`), so deletes propagate as
    /// tombstones (Req 6.3). These become the `changes` of `/sync/push`.
    public func pendingPushItems() throws -> [ActionItem] {
        try database.read { db in
            try ActionItemRecord
                .filter(Column("syncDirty") == true)
                .fetchAll(db)
                .map { try $0.toActionItem() }
        }
    }

    /// The currently-persisted version of the item with `id`, **including a
    /// tombstone** when it has been deleted locally, or `nil` when absent. Used
    /// to resolve a pulled remote change against the local version (Req 6.2).
    public func localItem(id: String) throws -> ActionItem? {
        try database.read { db in
            try ActionItemRecord.fetchOne(db, key: id)?.toActionItem()
        }
    }

    /// Writes a server-authoritative change — a pulled record or the winner of a
    /// last-writer-wins merge — into the store with its `dirty` flag cleared,
    /// since it is now reconciled with the server. A change whose `sync.deleted`
    /// is `true` is persisted as a tombstone (Req 6.3). Upsert by `id`, so it is
    /// idempotent across pull retries. Throws
    /// ``RepositoryError/notSaved(underlying:)`` on commit failure, leaving the
    /// store unchanged (Req 5.8).
    public func applyRemoteChange(_ item: ActionItem) throws {
        var reconciled = item
        reconciled.sync.dirty = false
        try commit { try ActionItemRecord(reconciled).save($0) }
    }

    /// Imports a full first-sign-in pull into the store **atomically** in a
    /// single durable write transaction (Req 6.7, 6.10): every item is saved
    /// (dirty cleared, tombstones preserved) or, if any one save throws, the
    /// transaction rolls back and **nothing** is imported, leaving the store
    /// exactly as it was (Property 7, Req 5.8).
    ///
    /// `commit` already wraps `database.write` in one transaction that rolls
    /// back on throw, so iterating the saves inside the single `commit` body is
    /// what delivers the all-or-nothing guarantee — a failure on item *n* undoes
    /// items *0..<n* as well. The call is idempotent across pull retries because
    /// each record is upserted by `id`.
    public func importAllAtomically(_ items: [ActionItem]) throws {
        try commit { db in
            for item in items {
                var reconciled = item
                reconciled.sync.dirty = false
                try ActionItemRecord(reconciled).save(db)
            }
        }
    }
}
