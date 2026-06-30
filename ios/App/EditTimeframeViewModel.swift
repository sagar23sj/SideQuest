import Foundation
import SideQuestKit

/// Drives the timeframe screen (Req 9.6, 9.7).
///
/// Holds the in-progress timeframe selection for an `ActionItem`, resolves it
/// into a `Timeframe`, and validates it through the portable
/// `Domain.validateTimeframe` (task 4.4) so a `.specificDate` in the past is
/// rejected with a corrective message and cannot be saved (Req 9.7). On save it
/// persists the new timeframe through the `ActionItemRepository` (task 6.1);
/// a commit failure leaves the prior persisted state intact and surfaces an
/// error indication (Req 5.8).
@MainActor
final class EditTimeframeViewModel: ObservableObject {

    /// The chosen option, or `nil` until one is picked. Saving stays disabled
    /// while this is `nil`.
    @Published var option: TimeframeOption? {
        didSet { revalidate() }
    }

    /// Backing date for the `.specificDate` option.
    @Published var date: Date {
        didSet { revalidate() }
    }

    /// The past-date rejection message (Req 9.7), or `nil` when the current
    /// selection is valid. Bound into ``TimeframePicker``.
    @Published private(set) var validationMessage: String?

    /// A "not saved" indication shown when the repository write fails (Req 5.8).
    @Published var saveErrorMessage: String?

    /// Flips to `true` once the timeframe has been persisted, so the hosting
    /// view can dismiss.
    @Published private(set) var didSave = false

    let item: ActionItem
    private let itemRepository: ActionItemRepository

    /// The clock and calendar used for validation. Injected so the screen's
    /// "today" boundary is deterministic under test and matches the device's
    /// local time zone at runtime (Req 9.7).
    private let now: () -> Date
    private let calendar: Calendar

    init(
        item: ActionItem,
        itemRepository: ActionItemRepository,
        now: @escaping () -> Date = Date.init,
        calendar: Calendar = .current
    ) {
        self.item = item
        self.itemRepository = itemRepository
        self.now = now
        self.calendar = calendar

        // Seed the picker from the item's existing timeframe so editing starts
        // from its current value.
        self.option = TimeframeOption(item.timeframe)
        if case .specificDate(let existing) = item.timeframe {
            self.date = existing
        } else {
            self.date = now()
        }
        revalidate()
    }

    /// The `Timeframe` the current selection resolves to, or `nil` when no
    /// option is chosen yet (Req 9.6). The `.specificDate` payload carries the
    /// picked date exactly as the capture flow does, so validation/encoding
    /// behave identically across the two entry points.
    var resolvedTimeframe: Timeframe? {
        switch option {
        case .none: return nil
        case .today: return .today
        case .withinADay: return .withinADay
        case .withinAWeek: return .withinAWeek
        case .specificDate: return .specificDate(date)
        }
    }

    /// `true` only when a timeframe is chosen and it passes validation, so the
    /// Save button is disabled for an empty selection or a past specific date
    /// (Req 9.7). Single source of truth for the button's enabled state.
    var canSave: Bool {
        resolvedTimeframe != nil && validationMessage == nil
    }

    /// Recomputes ``validationMessage`` from the current selection using the
    /// portable rule (Req 9.7). A non-`.specificDate` option (or no selection)
    /// is always valid.
    private func revalidate() {
        guard let timeframe = resolvedTimeframe else {
            validationMessage = nil
            return
        }
        switch Domain.validateTimeframe(timeframe, now: now(), calendar: calendar) {
        case .valid:
            validationMessage = nil
        case .invalid(let reason):
            validationMessage = reason
        }
    }

    /// Persists the selected timeframe onto the item (Req 9.6, 9.7).
    ///
    /// Re-validates first so a past specific date is rejected even if `canSave`
    /// was stale, then writes through the repository. On success ``didSave``
    /// flips so the view can dismiss; on a commit failure the store is unchanged
    /// and ``saveErrorMessage`` surfaces the "not saved" indication (Req 5.8).
    func save() {
        revalidate()
        guard let timeframe = resolvedTimeframe, validationMessage == nil else { return }

        var updated = item
        updated.timeframe = timeframe
        do {
            try itemRepository.update(updated)
            saveErrorMessage = nil
            didSave = true
        } catch {
            saveErrorMessage = "Couldn’t save the timeframe. Your change wasn’t saved — please try again."
        }
    }

    /// Clears the save-error indication once acknowledged.
    func dismissSaveError() {
        saveErrorMessage = nil
    }
}
