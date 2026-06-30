import Foundation

/// A user-defined grouping of `ActionItem`s
/// (contract: `backend/api/openapi.yaml` → `Bucket`).
///
/// `name` is unique per account after normalization (trim + case-insensitive),
/// enforced by bucket validation in a later task (Req 9.2). Status indicator
/// colors are stored per bucket so the board can render an item's color from
/// its current `ActionStatus`, with distinct colors per status (Req 8.2).
public struct Bucket: Codable, Identifiable, Equatable {

    public var id: String
    public var accountId: String
    public var name: String

    /// Color shown for `ActionStatus.notStarted` items in this bucket.
    public var notStartedColor: String
    /// Color shown for `ActionStatus.inProgress` items in this bucket.
    public var inProgressColor: String
    /// Color shown for `ActionStatus.completed` items in this bucket.
    public var completedColor: String

    public var sync: SyncMeta

    public init(
        id: String,
        accountId: String,
        name: String,
        notStartedColor: String,
        inProgressColor: String,
        completedColor: String,
        sync: SyncMeta
    ) {
        self.id = id
        self.accountId = accountId
        self.name = name
        self.notStartedColor = notStartedColor
        self.inProgressColor = inProgressColor
        self.completedColor = completedColor
        self.sync = sync
    }
}
