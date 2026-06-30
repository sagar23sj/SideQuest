import Foundation
import GRDB

// MARK: - Domain-level persistence helpers
//
// Minimal read/write helpers that map the public domain models onto the
// internal GRDB record types (`BucketRecord`, `ActionItemRecord`) and back.
// They wrap `dbPool.write` / `dbPool.read` so callers — the repositories added
// in task 6, and the persistence round-trip property test (Reused Property 31,
// Req 5.4) — can persist and read whole entities without touching GRDB types
// directly. Writes go through `SideQuestDatabase.write`, so each one commits
// durably before returning (Req 5.5).
//
// `save` performs an upsert keyed on the record's primary key, so it serves
// both first-time inserts and subsequent edits; the last write wins for a given
// id, which is exactly what an "edit" persists.

extension SideQuestDatabase {

    // MARK: Bucket

    /// Inserts or updates `bucket` (upsert by `id`), committing durably.
    public func saveBucket(_ bucket: Bucket) throws {
        try write { try BucketRecord(bucket).save($0) }
    }

    /// Deletes the bucket with `id`. Owned action items cascade-delete via the
    /// schema's foreign key (`onDelete: .cascade`).
    public func deleteBucket(id: String) throws {
        try write { _ = try BucketRecord.deleteOne($0, key: id) }
    }

    /// All persisted buckets as domain models.
    public func fetchAllBuckets() throws -> [Bucket] {
        try read { db in try BucketRecord.fetchAll(db).map { $0.toBucket() } }
    }

    // MARK: ActionItem

    /// Inserts or updates `item` (upsert by `id`), committing durably. The
    /// item's `bucketId` must reference an existing bucket (foreign key).
    public func saveActionItem(_ item: ActionItem) throws {
        try write { try ActionItemRecord(item).save($0) }
    }

    /// Deletes the action item with `id`.
    public func deleteActionItem(id: String) throws {
        try write { _ = try ActionItemRecord.deleteOne($0, key: id) }
    }

    /// All persisted action items as domain models.
    public func fetchAllActionItems() throws -> [ActionItem] {
        try read { db in try ActionItemRecord.fetchAll(db).map { try $0.toActionItem() } }
    }
}

// MARK: - ActionPlan persistence
//
// An `ActionPlan` is an aggregate: one `actionPlan` row plus its ordered
// `subAction` rows. The helpers below keep the two in sync within a single
// durable transaction, so a saved plan and its sub-actions are always written
// (or rolled back) atomically. They mirror the model-centric `saveBucket` /
// `saveActionItem` helpers above and are reused by `ActionPlanRepository`
// (task 6.1).

extension SideQuestDatabase {

    /// Inserts or updates `plan` and its sub-actions atomically (upsert by
    /// `id`), committing durably. The plan's existing sub-action rows are
    /// replaced wholesale by `plan.subActions`, so a removed sub-action is
    /// deleted and a reordered set persists its new `order` values. The plan's
    /// `actionItemId` must reference an existing action item (foreign key).
    public func saveActionPlan(_ plan: ActionPlan) throws {
        try write { db in
            try ActionPlanRecord(plan).save(db)
            // Replace the owned sub-action rows so the persisted set matches the
            // model exactly (handles removals and reorders).
            try SubActionRecord
                .filter(Column("planId") == plan.id)
                .deleteAll(db)
            for subAction in plan.subActions {
                try SubActionRecord(subAction, planId: plan.id).insert(db)
            }
        }
    }

    /// Deletes the action plan with `id`. Owned sub-actions cascade-delete via
    /// the schema's foreign key (`onDelete: .cascade`).
    public func deleteActionPlan(id: String) throws {
        try write { _ = try ActionPlanRecord.deleteOne($0, key: id) }
    }

    /// All persisted action plans as domain models, each with its sub-actions
    /// fetched and ordered ascending by `order`.
    public func fetchAllActionPlans() throws -> [ActionPlan] {
        try read { db in
            try ActionPlanRecord.fetchAll(db).map { planRecord in
                let subActions = try SubActionRecord
                    .filter(Column("planId") == planRecord.id)
                    .fetchAll(db)
                return planRecord.toActionPlan(subActions: subActions)
            }
        }
    }
}
