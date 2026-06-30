import Foundation
import GRDB

/// Repository for `ActionPlan`s over the local GRDB store (task 6.1).
///
/// An `ActionPlan` is an aggregate: a plan row plus its ordered `SubAction`
/// rows. Create/edit write the plan and replace its sub-actions atomically in a
/// single durable transaction (Req 5.3, 5.5); a `ValueObservation` stream
/// rebuilds the live plans (with sub-actions) reactively (Req 5.2). Every
/// mutation is marked `dirty` and stays dirty until a push is acknowledged
/// (Req 5.6), and a commit failure surfaces ``RepositoryError/notSaved(underlying:)``
/// while leaving the prior persisted state intact (Req 5.8).
///
/// Deletes are **tombstones** (`sync.deleted = true`, `dirty = true`) so the
/// deletion propagates on the next push (Req 6.3) and stays pending until
/// acknowledged (Req 5.6). The sub-action rows are retained while the plan is a
/// tombstone so the aggregate can still be pushed; the reactive stream filters
/// tombstoned plans out, so a deleted plan disappears from the UI immediately.
public final class ActionPlanRepository {

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
    /// `ActionPlan` or `SubAction` before handing it to ``create(_:)``.
    public func newIdentifier() -> String {
        identifiers.newIdentifier()
    }

    // MARK: - Reactive reads (Req 5.2, 5.3)

    /// A `ValueObservation` of all live (non-tombstoned) action plans, each with
    /// its sub-actions ordered ascending by `order`. Re-emits whenever a plan
    /// row or any of its sub-action rows change (the fetch reads both tables, so
    /// the observation tracks both).
    public func plansObservation() -> ValueObservation<ValueReducers.Fetch<[ActionPlan]>> {
        ValueObservation.tracking { db in
            try ActionPlanRecord
                .filter(Column("syncDeleted") == false)
                .fetchAll(db)
                .map { planRecord in
                    let subActions = try SubActionRecord
                        .filter(Column("planId") == planRecord.id)
                        .fetchAll(db)
                    return planRecord.toActionPlan(subActions: subActions)
                }
        }
    }

    /// A `ValueObservation` of the live plan for a single action item, or `nil`
    /// when none exists (an action item has at most one plan).
    public func planObservation(forItem actionItemId: String) -> ValueObservation<ValueReducers.Fetch<ActionPlan?>> {
        ValueObservation.tracking { db in
            guard let planRecord = try ActionPlanRecord
                .filter(Column("actionItemId") == actionItemId)
                .filter(Column("syncDeleted") == false)
                .fetchOne(db)
            else { return nil }
            let subActions = try SubActionRecord
                .filter(Column("planId") == planRecord.id)
                .fetchAll(db)
            return planRecord.toActionPlan(subActions: subActions)
        }
    }

    /// An async stream of the live plans, suitable for binding a SwiftUI view
    /// model to the store (Req 5.2). Backed by ``plansObservation()``.
    public func plansStream() -> AsyncValueObservation<[ActionPlan]> {
        plansObservation().values(in: database.dbPool)
    }

    /// The persisted live (non-tombstoned) plan for a single action item, read
    /// once, or `nil` when none exists (an action item has at most one plan).
    ///
    /// A one-shot counterpart to ``planObservation(forItem:)`` for callers (the
    /// action-plan editor, task 12.1) that seed an editable draft on appear and
    /// persist via ``create(_:)`` / ``update(_:)`` rather than re-rendering on
    /// every store change. Sub-actions are returned ordered ascending by
    /// `order`.
    public func plan(forItem actionItemId: String) throws -> ActionPlan? {
        try database.read { db in
            guard let planRecord = try ActionPlanRecord
                .filter(Column("actionItemId") == actionItemId)
                .filter(Column("syncDeleted") == false)
                .fetchOne(db)
            else { return nil }
            let subActions = try SubActionRecord
                .filter(Column("planId") == planRecord.id)
                .fetchAll(db)
            return planRecord.toActionPlan(subActions: subActions)
        }
    }

