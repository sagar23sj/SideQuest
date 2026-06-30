import SwiftUI
import SideQuestKit

// MARK: - Categorization sheet (task 8.1, Req 4.3, 4.7)
//
// The SwiftUI sheet the Share Extension presents for a supported shared item.
// It requires the user to choose exactly one Bucket and exactly one Timeframe
// and keeps Save disabled until both are chosen (Req 4.3); cancelling discards
// the item without creating an Action_Item (Req 4.7).
//
// The Save/Cancel outcomes are reported back to the hosting `ShareViewController`
// via closures so the view stays free of UIKit/extension-context concerns. The
// gating rule itself is the shared, host-tested
// ``CategorizationSelection/canSave`` — this view is the rule's single UI
// binding, so there is no second source of truth for the Save button's state.

/// Categorization sheet for a supported ``CaptureDraft``.
struct CategorizationSheet: View {

    /// What was shared, used to show a short preview header.
    let draft: CaptureDraft

    /// The account's buckets to choose from (read from the shared store).
    let buckets: [Bucket]

    /// Called when the user cancels before confirming — the host discards the
    /// shared item without creating an Action_Item (Req 4.7).
    let onCancel: () -> Void

    /// Called when the user confirms a complete selection. The host builds the
    /// not-started item and writes it to the shared store (task 8.3).
    let onSave: (CategorizationSelection) -> Void

    /// The in-progress selection. Single-valued bucket + timeframe, exactly the
    /// "exactly one each" rule (Req 4.3).
    @State private var selection = CategorizationSelection()

    /// Backing date for the `.specificDate` timeframe option. Only meaningful
    /// while ``timeframeKind`` is `.specificDate`.
    @State private var specificDate = Date()

    /// Which timeframe option is selected in the picker, mapped into
    /// `selection.timeframe`. `nil` until the user picks one, so Save stays
    /// disabled (Req 4.3).
    @State private var timeframeKind: TimeframeKind?

    var body: some View {
        NavigationStack {
            Form {
                Section("Shared") {
                    Text(draft.title)
                        .lineLimit(3)
                        .font(.body)
                }

                Section("Bucket") {
                    if buckets.isEmpty {
                        Text("No buckets yet. Create one in SideQuest first.")
                            .foregroundStyle(.secondary)
                    } else {
                        Picker("Bucket", selection: $selection.bucketId) {
                            Text("Choose a bucket").tag(String?.none)
                            ForEach(buckets) { bucket in
                                Text(bucket.name).tag(String?.some(bucket.id))
                            }
                        }
                    }
                }

                Section("Timeframe") {
                    Picker("Timeframe", selection: $timeframeKind) {
                        Text("Choose a timeframe").tag(TimeframeKind?.none)
                        ForEach(TimeframeKind.allCases) { kind in
                            Text(kind.label).tag(TimeframeKind?.some(kind))
                        }
                    }

                    if timeframeKind == .specificDate {
                        DatePicker(
                            "Date",
                            selection: $specificDate,
                            displayedComponents: .date
                        )
                    }
                }
            }
            .navigationTitle("Save to SideQuest")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { onSave(selection) }
                        // Save is impossible until exactly one bucket and one
                        // timeframe are chosen (Req 4.3).
                        .disabled(!selection.canSave)
                }
            }
        }
        // Keep `selection.timeframe` in sync with the picker + date controls.
        .onChange(of: timeframeKind) { _ in updateTimeframe() }
        .onChange(of: specificDate) { _ in updateTimeframe() }
    }

    /// Maps the picker selection (+ date) into `selection.timeframe`.
    private func updateTimeframe() {
        switch timeframeKind {
        case .none:
            selection.timeframe = nil
        case .today:
            selection.timeframe = .today
        case .withinADay:
            selection.timeframe = .withinADay
        case .withinAWeek:
            selection.timeframe = .withinAWeek
        case .specificDate:
            selection.timeframe = .specificDate(specificDate)
        }
    }
}

/// The timeframe options offered on the sheet (Req 9.6). Kept separate from the
/// `Timeframe` model so the `.specificDate` option can be selected before a date
/// is chosen.
enum TimeframeKind: String, CaseIterable, Identifiable {
    case today
    case withinADay
    case withinAWeek
    case specificDate

    var id: String { rawValue }

    var label: String {
        switch self {
        case .today: return "Today"
        case .withinADay: return "Within a day"
        case .withinAWeek: return "Within a week"
        case .specificDate: return "Specific date"
        }
    }
}

// MARK: - Unsupported content (Req 4.4)

/// Shown when the shared item's type is not one of links, text, images, or
/// video references. It states the content type is not supported; dismissing
/// discards the item without creating an Action_Item and ends the extension
/// request (Req 4.4).
struct UnsupportedContentView: View {

    /// Called when the user dismisses — the host discards the item and ends the
    /// extension request.
    let onDismiss: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Image(systemName: "exclamationmark.triangle")
                    .font(.largeTitle)
                    .foregroundStyle(.secondary)
                Text("Content type not supported")
                    .font(.headline)
                    .multilineTextAlignment(.center)
                Text("SideQuest can save links, text, images, and videos.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Dismiss", action: onDismiss)
                }
            }
        }
    }
}
