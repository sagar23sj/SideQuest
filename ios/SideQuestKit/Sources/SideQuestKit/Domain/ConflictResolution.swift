import Foundation

// MARK: - Sync conflict resolution (Req 6.2)
//
// Pure, portable last-writer-wins conflict resolution. Mirrors the Android
// client's `com.sidequest.domain.sync.ConflictResolution` (and the Go backend's
// `ResolveConflict`) so the iOS Swift implementation produces field-by-field
// identical results (Req 3.2, 3.3; reused sibling Property 32 — "conflict
// resolution is deterministic last-writer-wins"; cross-implementation
// equivalence validated by task 4.19; the dedicated property test is task 4.18).
//
// When two devices edit the same record offline, a sync round trip can surface
// two concurrent versions of one entity. Resolution is keyed on
// `SyncMeta.updatedAt`: the version with the greater `updatedAt` wins. Ties are
// broken deterministically so the merge is also order-independent (commutative)
// and repeatable:
//
//   1. greater `SyncMeta.updatedAt`           (primary: last-writer-wins)
//   2. greater `SyncMeta.version`             (tie on the same instant)
//   3. greater record `id`                    (Req 6.2: tie-break by identifier)
//   4. greater canonical (full-value) form    (final strict total order)
//
// Step 3 satisfies Req 6.2 ("WHERE two conflicting records share the same update
// time THE Sync_Service SHALL break the tie deterministically by record
// identifier"). Conflict resolution only ever compares two *versions of the same
// record*, which share an `id`, so in practice step 3 is a no-op and step 4
// supplies the final total order — exactly as the Android client falls straight
// from `version` to its canonical `toString()` comparison. Keeping the id step
// explicit honors the requirement's wording without changing same-record
// results, so the two implementations stay behaviorally identical.

/// A value that can participate in last-writer-wins conflict resolution: it
/// carries a stable record `id` and ``SyncMeta`` (the `updatedAt`/`version`
/// keys), plus a deterministic ``canonicalForm`` used only as the final
/// tie-breaker. `ActionItem`, `Bucket`, and `ActionPlan` all conform, so the
/// same resolver applies to every syncable entity.
public protocol SyncResolvable: Equatable {

    /// The client-generated record identifier. Two versions of the same record
    /// share this value (Req 6.2).
    var id: String { get }

    /// The sync metadata whose `updatedAt` and `version` drive resolution.
    var sync: SyncMeta { get }

    /// A deterministic, value-dependent string form used solely as the final
    /// tie-breaker when `updatedAt`, `version`, and `id` are all equal. It must
    /// be stable for equal values and differ for values that differ in any
    /// resolved field.
    var canonicalForm: String { get }
}

extension SyncResolvable where Self: Encodable {

    /// Default ``canonicalForm`` derived from the entity's contract JSON with
    /// sorted keys, giving a stable, value-dependent ordering without bespoke
    /// per-type code. This is analogous to the Kotlin data class `toString()`
    /// the Android client uses for the same final tie-break. The encoding only
    /// affects the rare full-tie path, so its exact text is an implementation
    /// detail; determinism (not cross-language byte-equality) is what matters.
    public var canonicalForm: String {
        let encoder = SideQuestCoding.makeEncoder()
        encoder.outputFormatting = [.sortedKeys]
        guard let data = try? encoder.encode(self),
              let string = String(data: data, encoding: .utf8) else {
            // Encoding never fails for these models; fall back to the id so the
            // function stays total even on a hypothetical failure.
            return id
        }
        return string
    }
}

extension Domain {

    /// The outcome of resolving two concurrent versions of a record: the
    /// ``winner`` to keep and the ``loser`` preserved separately (for example to
    /// write to a conflict log) without affecting the winning state. Mirrors the
    /// Android `ConflictResolution.Conflict`.
    public struct Conflict<T: SyncResolvable>: Equatable {