    /// All persisted live (non-tombstoned) plans, read once.
    public func fetchAll() throws -> [ActionPlan] {
        try database.read { db in
            try ActionPlanRecord
                .filter(Column("syncDeleted") == false)
                .fetchAll(db)
                .map { planRecord in
                    let subActions = try SubActionRecord
                        .filter(Column("planId") == planRecord.id)
                        .fetchAll(db)
                    return planRecord.toActionPlan(subActions: subActions)
                }
        }
    }

    // MARK: - Mutations (Req 5.3, 5.6, 5.8)

    /// Persists `plan` and its sub-actions as a new aggregate, stamping fresh
    /// `dirty` sync metadata (Req 5.6). The caller supplies the plan/sub-action
    /// ids (typically from ``newIdentifier()``) and `actionItemId` (which must
    /// reference an existing action item — foreign key); the provided `sync` is
    /// overwritten. Returns the stored plan. Throws
    /// ``RepositoryError/notSaved(underlying:)`` on commit failure, leaving the
    /// store unchanged (Req 5.8).
    @discardableResult
    public func create(_ plan: ActionPlan) throws -> ActionPlan {
        var stored = plan
        stored.sync = .created(now: now())
        try commit { try Self.writeAggregate(stored, in: $0) }
        return stored
    }

    /// Persists an edit to `plan` (including sub-action additions, removals, and
    /// reorders), bumping its version and re-marking it `dirty` (Req 5.6). The
    /// new version is derived from the currently persisted plan row inside the
    /// same transaction, and the plan's sub-action rows are replaced wholesale
    /// to match `plan.subActions`. Returns the stored plan. Throws
    /// ``RepositoryError/notSaved(underlying:)`` on commit failure, leaving the
    /// store unchanged (Req 5.8).
    @discardableResult
    public func update(_ plan: ActionPlan) throws -> ActionPlan {
        var stored = plan
        try commit { db in
            let current = try ActionPlanRecord.fetchOne(db, key: plan.id)
            stored.sync = .edited(fromVersion: current?.syncVersion ?? plan.sync.version, now: now())
            try Self.writeAggregate(stored, in: db)
        }
        return stored
    }

    /// Tombstones the action plan with `id` (Req 6.3): marks it deleted and
    /// `dirty` and bumps its version. No-op if the plan does not exist. Throws
    /// ``RepositoryError/notSaved(underlying:)`` on commit failure, leaving the
    /// store unchanged (Req 5.8).
    public func delete(id: String) throws {
        try commit { db in
            guard var record = try ActionPlanRecord.fetchOne(db, key: id) else { return }
            let tombstone = SyncMeta.deleted(fromVersion: record.syncVersion, now: now())
            record.syncUpdatedAt = tombstone.updatedAt
            record.syncVersion = tombstone.version
            record.syncDeleted = tombstone.deleted
            record.syncDirty = tombstone.dirty
            try record.update(db)
        }
    }

    /// Clears the `dirty` flag for the plan with `id` after a successful push
    /// acknowledgment for `version` (Req 5.6). The flag is cleared **only** when
    /// the acknowledged `version` matches the currently persisted version, so an
    /// edit made after the push was sent stays dirty (Property 5). No-op if the
    /// plan is absent or its version has moved on.
    public func acknowledgePush(id: String, version: Int64) throws {
        try commit { db in
            guard var record = try ActionPlanRecord.fetchOne(db, key: id),
                  record.syncVersion == version else { return }
            record.syncDirty = false
            try record.update(db)
        }
    }

    // MARK: - Helpers

    /// Upserts the plan row and replaces its sub-action rows so the persisted
    /// set matches `plan.subActions` exactly (handles removals and reorders),
    /// all within the caller's transaction.
    private static func writeAggregate(_ plan: ActionPlan, in db: Database) throws {
        try ActionPlanRecord(plan).save(db)
        try SubActionRecord
            .filter(Column("planId") == plan.id)
            .deleteAll(db)
        for subAction in plan.subActions {
            try SubActionRecord(subAction, planId: plan.id).insert(db)
        }
    }

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
