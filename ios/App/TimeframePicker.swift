import SwiftUI
import SideQuestKit

// MARK: - Timeframe option set + picker (Req 9.6, 9.7)
//
// The timeframe option set offered when assigning a target window to an
// Action_Item: "today", "within a day", "within a week", and a specific date
// (Req 9.6). A `.specificDate` in the past is rejected with a corrective
// message produced by the portable `Domain.validateTimeframe` (task 4.4,
// Req 9.7) — this view never re-implements that rule, it only surfaces it.
//
// `TimeframePicker` is a *presentational* control: it binds the chosen option
// and date and renders the validation message it is handed. Resolving the
// option/date into a `Timeframe` and running validation lives in
// ``EditTimeframeViewModel`` so the rule stays testable and there is a single
// source of truth for the Save button's enabled state.

/// The timeframe options offered in the picker (Req 9.6).
///
/// Kept separate from the `Timeframe` model so `.specificDate` can be selected
/// before a date is picked (the model's `.specificDate` always carries a date).
/// Mirrors the Share Extension's `TimeframeKind`; duplicated intentionally so
/// the main-app target does not depend on extension-only code.
enum TimeframeOption: String, CaseIterable, Identifiable {
    case today
    case withinADay
    case withinAWeek
    case specificDate

    var id: String { rawValue }

    /// The user-facing label, read by VoiceOver via the picker.
    var label: String {
        switch self {
        case .today: return "Today"
        case .withinADay: return "Within a day"
        case .withinAWeek: return "Within a week"
        case .specificDate: return "Specific date"
        }
    }

    /// The option matching an existing `Timeframe`, for seeding the picker when
    /// editing an item that already has a timeframe.
    init(_ timeframe: Timeframe) {
        switch timeframe {
        case .today: self = .today
        case .withinADay: self = .withinADay
        case .withinAWeek: self = .withinAWeek
        case .specificDate: self = .specificDate
        }
    }
}

/// A presentational control for choosing a ``TimeframeOption`` and, when
/// `.specificDate` is chosen, a date (Req 9.6). It renders the
/// `validationMessage` it is given (the past-date rejection text from
/// `Domain.validateTimeframe`, Req 9.7) inline beneath the date picker.
struct TimeframePicker: View {

    /// The chosen option, or `nil` until the user picks one.
    @Binding var option: TimeframeOption?

    /// The backing date for the `.specificDate` option. Only meaningful while
    /// `option == .specificDate`.
    @Binding var date: Date

    /// The corrective message to show when the current selection is invalid
    /// (a past specific date — Req 9.7), or `nil` when the selection is valid.
    let validationMessage: String?

    var body: some View {
        Section("Timeframe") {
            Picker("Timeframe", selection: $option) {
                Text("Choose a timeframe").tag(TimeframeOption?.none)
                ForEach(TimeframeOption.allCases) { choice in
                    Text(choice.label).tag(TimeframeOption?.some(choice))
                }
            }

            if option == .specificDate {
                DatePicker(
                    "Date",
                    selection: $date,
                    displayedComponents: .date
                )

                if let validationMessage {
                    Label(validationMessage, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.red)
                        // Surface the rejection to assistive tech as one element.
                        .accessibilityElement(children: .combine)
                }
            }
        }
    }
}

// MARK: - Previews

#Preview("Specific date — valid") {
    Form {
        TimeframePicker(
            option: .constant(.specificDate),
            date: .constant(Date()),
            validationMessage: nil
        )
    }
}

#Preview("Specific date — past rejected") {
    Form {
        TimeframePicker(
            option: .constant(.specificDate),
            date: .constant(Date(timeIntervalSince1970: 0)),
            validationMessage: Domain.pastSpecificDateMessage
        )
    }
}
