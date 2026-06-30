import SwiftUI
import SideQuestKit

// MARK: - Board screen (Req 8.1–8.6)
//
// The action board displays Action_Items grouped by Bucket with a completion
// counter pinned at the top. It is split into two views:
//
//   * `BoardView` — the stateful container. It owns the `BoardViewModel`, binds
//     it to the repository observation streams, and forwards the derived
//     `BoardState` plus user intents to the presentational view.
//   * `BoardContentView` — a stateless view that renders a `BoardState` and
//     reports status-change intents through closures. Keeping it free of
//     stores/streams makes it trivially previewable and testable.
//
// Each non-completed row also carries the press-and-hold completion control
// (`HoldToCompleteButton`, Req 8.7–8.9): holding it for 800 ms persists the
// "completed" status through the same `onChangeStatus` path. A status menu
// remains available for arbitrary status changes (and exercises the same
// success/failure handling, Req 8.3/8.4).

/// The stateful board container: owns the view model, starts observation, and
/// renders ``BoardContentView`` (Req 8.1–8.6).
struct BoardView: View {

    @StateObject private var viewModel: BoardViewModel

    /// Builds the per-item detail screen pushed when a row is opened. Type-erased
    /// so the board does not depend on the concrete detail view's dependencies.
    private let detail: (ActionItem) -> AnyView

    init(
        itemRepository: ActionItemRepository,
        bucketRepository: BucketRepository,
        notificationService: NotificationService,
        detail: @escaping (ActionItem) -> AnyView
    ) {
        _viewModel = StateObject(
            wrappedValue: BoardViewModel(
                itemRepository: itemRepository,
                bucketRepository: bucketRepository,
                notificationService: notificationService
            )
        )
        self.detail = detail
    }

    var body: some View {
        BoardContentView(
            board: viewModel.board,
            statusErrorMessage: viewModel.statusErrorMessage,
            onChangeStatus: { item, status in
                viewModel.changeStatus(of: item, to: status)
            },
            onDismissError: {
                viewModel.dismissStatusError()
            },
            detail: detail
        )
        // Observation runs for the lifetime of the view and is cancelled
        // automatically when it disappears.
        .task {
            await viewModel.observe()
        }
    }
}

/// The stateless board UI: renders a ``BoardState`` and reports intents through
/// closures (Req 8.1, 8.2, 8.5).
struct BoardContentView: View {

    let board: BoardState
    let statusErrorMessage: String?
    let onChangeStatus: (ActionItem, ActionStatus) -> Void
    let onDismissError: () -> Void
    /// Builds the detail screen for a row when present. `nil` in previews, where
    /// rows render without a navigation affordance.
    var detail: ((ActionItem) -> AnyView)? = nil

    /// Whether there is anything to show. The board can be empty before the
    /// first capture or while data loads.
    private var isEmpty: Bool {
        board.groups.allSatisfy { $0.items.isEmpty }
    }

    var body: some View {
        NavigationStack {
            Group {
                if isEmpty {
                    emptyState
                } else {
                    boardList
                }
            }
            .navigationTitle("Board")
            .safeAreaInset(edge: .top) {
                CompletionCounterView(count: board.completionCount)
            }
            // The error indication for a failed status change (Req 8.4): the
            // prior status and indicator are untouched, and this surfaces that
            // the update did not save.
            .alert(
                "Status not saved",
                isPresented: Binding(
                    get: { statusErrorMessage != nil },
                    set: { stillPresented in
                        if !stillPresented { onDismissError() }
                    }
                ),
                actions: {
                    Button("OK", role: .cancel, action: onDismissError)
                },
                message: {
                    if let statusErrorMessage {
                        Text(statusErrorMessage)
                    }
                }
            )
        }
    }

    // MARK: - List

