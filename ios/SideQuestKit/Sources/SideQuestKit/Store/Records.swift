import Foundation
import GRDB

// MARK: - GRDB record types
//
// The wire models in `Models/` are kept clean for the OpenAPI JSON contract, so
// the store maps them onto flat GRDB rows here rather than annotating the
// models with persistence concerns. Each record is a flat `Codable` struct so
// GRDB derives the column mapping automatically (FetchableRecord +
// PersistableRecord), and each carries conversions to/from its domain model.
//
// Embedded `SyncMeta` is flattened into `sync*` columns. `Timeframe` becomes a
// discriminator (`timeframeKind`) + payload (`timeframeDate`) pair, and
// `LinkPreview` becomes a JSON blob (`preview`) — both via `StoreCoding`.

// MARK: Account

struct AccountRecord: Codable, FetchableRecord, PersistableRecord, Equatable {
    static let databaseTableName = "account"

    var id: String
    var email: String
    var displayName: String
    var createdAt: Date

    init(_ model: Account) {
        id = model.id
        email = model.email
        displayName = model.displayName
        createdAt = model.createdAt
    }

    func toAccount() -> Account {
        Account(id: id, email: email, displayName: displayName, createdAt: createdAt)
    }
}

// MARK: Bucket

struct BucketRecord: Codable, FetchableRecord, PersistableRecord, Equatable {
    static let databaseTableName = "bucket"

    var id: String
    var accountId: String
    var name: String
    var notStartedColor: String
    var inProgressColor: String
    var completedColor: String
    var syncUpdatedAt: Date
    var syncVersion: Int64
    var syncDeleted: Bool
    var syncDirty: Bool

    init(_ model: Bucket) {
        id = model.id
        accountId = model.accountId
        name = model.name
        notStartedColor = model.notStartedColor
        inProgressColor = model.inProgressColor
        completedColor = model.completedColor
        syncUpdatedAt = model.sync.updatedAt
        syncVersion = model.sync.version
        syncDeleted = model.sync.deleted
        syncDirty = model.sync.dirty
    }

    func toBucket() -> Bucket {
        Bucket(
            id: id,
            accountId: accountId,
            name: name,
            notStartedColor: notStartedColor,
            inProgressColor: inProgressColor,
            completedColor: completedColor,
            sync: SyncMeta(
                updatedAt: syncUpdatedAt,
                version: syncVersion,
                deleted: syncDeleted,
                dirty: syncDirty
            )
        )
    }
}

// MARK: ActionItem

struct ActionItemRecord: Codable, FetchableRecord, PersistableRecord, Equatable {
    static let databaseTableName = "actionItem"

    var id: String
    var accountId: String
    var bucketId: String
    var title: String
    var description: String?
    var contentType: String
    var sourceContent: String?
    var preview: Data?
    var timeframeKind: String
    var timeframeDate: String?
    var status: String
    var createdAt: Date
    var syncUpdatedAt: Date
    var syncVersion: Int64
    var syncDeleted: Bool
    var syncDirty: Bool

    init(_ model: ActionItem) throws {
        id = model.id
        accountId = model.accountId
        bucketId = model.bucketId
        title = model.title
        description = model.description
        contentType = model.contentType.rawValue
        sourceContent = model.sourceContent
        preview = try StoreCoding.encodePreview(model.preview)
        timeframeKind = StoreCoding.discriminator(for: model.timeframe)
        timeframeDate = StoreCoding.datePayload(for: model.timeframe)
        status = model.status.rawValue
        createdAt = model.createdAt
        syncUpdatedAt = model.sync.updatedAt
        syncVersion = model.sync.version
        syncDeleted = model.sync.deleted
        syncDirty = model.sync.dirty
    }

