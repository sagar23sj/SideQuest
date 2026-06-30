import SwiftUI
import SideQuestKit

// MARK: - Item detail screen (task 18.1 — functional equivalence hub)
//
// Pushed from a board row, this screen gathers the per-item capabilities that
// give parity with the Android client: changing the item's timeframe (Req 9.6,
// 9.7), building/editing its action plan (Req 9.8–9.10), and attaching a
// reminder (Req 7.2–7.5). Each is opened as a sheet hosting the existing,
// self-contained editor view, so this screen stays a thin hub that wires those
// editors to the shared repositories and notification service.
struct ItemDetailView: View {

    let item: ActionItem
    let itemRepository: ActionItemRepository
    let planRepository: ActionPlanRepository
    let notificationService: NotificationService

    @State private var editingTimeframe = false
    @State private var editingPlan = false
    @State private var editingReminder = false

    var body: some View {
        List {
            Section("Item") {
                Text(item.title)
                    .font(.headline)
                LabeledContent("Status", value: item.status.displayName)
                LabeledContent("Timeframe", value: Self.timeframeLabel(item.timeframe))
            }

            Section("Plan & scheduling") {
                Button {
                    editingTimeframe = true
                } label: {
                    Label("Edit timeframe", systemImage: "calendar")
                }

                Button {
                    editingPlan = true
                } label: {
                    Label("Action plan", systemImage: "checklist")
                }

                Button {
                    editingReminder = true
                } label: {
                    Label("Set a reminder", systemImage: "bell.badge")
                }
            }
        }
        .navigationTitle("Details")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $editingTimeframe) {
            EditTimeframeView(item: item, itemRepository: itemRepository)
        }
        .sheet(isPresented: $editingPlan) {
            ActionPlanView(
                item: item,
                planRepository: planRepository,
                itemRepository: itemRepository
            )
        }
        .sheet(isPresented: $editingReminder) {
            ReminderEditView(item: item, notificationService: notificationService)
        }
    }

    /// A human-readable label for the item's current timeframe.
    private static func timeframeLabel(_ timeframe: Timeframe) -> String {
        switch timeframe {
        case .today: return "Today"
        case .withinADay: return "Within a day"
        case .withinAWeek: return "Within a week"
        case .specificDate(let date):
            return date.formatted(date: .abbreviated, time: .omitted)
        }
    }
}