        /// The version that should be kept (the last writer).
        public let winner: T

        /// The version that lost resolution, retained for auditing.
        public let loser: T

        public init(winner: T, loser: T) {
            self.winner = winner
            self.loser = loser
        }
    }

    /// Resolves two concurrent versions of the same record via deterministic
    /// last-writer-wins (Req 6.2, reused sibling Property 32). Generic over any
    /// ``SyncResolvable``, so it applies uniformly to `ActionItem`, `Bucket`,
    /// and `ActionPlan`.
    ///
    /// The two versions are expected to share an `id`. The winner is the version
    /// with the greater ``SyncMeta/updatedAt``; ties are broken in order by
    /// greater ``SyncMeta/version``, then greater `id` (Req 6.2), then greater
    /// ``SyncResolvable/canonicalForm`` — a strict total order that makes the
    /// result independent of argument order (commutative) and repeatable
    /// (deterministic).
    ///
    /// This function is pure and total: it never mutates its inputs and never
    /// throws for any input. When the two versions are fully equal, `a` is
    /// reported as the winner, but it is then indistinguishable from the loser
    /// so argument order is irrelevant.
    public static func resolveConflict<T: SyncResolvable>(_ a: T, _ b: T) -> Conflict<T> {
        firstWins(a, b)
            ? Conflict(winner: a, loser: b)
            : Conflict(winner: b, loser: a)
    }

    /// Resolves two concurrent versions of the same ``ActionItem`` via
    /// deterministic last-writer-wins. Named convenience over
    /// ``resolveConflict(_:_:)`` mirroring the Android `resolveActionItem`.
    public static func resolveActionItem(_ a: ActionItem, _ b: ActionItem) -> Conflict<ActionItem> {
        resolveConflict(a, b)
    }

    /// Resolves two concurrent versions of the same ``Bucket`` via deterministic
    /// last-writer-wins.
    public static func resolveBucket(_ a: Bucket, _ b: Bucket) -> Conflict<Bucket> {
        resolveConflict(a, b)
    }

    /// Resolves two concurrent versions of the same ``ActionPlan`` via
    /// deterministic last-writer-wins.
    public static func resolveActionPlan(_ a: ActionPlan, _ b: ActionPlan) -> Conflict<ActionPlan> {
        resolveConflict(a, b)
    }

    // MARK: - Helpers

    /// Reports whether version `a` beats (or ties) version `b` under the
    /// last-writer-wins order: ``SyncMeta/updatedAt`` is the primary key,
    /// ``SyncMeta/version`` breaks an `updatedAt` tie, the record `id` breaks a
    /// `version` tie (Req 6.2), and ``SyncResolvable/canonicalForm`` is the final
    /// total-order tie-breaker.
    ///
    /// The `canonicalForm` comparison uses `>=` so two fully equal values return
    /// `true`; the winner is then indistinguishable from the loser, keeping the
    /// result well-defined and order-independent. For any pair that differs in a
    /// resolved field, exactly one of `firstWins(a, b)` and `firstWins(b, a)` is
    /// `true`, which is what makes resolution commutative.
    private static func firstWins<T: SyncResolvable>(_ a: T, _ b: T) -> Bool {
        if a.sync.updatedAt != b.sync.updatedAt {
            return a.sync.updatedAt > b.sync.updatedAt
        }
        if a.sync.version != b.sync.version {
            return a.sync.version > b.sync.version
        }
        if a.id != b.id {
            return a.id > b.id
        }
        return a.canonicalForm >= b.canonicalForm
    }
}

// MARK: - SyncResolvable conformances
//
// Each syncable entity already exposes `id` and `sync` and is `Encodable`, so it
// adopts ``SyncResolvable`` directly and inherits the default ``canonicalForm``.

extension ActionItem: SyncResolvable {}
extension Bucket: SyncResolvable {}
extension ActionPlan: SyncResolvable {}
