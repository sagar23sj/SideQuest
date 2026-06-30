import Foundation

/// An ordered breakdown of an `ActionItem` into smaller steps
/// (contract: `backend/api/openapi.yaml` → `ActionPlan`).
///
/// `subActions` is an ordered list of 1...100 steps (Req 9.8). Progress and the
/// "mark complete" prompt (Req 9.9, 9.10) are derived by the portable domain
/// logic in a later task.
public struct ActionPlan: Codable, Identifiable, Equatable {

    public var id: String
    public var actionItemId: String
    public var subActions: [SubAction]
    public var sync: SyncMeta

    public init(
        id: String,
        actionItemId: String,
        subActions: [SubAction],
        sync: SyncMeta
    ) {
        self.id = id
        self.actionItemId = actionItemId
        self.subActions = subActions
        self.sync = sync
    }
}

/// A single step within an `ActionPlan`
/// (contract: `backend/api/openapi.yaml` → `SubAction`).
///
/// `order` defines its position in the (contiguous) sequence; `completed`
/// tracks whether the step is done.
public struct SubAction: Codable, Identifiable, Equatable {

    public var id: String
    public var text: String
    public var order: Int
    public var completed: Bool

    public init(id: String, text: String, order: Int, completed: Bool) {
        self.id = id
        self.text = text
        self.order = order
        self.completed = completed
    }
}
