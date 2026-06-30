import SwiftUI
import SideQuestKit

// MARK: - Reminder editor (Req 7.2–7.5, 7.18, 11.1, 11.4)
//
// Attaches a Task_Reminder to an Action_Item: a local time of day, an
// until-date (constrained to [today, today + 365] by the portable validator),
// and an optional daily recurrence. Saving requests notification permission the
// first time a notifying feature is used (Req 11.1); when permission is denied
// it shows an explanation and a deep link to iOS settings (Req 7.18, 11.4).

/// The reminder editor sheet for an item.
struct ReminderEditView: View {

    @StateObject private var viewModel: ReminderEditViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    init(item: ActionItem, notificationService: NotificationService) {
        _viewModel = StateObject(
            wrappedValue: ReminderEditViewModel(
                item: item,
                notificationService: notificationService
            )
        )
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Item") {
                    Text(viewModel.item.title)
                        .font(.body)
                        .lineLimit(3)
                }

                Section("Reminder") {
                    DatePicker(
                        "Time",
                        selection: $viewModel.time,
                        displayedComponents: .hourAndMinute
                    )
                    DatePicker(
                        "Until",
                        selection: $viewModel.untilDate,
                        displayedComponents: .date
                    )
                    Toggle("Repeat daily", isOn: $viewModel.recurringDaily)

                    if let message = viewModel.validationMessage {
                        Label(message, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote)
                            .foregroundStyle(.red)
                            .accessibilityElement(children: .combine)
                    }
                }
            }
            .navigationTitle("Reminder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task { await viewModel.save() }
                    }
                }
            }
            // Notifications are off — explain and offer a jump to iOS settings
            // (Req 7.18, 11.4).
            .alert(
                "Notifications are off",
                isPresented: $viewModel.permissionDenied,
                actions: {
                    if let url = viewModel.settingsURL {
                        Button("Open Settings") { openURL(url) }
                    }
                    Button("OK", role: .cancel) {}
                },
                message: {
                    Text("Turn on notifications for SideQuest in Settings to receive reminders.")
                }
            )
            .onChange(of: viewModel.didSchedule) { scheduled in
                if scheduled { dismiss() }
            }
        }
    }
}
