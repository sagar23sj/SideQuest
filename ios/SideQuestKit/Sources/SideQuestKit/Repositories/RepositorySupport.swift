import Foundation

// MARK: - Repository support (task 6.1)
//
// The repository layer is the single entry point for data (design: "Client
// Architecture (MVVM + Repository)"). It reads/writes the local GRDB store,
// stamps sync metadata so every mutation is pending-sync until acknowledged
// (Req 5.6), and exposes `ValueObservation` streams so views update reactively
// (Req 5.2, 5.3). It holds **no UI dependencies** — it lives in the shared
// `SideQuestKit` module linked by both the main app and the Share Extension.
//
// This file holds the cross-repository pieces: the "not saved" error surfaced
// on commit failure (Req 5.8), an injectable clock for deterministic
// timestamps, and the pure sync-metadata stamping rules shared by every
// repository.

/// Error surfaced by a repository when a mutation fails to commit to the local
/// store (Req 5.8).
///
/// A GRDB write runs in a transaction that is rolled back when its body throws,
/// so a failed mutation leaves the store at its **prior persisted state** (no
/// partial write). The repository re-throws the underlying failure wrapped as
/// ``notSaved`` so the caller can keep the user's input and show a "not saved"
/// indication; the repository never discards the caller's input value, so the
/// input is retained for a retry.
public enum RepositoryError: Error, LocalizedError {

    /// A create/edit/delete could not be committed durably. The store is
    /// unchanged from before the attempt. Carries the underlying store error
    /// for logging/diagnostics.
    case notSaved(underlying: Error)

    /// User-facing "not saved" indication (Req 5.8). The view model maps this
    /// to its own localized copy; this default keeps the surface non-empty.
    public var errorDescription: String? {
        switch self {
        case .notSaved:
            return "The change wasn’t saved. Please try again."
        }
    }

    /// The wrapped store-level error, when present.
    public var underlyingError: Error? {
        switch self {
        case .notSaved(let underlying):
            return underlying
        }
    }
}

/// A source of "now". Injected into repositories so timestamps are
/// deterministic under test; production code uses the real wall clock.
public typealias RepositoryClock = () -> Date

extension SyncMeta {

    /// Sync metadata for a **newly created** entity: marked `dirty` (pending a
    /// push), not deleted, version `1`, stamped at `now` (Req 5.6).
    static func created(now: Date) -> SyncMeta {
        SyncMeta(updatedAt: now, version: 1, deleted: false, dirty: true)
    }

    /// Sync metadata for an **edit** of an entity whose currently persisted
    /// version is `currentVersion`: version is bumped, `dirty` is set, and the
    /// tombstone flag is cleared (an edit revives a record), stamped at `now`
    /// (Req 5.6).
    static func edited(fromVersion currentVersion: Int64, now: Date) -> SyncMeta {
        SyncMeta(updatedAt: now, version: currentVersion + 1, deleted: false, dirty: true)
    }

    /// Sync metadata for a **delete**, expressed as a tombstone so the deletion
    /// can propagate to other devices on the next push (Req 6.3) and stays
    /// pending until acknowledged (Req 5.6): `deleted` and `dirty` are set and
    /// the version is bumped from `currentVersion`, stamped at `now`.
    static func deleted(fromVersion currentVersion: Int64, now: Date) -> SyncMeta {
        SyncMeta(updatedAt: now, version: currentVersion + 1, deleted: true, dirty: true)
    }
}
