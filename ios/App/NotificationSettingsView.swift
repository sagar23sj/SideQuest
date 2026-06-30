import SwiftUI
import SideQuestKit

// MARK: - Notification settings (Req 7.12, 7.15, 11.1, 11.4)
//
// The opt-in notification controls: the collective evening nudge (Req 7.12) and
// the global daily self-reminder (Req 7.15), each an enable/disable toggle with
// a local time of day. Changing a toggle applies it immediately — requesting
// permission on first use (Req 11.1) and scheduling/cancelling via the portable
// applier — so the screen needs no explicit Save.

/// The notification settings tab.
struct NotificationSettingsView: View {

    @StateObject private var viewModel: NotificationSettingsViewModel
    @Environment(\.openURL) private var openURL

    init(
        notificationService: NotificationService,
        preferences: NotificationPreferencesStore,
        itemRepository: ActionItemRepository
    ) {
        _viewModel = StateObject(
            wrappedValue: NotificationSettingsViewModel(
                notificationService: notificationService,
                preferences: preferences,
                itemRepository: itemRepository
            )
        )
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("Evening nudge", isOn: $viewModel.eveningNudgeEnabled)
                    if viewModel.eveningNudgeEnabled {
                        DatePicker(
                            "Time",
                            selection: $viewModel.eveningNudgeTime,
                            displayedComponents: .hourAndMinute
                        )
                    }
                } header: {
                    Text("Evening nudge")
                } footer: {
                    Text("A daily summary of open items that don’t already have a reminder.")
                }

                Section {
                    Toggle("Daily reminder", isOn: $viewModel.globalDailyEnabled)
                    if viewModel.globalDailyEnabled {
                        DatePicker(
                            "Time",
                            selection: $viewModel.globalDailyTime,
                            displayedComponents: .hourAndMinute
                        )
                    }
                } header: {
                    Text("Daily reminder")
                } footer: {
                    Text("A gentle daily nudge to open SideQuest and keep momentum.")
                }
            }
            .navigationTitle("Reminders")
            // Apply on any change so toggles take effect without a Save button.
            .onChange(of: viewModel.eveningNudgeEnabled) { _ in apply() }
            .onChange(of: viewModel.eveningNudgeTime) { _ in apply() }
            .onChange(of: viewModel.globalDailyEnabled) { _ in apply() }
            .onChange(of: viewModel.globalDailyTime) { _ in apply() }
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
        }
    }

    private func apply() {
        Task { await viewModel.apply() }
    }
}
