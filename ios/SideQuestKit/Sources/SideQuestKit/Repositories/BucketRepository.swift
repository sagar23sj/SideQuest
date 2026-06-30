import Foundation
import GRDB

/// Repository for `Bucket`s over the local GRDB store (task 6.1).
///
/// Provides create/edit/delete that read and write the local store (Req 5.3),
/// exposes a `ValueObservation` stream of the live (non-tombstoned) buckets so
/// views update reactively (Req 5.2), marks every mutation `dirty` and keeps it
/// dirty until a push is acknowledged (Req 5.6), and surfaces a
/// ``RepositoryError/notSaved(underlying:)`` on commit failure while leaving the
/// prior persisted state intact (Req 5.8).
///
/// Deletes are **tombstones** (`sync.deleted = true`, `dirty = true`) rather
/// than hard deletes, so the deletion can propagate to other devices on the
/// next push (Req 6.3) and stays pending until acknowledged (Req 5.6). The
/// reactive stream filters tombstones out, so a deleted bucket disappears from
/// the UI immediately. (The reassign-or-delete decision for a non-empty bucket
/// is the separate bucket-management flow in task 7.1.)
public final class BucketRepository {

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

    /// A fresh client-generated identifier (Req 5.7), for callers that build a
    /// `Bucket` before handing it to ``create(_:)``.
    public func newIdentifier() -> String {
        identifiers.newIdentifier()
    }

    // MARK: - Reactive reads (Req 5.2, 5.3)

    /// A `ValueObservation` of all live (non-tombstoned) buckets. Re-emits
    /// whenever the underlying rows change — including writes made by the Share
    /// Extension process to the shared store.
    public func bucketsObservation() -> ValueObservation<ValueReducers.Fetch<[Bucket]>> {
        ValueObservation.tracking { db in
            try BucketRecord
                .filter(Column("syncDeleted") == false)
                .fetchAll(db)
                .map { $0.toBucket() }
        }
    }

    /// An async stream of the live buckets, suitable for binding a SwiftUI view
    /// model to the store (Req 5.2). Backed by ``bucketsObservation()``.
    public func bucketsStream() -> AsyncValueObservation<[Bucket]> {
        bucketsObservation().values(in: database.dbPool)
    }

    /// All persisted live (non-tombstoned) buckets, read once.
    public func fetchAll() throws -> [Bucket] {
        try database.read { db in
            try BucketRecord
                .filter(Column("syncDeleted") == false)
                .fetchAll(db)
                .map { $0.toBucket() }
        }
    }

    // MARK: - Mutations (Req 5.3, 5.6, 5.8)

    /// Persists `bucket` as a new record, stamping fresh `dirty` sync metadata
    /// (Req 5.6). The caller supplies the `bucketId` (typically from
    /// ``newIdentifier()``); the provided `sync` is overwritten. Returns the
    /// stored bucket. Throws ``RepositoryError/notSaved(underlying:)`` on commit
    /// failure, leaving the store unchanged (Req 5.8).
    @discardableResult
    public func create(_ bucket: Bucket) throws -> Bucket {
        var stored = bucket
        stored.sync = .created(now: now())
        try commit { try BucketRecord(stored).insert($0) }
        return stored
    }

    /// Persists an edit to `bucket`, bumping its version and re-marking it
    /// `dirty` (Req 5.6). The new version is derived from the currently
    /// persisted row inside the same transaction, so concurrent edits can't
    /// lose a version bump. Returns the stored bucket. Throws
    /// ``RepositoryError/notSaved(underlying:)`` on commit failure, leaving the
    /// store unchanged (Req 5.8).
    @discardableResult
    public func update(_ bucket: Bucket) throws -> Bucket {
        var stored = bucket
        try commit { db in
            let current = try BucketRecord.fetchOne(db, key: bucket.id)
            stored.sync = .edited(fromVersion: current?.syncVersion ?? bucket.sync.version, now: now())
            try BucketRecord(stored).save(db)
        }
        return stored
    }

    /// Tombstones the bucket with `id` (Req 6.3): marks it deleted and `dirty`
    /// and bumps its version, so it disappears from the reactive stream but
    /// remains pending a push. No-op if the bucket does not exist. Throws
    /// ``RepositoryError/notSaved(underlying:)`` on commit failure, leaving the
    /// store unchanged (Req 5.8).
    public func delete(id: String) throws {
        try commit { db in
            guard var record = try BucketRecord.fetchOne(db, key: id) else { return }
            let tombstone = SyncMeta.deleted(fromVersion: record.syncVersion, now: now())
            record.syncUpdatedAt = tombstone.updatedAt
            record.syncVersion = tombstone.version
            record.syncDeleted = tombstone.deleted
            record.syncDirty = tombstone.dirty
            try record.update(db)
        }
    }

    /// Clears the `dirty` flag for the bucket with `id` after a successful push
    /// acknowledgment for `version` (Req 5.6). The flag is cleared **only** when
    /// the acknowledged `version` matches the currently persisted version, so an
    /// edit made after the push was sent stays dirty (Property 5). No-op if the
    /// bucket is absent or its version has moved on.
    public func acknowledgePush(id: String, version: Int64) throws {
        try commit { db in
            guard var record = try BucketRecord.fetchOne(db, key: id),
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
