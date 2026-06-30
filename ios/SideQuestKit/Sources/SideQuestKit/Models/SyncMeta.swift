import Foundation

/// Sync metadata embedded in every syncable entity (mirrors the contract's
/// `SyncMeta` schema), driving offline-first, last-writer-wins synchronization.
///
/// Wire format (`backend/api/openapi.yaml` → `SyncMeta`):
/// ```json
/// { "updatedAt": "2025-06-14T10:30:00Z", "version": 7, "deleted": false }
/// ```
///
/// `updatedAt` is server-authoritative after a push is acknowledged. `version`
/// increments per update for concurrency detection. `deleted` is the tombstone
/// flag (Req 6.3).
///
/// `dirty` is a **client-only** flag (design: Data Models — "client-only:
/// pending push") that is true while the entity has local changes awaiting a
/// push. It is intentionally excluded from `CodingKeys`, so it is never encoded
/// to or decoded from the wire — matching the contract, which omits it (and the
/// Go backend, whose `SyncMeta` has no `dirty` field). It defaults to `false`
/// when an entity is decoded from the network.
public struct SyncMeta: Codable, Equatable {

    /// Server-authoritative last-update timestamp (epoch instant). Encoded as an
    /// RFC 3339 date-time string per the contract.
    public var updatedAt: Date

    /// Monotonically increasing per-update counter for concurrency detection.
    public var version: Int64

    /// Tombstone flag: `true` once the entity has been deleted (Req 6.3).
    public var deleted: Bool

    /// Client-only: `true` while the entity has changes pending a push.
    /// Never serialized (see type docs).
    public var dirty: Bool

    public init(updatedAt: Date, version: Int64, deleted: Bool, dirty: Bool = false) {
        self.updatedAt = updatedAt
        self.version = version
        self.deleted = deleted
        self.dirty = dirty
    }

    /// Only the contract fields participate in coding; `dirty` is omitted (it
    /// has a default value, so synthesized `Codable` simply skips it).
    private enum CodingKeys: String, CodingKey {
        case updatedAt
        case version
        case deleted
    }
}