    private var boardList: some View {
        List {
            // Stable identity for groups keeps row animations correct as the
            // store changes (Req 8.1 ordering is decided by the domain logic).
            ForEach(board.groups, id: \.bucket.id) { group in
                Section {
                    ForEach(group.items, id: \.item.id) { boardItem in
                        BoardItemRow(
                            boardItem: boardItem,
                            onChangeStatus: onChangeStatus,
                            detail: detail
                        )
                    }
                } header: {
                    Text(displayName(for: group.bucket))
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "tray")
                .font(.largeTitle)
                .foregroundStyle(.secondary)
            Text("Nothing here yet")
                .font(.headline)
            Text("Share something into SideQuest to start tracking it.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// The header label for a bucket. Items whose bucket is unknown are surfaced
    /// by the domain logic under a synthetic placeholder bucket with an empty
    /// name; show a clear stand-in so those items are never hidden.
    private func displayName(for bucket: Bucket) -> String {
        let trimmed = bucket.name.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "Unsorted" : trimmed
    }
}

/// The completion counter pinned at the top of the board (Req 8.5): the total
/// number of completed items, never less than zero (guaranteed by the domain
/// logic).
struct CompletionCounterView: View {

    let count: Int

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.seal.fill")
                .foregroundStyle(.green)
            Text("Completed")
                .font(.headline)
            Spacer()
            Text("\(count)")
                .font(.title3.bold())
                .monospacedDigit()
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
        .background(.bar)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Completed items")
        .accessibilityValue("\(count)")
    }
}

/// A single board row: a color indicator for the item's current status plus its
/// title, a press-and-hold completion control for non-completed items
/// (Req 8.7–8.9), and a menu to change the status (Req 8.2, 8.3).
private struct BoardItemRow: View {

    let boardItem: BoardItem
    let onChangeStatus: (ActionItem, ActionStatus) -> Void
    /// Builds the detail screen for this item, or `nil` to render without a
    /// navigation affordance (previews).
    let detail: ((ActionItem) -> AnyView)?

    private var item: ActionItem { boardItem.item }

    var body: some View {
        HStack(spacing: 12) {
            StatusIndicator(colorHex: boardItem.statusColor)

            VStack(alignment: .leading, spacing: 2) {
                Text(item.title)
                    .font(.body)
                    .lineLimit(2)
                Text(item.status.displayName)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            // Group only the indicator + text into one descriptive element so
            // the interactive controls below remain separately operable by
            // assistive technologies. Color is never the only signal: the
            // status is spelled out in text and in the value.
            .accessibilityElement(children: .combine)
            .accessibilityLabel(item.title)
            .accessibilityValue("Status: \(item.status.displayName)")

            Spacer()

            // Press-and-hold to complete (Req 8.7–8.9). Hidden once the item is
            // already completed since there is nothing left to complete.
            if item.status != .completed {
                HoldToCompleteButton(
                    onComplete: { onChangeStatus(item, .completed) },
                    tint: Color(hex: boardItem.statusColor) ?? .accentColor
                )
            }

            statusMenu

            // Open the item's detail screen (timeframe, action plan, reminders)
            // when navigation is available (Req 9.6–9.10, 7.2).
            if let detail {
                NavigationLink {
                    detail(item)
                } label: {
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.tertiary)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("Open details")
            }
        }
        .padding(.vertical, 4)
    }

    /// Lets the user move the item between statuses. The chosen status is
    /// persisted by the view model; the indicator updates only after the write
    /// commits (Req 8.3) and stays put if it fails (Req 8.4).
    private var statusMenu: some View {
        Menu {
            ForEach(ActionStatus.orderedForPicker, id: \.self) { status in
                Button {
                    onChangeStatus(item, status)
                } label: {
                    if status == item.status {
                        Label(status.displayName, systemImage: "checkmark")
                    } else {
                        Text(status.displayName)
                    }
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .font(.title3)
                // 44pt minimum touch target for the control.
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Change status")
    }
}

/// The per-status color indicator (Req 8.2). Renders the bucket's configured
/// color for the item's current status; falls back to a neutral semantic color
/// when the stored value is empty or unparseable (e.g. placeholder buckets).
private struct StatusIndicator: View {

    let colorHex: String

    var body: some View {
        Circle()
            .fill(Color(hex: colorHex) ?? Color.gray.opacity(0.4))
            .frame(width: 16, height: 16)
            .overlay(
                Circle().strokeBorder(Color.primary.opacity(0.15), lineWidth: 0.5)
            )
            .accessibilityHidden(true)
    }
}

// MARK: - Previews

#Preview("Populated board") {
    BoardContentView(
        board: BoardPreviewData.populated,
        statusErrorMessage: nil,
        onChangeStatus: { _, _ in },
        onDismissError: {}
    )
}

#Preview("Status error") {
    BoardContentView(
        board: BoardPreviewData.populated,
        statusErrorMessage: "Couldn’t update the status. Your change wasn’t saved — please try again.",
        onChangeStatus: { _, _ in },
        onDismissError: {}
    )
}

#Preview("Empty board") {
    BoardContentView(
        board: BoardState(groups: [], completionCount: 0),
        statusErrorMessage: nil,
        onChangeStatus: { _, _ in },
        onDismissError: {}
    )
}

/// Sample data for previews, built through the real domain logic so the preview
/// exercises the same grouping/ordering/color resolution the app uses.
private enum BoardPreviewData {

    static var populated: BoardState {
        let account = "acct-preview"
        let work = Bucket(
            id: "bkt-work",
            accountId: account,
            name: "Work",
            notStartedColor: "#9E9E9E",
            inProgressColor: "#1E88E5",
            completedColor: "#43A047",
            sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: 0), version: 1, deleted: false, dirty: false)
        )
        let home = Bucket(
            id: "bkt-home",
            accountId: account,
            name: "Home",
            notStartedColor: "#BDBDBD",
            inProgressColor: "#FB8C00",
            completedColor: "#8E24AA",
            sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: 0), version: 1, deleted: false, dirty: false)
        )

        func item(_ id: String, _ bucket: Bucket, _ title: String, _ status: ActionStatus, _ offset: TimeInterval) -> ActionItem {
            ActionItem(
                id: id,
                accountId: account,
                bucketId: bucket.id,
                title: title,
                contentType: .text,
                timeframe: .today,
                status: status,
                createdAt: Date(timeIntervalSince1970: offset),
                sync: SyncMeta(updatedAt: Date(timeIntervalSince1970: offset), version: 1, deleted: false, dirty: false)
            )
        }

        let items = [
            item("i1", work, "Draft the proposal", .inProgress, 100),
            item("i2", work, "Reply to onboarding email", .notStarted, 200),
            item("i3", work, "Ship release notes", .completed, 50),
            item("i4", home, "Water the plants", .completed, 10),
            item("i5", home, "Plan weekend trip", .notStarted, 300)
        ]

        return Domain.buildBoard(items: items, buckets: [home, work])
    }
}
