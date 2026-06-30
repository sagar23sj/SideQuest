import Foundation

/// A time of day in the device's local time zone (design: Data Models →
/// `TimeOfDay`). Used to anchor reminders and nudges to local wall-clock time
/// (Req 7.10).
public struct TimeOfDay: Codable, Equatable {

    /// Hour of day, `0...23`.
    public var hour: Int
    /// Minute of hour, `0...59`.
    public var minute: Int

    public init(hour: Int, minute: Int) {
        self.hour = hour
        self.minute = minute
    }
}

/// An optional reminder attached to an `ActionItem` (design: Data Models →
/// `TaskReminder`; Req 7.2–7.9).
///
/// The reminder fires at `timeOfDay` (local time). When `recurringDaily` is
/// `true` it fires every day up to and including `untilDate`; otherwise it
/// fires once. In both cases reminders stop once the item is completed
/// (enforced by the scheduling layer) and never fire after `untilDate`.
///
/// `TaskReminder` is a client/design model — it is not part of the OpenAPI
/// contract's request/response schemas — so its `untilDate` rides the standard
/// date-time coding strategy (`SideQuestCoding`) and round-trips with the same
/// coders used for the contract models.
public struct TaskReminder: Codable, Equatable {

    /// The `ActionItem` this reminder belongs to.
    public var actionItemId: String

    /// Hour + minute (local time zone) at which the reminder fires.
    public var timeOfDay: TimeOfDay

    /// The last calendar day (inclusive) the reminder may fire; constrained to
    /// `[today, today + 365 days]` by validation in a later task (Req 7.4).
    public var untilDate: Date

    /// Whether the reminder repeats daily until `untilDate` (Req 7.3).
    public var recurringDaily: Bool

    public init(
        actionItemId: String,
        timeOfDay: TimeOfDay,
        untilDate: Date,
        recurringDaily: Bool
    ) {
        self.actionItemId = actionItemId
        self.timeOfDay = timeOfDay
        self.untilDate = untilDate
        self.recurringDaily = recurringDaily
    }
}
