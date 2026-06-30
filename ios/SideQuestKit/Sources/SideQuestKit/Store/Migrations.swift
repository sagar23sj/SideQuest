import Foundation
import GRDB

extension SideQuestDatabase {

    /// The schema migrator for the local store.
    ///
    /// Migrations are registered once, identified by name, and applied in order
    /// by `migrate(_:)`. New schema changes are added as additional migrations
    /// (never by editing an applied one) so existing on-device databases — and
    /// the database shared with the Share Extension — upgrade forward cleanly.
    ///
    /// `eraseDatabaseOnSchemaChange` is intentionally **off**: the store is the
    /// offline-first source of truth (Req 5.1), so the schema must evolve via
    /// real migrations rather than by wiping user data.
    static var migrator: DatabaseMigrator {
        var migrator = DatabaseMigrator()

        migrator.registerMigration("v1.createSchema") { db in
            // Account — the locally cached authenticated identity.
            try db.create(table: AccountRecord.databaseTableName) { t in
                t.primaryKey("id", .text)
                t.column("email", .text).notNull()
                t.column("displayName", .text).notNull()
                t.column("createdAt", .datetime).notNull()
            }

            // Bucket — grouping of action items, scoped to an account.
            try db.create(table: BucketRecord.databaseTableName) { t in
                t.primaryKey("id", .text)
                t.column("accountId", .text).notNull().indexed()
                t.column("name", .text).notNull()
                t.column("notStartedColor", .text).notNull()
                t.column("inProgressColor", .text).notNull()
                t.column("completedColor", .text).notNull()
                t.column("syncUpdatedAt", .datetime).notNull()
                t.column("syncVersion", .integer).notNull()
                t.column("syncDeleted", .boolean).notNull()
                t.column("syncDirty", .boolean).notNull()
            }

            // ActionItem — the core tracked task. `timeframe` is persisted as a
            // discriminator (`timeframeKind`) + payload (`timeframeDate`), and
            // `preview` as a JSON blob (see StoreCoding).
            try db.create(table: ActionItemRecord.databaseTableName) { t in
                t.primaryKey("id", .text)
                t.column("accountId", .text).notNull().indexed()
                t.column("bucketId", .text).notNull()
                    .references(BucketRecord.databaseTableName, onDelete: .cascade)
                    .indexed()
                t.column("title", .text).notNull()
                t.column("description", .text)
                t.column("contentType", .text).notNull()
                t.column("sourceContent", .text)
                t.column("preview", .blob)
                t.column("timeframeKind", .text).notNull()
                t.column("timeframeDate", .text)
                t.column("status", .text).notNull()
                t.column("createdAt", .datetime).notNull()
                t.column("syncUpdatedAt", .datetime).notNull()
                t.column("syncVersion", .integer).notNull()
                t.column("syncDeleted", .boolean).notNull()
                t.column("syncDirty", .boolean).notNull()
            }

            // ActionPlan — an ordered breakdown attached to one action item.
            try db.create(table: ActionPlanRecord.databaseTableName) { t in
                t.primaryKey("id", .text)
                t.column("actionItemId", .text).notNull()
                    .references(ActionItemRecord.databaseTableName, onDelete: .cascade)
                    .indexed()
                t.column("syncUpdatedAt", .datetime).notNull()
                t.column("syncVersion", .integer).notNull()
                t.column("syncDeleted", .boolean).notNull()
                t.column("syncDirty", .boolean).notNull()
            }

            // SubAction — one step of an action plan; `order` defines sequence.
            try db.create(table: SubActionRecord.databaseTableName) { t in
                t.primaryKey("id", .text)
                t.column("planId", .text).notNull()
                    .references(ActionPlanRecord.databaseTableName, onDelete: .cascade)
                    .indexed()
                t.column("text", .text).notNull()
                t.column("order", .integer).notNull()
                t.column("completed", .boolean).notNull()
            }

            // TaskReminder — local reminder config; an item may have several,
            // so rows use an auto-incremented primary key.
            try db.create(table: TaskReminderRecord.databaseTableName) { t in
                t.autoIncrementedPrimaryKey("id")
                t.column("actionItemId", .text).notNull()
                    .references(ActionItemRecord.databaseTableName, onDelete: .cascade)
                    .indexed()
                t.column("hour", .integer).notNull()
                t.column("minute", .integer).notNull()
                t.column("untilDate", .datetime).notNull()
                t.column("recurringDaily", .boolean).notNull()
            }

            // Thought — the built-in "thought of the day" set (Req 12.3).
            try db.create(table: ThoughtRecord.databaseTableName) { t in
                t.primaryKey("id", .integer)
                t.column("text", .text).notNull()
            }
        }

        return migrator
    }
}
