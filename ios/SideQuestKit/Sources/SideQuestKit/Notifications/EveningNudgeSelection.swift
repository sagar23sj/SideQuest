import Foundation

// MARK: - Collective_Evening_Nudge eligible-item selection (pure, portable) — Req 7.13, 7.14
//
// Pure, I/O-free derivation of the Action_Items a single daily evening nudge
// summarizes. It is deliberately separate from the iOS-only
// `UNUserNotificationCenter` scheduling layer (`SystemNotificationService`) so it
// compiles and can be unit/property tested on any host — the platform
// notification center cannot be exercised on the build host. The iOS service
// queries which items already carry a Task_Reminder (from its pending requests),
// feeds the full item list plus that id-set here, and schedules a single daily
// `UNCalendarNotificationTrigger` only when the result is non-empty.
//
// This realizes the selection half of task 13.8 and is the function the
// evening-nudge-selection property test (13.9, Property 14) targets:
//
//   * Eligible items are exactly those that are **not completed** AND have **no
//     Task_Reminder set** (Req 7.13). Items with a reminder are excluded because
//     they are already covered by their own reminder.
//   * The selection is capped at the first **20** eligible items in input order
//     (Req 7.13).
//   * When the eligible set is empty, the selection is empty and the caller
//     omits the nudge for that day (Req 7.14).

/// Pure derivation of the Action_Items summarized by the daily evening nudge
/// (Req 7.13, 7.14). Every function is total, deterministic, and performs no
/// I/O — the set of item ids that already carry a Task_Reminder is injected.
public enum EveningNudgeSelection {

    /// The maximum number of items a single evening nudge summarizes (Req 7.13).
    public static let maxItems = 20

    /// The maximum length of the nudge body, matching the LLM-text bound so the
    /// fail-soft default text never exceeds what the LLM path would produce
    /// (Req 7.16).
    public static let maxBodyLength = 200

    /// The items the evening nudge should summarize, in input order, capped at
    /// ``maxItems`` (Property 14).
    ///
    /// An item is **eligible** when both hold:
    /// - its status is not ``ActionStatus/completed`` (Req 7.13), and
    /// - its id is **not** in `itemIdsWithReminder`, i.e. it has no Task_Reminder
    ///   set (Req 7.13) — items with a reminder are already covered by it.
    ///
    /// The eligible items are taken in their original order and truncated to the
    /// first `limit` (default ``maxItems``). When no item is eligible the result
    /// is empty, which is the caller's cue to omit the nudge for the day
    /// (Req 7.14).
    ///
    /// Pure and total: it never mutates its inputs and never throws.
    ///
    /// - Parameters:
    ///   - items: The candidate Action_Items (for example the day's open board).
    ///   - itemIdsWithReminder: Ids of items that have a Task_Reminder set; these
    ///     are excluded from the nudge (Req 7.13).
    ///   - limit: Maximum number of items to include. Defaults to ``maxItems``;
    ///     a non-positive limit yields an empty selection.
    /// - Returns: The eligible items in input order, truncated to `limit`.
    public static func eligibleItems(
        from items: [ActionItem],
        itemIdsWithReminder: Set<String>,
        limit: Int = maxItems
    ) -> [ActionItem] {
        guard limit > 0 else { return [] }

        let eligible = items.filter { item in
            item.status != .completed && !itemIdsWithReminder.contains(item.id)
        }
        return Array(eligible.prefix(limit))
    }

    /// A non-empty, length-bounded default body summarizing the selected items,
    /// used on the LLM fail-soft path (Req 7.17) before the LLM-generated text
    /// (task 13.10 / 13.11) is wired in.
    ///
    /// The body names the open-item count and lists the titles, truncated to
    /// ``maxBodyLength`` characters so it stays within the notification-text
    /// bound (Req 7.16). For an empty selection it returns the generic default
    /// (the caller omits the nudge in that case, so this is only a safety net).
    ///
    /// - Parameter items: The selected items (already capped by
    ///   ``eligibleItems(from:itemIdsWithReminder:limit:)``).
    /// - Returns: A non-empty body string of at most ``maxBodyLength`` characters.
    public static func defaultBody(for items: [ActionItem]) -> String {
        guard !items.isEmpty else { return NotificationDefaults.eveningNudgeBody }

        let titles = items.map(\.title).joined(separator: ", ")
        let noun = items.count == 1 ? "item" : "items"
        let body = "You have \(items.count) open \(noun): \(titles)"
        return String(body.prefix(maxBodyLength))
    }
}