    func toActionItem() throws -> ActionItem {
        guard let contentType = ContentType(rawValue: contentType) else {
            throw SideQuestStoreError.invalidContentType(contentType)
        }
        guard let status = ActionStatus(rawValue: status) else {
            throw SideQuestStoreError.invalidStatus(status)
        }
        let timeframe = try StoreCoding.timeframe(
            discriminator: timeframeKind,
            datePayload: timeframeDate
        )
        let preview = try StoreCoding.decodePreview(preview)
        return ActionItem(
            id: id,
            accountId: accountId,
            bucketId: bucketId,
            title: title,
            description: description,
            contentType: contentType,
            sourceContent: sourceContent,
            preview: preview,
            timeframe: timeframe,
            status: status,
            createdAt: createdAt,
            sync: SyncMeta(
                updatedAt: syncUpdatedAt,
                version: syncVersion,
                deleted: syncDeleted,
                dirty: syncDirty
            )
        )
    }
}

// MARK: ActionPlan + SubAction

struct ActionPlanRecord: Codable, FetchableRecord, PersistableRecord, Equatable {
    static let databaseTableName = "actionPlan"

    var id: String
    var actionItemId: String
    var syncUpdatedAt: Date
    var syncVersion: Int64
    var syncDeleted: Bool
    var syncDirty: Bool

    init(_ model: ActionPlan) {
        id = model.id
        actionItemId = model.actionItemId
        syncUpdatedAt = model.sync.updatedAt
        syncVersion = model.sync.version
        syncDeleted = model.sync.deleted
        syncDirty = model.sync.dirty
    }

    /// Rebuilds the aggregate from this row plus its (separately fetched,
    /// order-sorted) sub-action rows.
    func toActionPlan(subActions: [SubActionRecord]) -> ActionPlan {
        ActionPlan(
            id: id,
            actionItemId: actionItemId,
            subActions: subActions
                .sorted { $0.order < $1.order }
                .map { $0.toSubAction() },
            sync: SyncMeta(
                updatedAt: syncUpdatedAt,
                version: syncVersion,
                deleted: syncDeleted,
                dirty: syncDirty
            )
        )
    }
}

struct SubActionRecord: Codable, FetchableRecord, PersistableRecord, Equatable {
    static let databaseTableName = "subAction"

    var id: String
    /// Foreign key to the owning `ActionPlan`. Not part of the `SubAction`
    /// model — the relationship is implicit in the model's array membership.
    var planId: String
    var text: String
    var order: Int
    var completed: Bool

    init(_ model: SubAction, planId: String) {
        id = model.id
        self.planId = planId
        text = model.text
        order = model.order
        completed = model.completed
    }

    func toSubAction() -> SubAction {
        SubAction(id: id, text: text, order: order, completed: completed)
    }
}

// MARK: TaskReminder

struct TaskReminderRecord: Codable, FetchableRecord, MutablePersistableRecord, Equatable {
    static let databaseTableName = "taskReminder"

    /// `TaskReminder` has no natural identifier and an item may carry several
    /// reminders, so rows use an auto-incremented row id captured on insert.
    var id: Int64?
    var actionItemId: String
    var hour: Int
    var minute: Int
    var untilDate: Date
    var recurringDaily: Bool

    init(_ model: TaskReminder, id: Int64? = nil) {
        self.id = id
        actionItemId = model.actionItemId
        hour = model.timeOfDay.hour
        minute = model.timeOfDay.minute
        untilDate = model.untilDate
        recurringDaily = model.recurringDaily
    }

    mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }

    func toTaskReminder() -> TaskReminder {
        TaskReminder(
            actionItemId: actionItemId,
            timeOfDay: TimeOfDay(hour: hour, minute: minute),
            untilDate: untilDate,
            recurringDaily: recurringDaily
        )
    }
}

// MARK: Thought

struct ThoughtRecord: Codable, FetchableRecord, PersistableRecord, Equatable {
    static let databaseTableName = "thought"

    var id: Int
    var text: String

    init(_ model: Thought) {
        id = model.id
        text = model.text
    }

    func toThought() -> Thought {
        Thought(id: id, text: text)
    }
}
