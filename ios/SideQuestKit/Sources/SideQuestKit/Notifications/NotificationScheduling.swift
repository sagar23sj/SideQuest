import Foundation

// MARK: - Notification scheduling primitives (pure, portable) — Req 7.10, 7.11
//
// Pure, I/O-free scheduling-computation logic shared by both the main app and
// the Share Extension. Keeping the `DateComponents` derivation here (rather than
// inside the iOS-only `UserNotifications` layer) means it can be unit/property
// tested on any host — important because the platform `UNUserNotificationCenter`
// is iOS-only and cannot be exercised on the build host.
//
// The single behavioural guarantee these functions provide is **local
// wall-clock anchoring** (Req 7.10): the produced `DateComponents` carry only
// calendar fields (hour/minute, optionally year/month/day) and deliberately set
// **no `timeZone`**. When fed to a `UNCalendarNotificationTrigger`, the system
// evaluates such components in the device's *current* local time zone, so a
// reminder fires at the intended wall-clock time even after the device's time
// zone changes — and, because the trigger is registered with the system, it
// also survives a reboot (Req 7.11). This is the property validated by task
// 13.2 (Property 12 — "Scheduled notifications are anchored to local wall-clock
// time").
//
// The day-set derivation that turns a `TaskReminder` into a series of
// occurrences (one-shot vs daily-recurring up to the until-date) is task 13.4;
// it is expected to build on these primitives.

/// Pure derivation of the calendar `DateComponents` used to anchor a scheduled
/// notification to the device's local wall-clock time (Req 7.10).
///
/// Every function here is total and deterministic and performs no I/O. None of
/// the returned components set a `timeZone`, which is what makes the resulting
/// `UNCalendarNotificationTrigger` track the device's *current* local time zone.
public enum NotificationScheduling {

    /// Components for a **daily-recurring** notification at the given local time
    /// of day.
    ///
    /// Only `hour` and `minute` are set, so a `UNCalendarNotificationTrigger`
    /// created with `repeats: true` fires once per day at that local wall-clock
    /// time (used by the global daily notification, Req 7.15, and the recurring
    /// evening nudge, Req 7.13). No date and no time zone are set.
    ///
    /// - Parameter time: Hour + minute in the device's local time zone.
    /// - Returns: `DateComponents` containing exactly `hour` and `minute`.
    public static func dailyComponents(at time: TimeOfDay) -> DateComponents {
        var components = DateComponents()
        components.hour = time.hour
        components.minute = time.minute
        return components
    }

    /// Components for a **one-shot** notification on a specific calendar day at
    /// the given local time of day.
    ///
    /// The year/month/day are read from `date` using `calendar` (which carries
    /// the time zone used to resolve the calendar day), then `hour`/`minute` are
    /// applied. The result contains exactly `year`, `month`, `day`, `hour`, and
    /// `minute` — and crucially **no `timeZone`** — so a
    /// `UNCalendarNotificationTrigger` created with `repeats: false` fires once,
    /// at that wall-clock time, in whatever local time zone the device is in
    /// when the time arrives (Req 7.10).
    ///
    /// Used by one-shot task reminders and as the building block for the
    /// recurring day-set (task 13.4).
    ///
    /// - Parameters:
    ///   - time: Hour + minute in the device's local time zone.
    ///   - date: An instant on the target calendar day.
    ///   - calendar: Calendar (including time zone) used to resolve the calendar
    ///     day of `date`. Injected (default `Calendar.current`) so the function
    ///     is deterministic and testable.
    /// - Returns: `DateComponents` containing exactly `year`, `month`, `day`,
    ///   `hour`, and `minute`.
    public static func components(
        at time: TimeOfDay,
        onDayOf date: Date,
        calendar: Calendar = .current
    ) -> DateComponents {
        let day = calendar.dateComponents([.year, .month, .day], from: date)
        // Build a fresh value so only the intended fields are present — no stray
        // `calendar`/`timeZone` leaks in to defeat local wall-clock anchoring.
        var components = DateComponents()
        components.year = day.year
        components.month = day.month
        components.day = day.day
        components.hour = time.hour
        components.minute = time.minute
        return components
    }
}

/// Stable, collision-resistant identifiers for the notification requests the app
/// schedules with the system.
///
/// Identifiers are the only handle the app has on already-scheduled requests, so
/// they encode enough structure to (a) cancel every reminder belonging to one
/// `ActionItem` (Req 7.8 — `cancelReminders(for:)`) and (b) replace the single
/// evening-nudge / global-daily request idempotently. Task-reminder identifiers
/// are namespaced by the owning item id so `isTaskReminder(_:forItem:)` can
/// match the whole family for cancellation; the per-occurrence suffix is filled
/// in by the occurrence-scheduling task (13.4).
public enum NotificationIdentifier {

    /// Namespace prefix shared by every task-reminder request, before the item id.
    public static let taskReminderNamespace = "sidequest.task-reminder."

    /// Identifier of the single global daily self-reminder (Req 7.15).
    public static let globalDaily = "sidequest.global-daily"

    /// Identifier of the single collective evening nudge (Req 7.13).
    public static let eveningNudge = "sidequest.evening-nudge"

    /// The identifier prefix shared by all reminder requests for one item.
    ///
    /// Every task-reminder request for `itemId` begins with this string, so
    /// cancelling reminders for an item is a prefix match over the pending
    /// requests (Req 7.8).
    public static func taskReminderPrefix(forItem itemId: String) -> String {
        "\(taskReminderNamespace)\(itemId)."
    }

    /// Builds the identifier for a single task-reminder occurrence of `itemId`.
    ///
    /// - Parameters:
    ///   - itemId: The owning `ActionItem` id.
    ///   - occurrence: A per-occurrence discriminator (for example a day key),
    ///     supplied by the occurrence-scheduling task (13.4).
    public static func taskReminder(itemId: String, occurrence: String) -> String {
        "\(taskReminderPrefix(forItem: itemId))\(occurrence)"
    }

    /// Reports whether `identifier` belongs to the reminder family of `itemId`.
    /// Used to select the pending requests to remove on cancellation (Req 7.8).
    public static func isTaskReminder(_ identifier: String, forItem itemId: String) -> Bool {
        identifier.hasPrefix(taskReminderPrefix(forItem: itemId))
    }
}
