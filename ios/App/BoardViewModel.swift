import Foundation
import Combine
import GRDB
import SideQuestKit

/// Drives the action board screen (Req 8.1–8.6).
///
/// The view model is the board's only state owner: it subscribes to the
/// repositories' GRDB `ValueObservation` streams for live items and buckets,
/// feeds them through the portable domain logic (`Domain.buildBoard`) to derive
/// the grouped, ordered, color-resolved ``BoardState`` (Req 8.1, 8.2), and
/// publishes it for the view to render. Because every displayed value comes
/// from the local store via observation, the board reflects only **persisted**
/// state.
///
/// Status changes (Req 8.3, 8.4) are deliberately **not** applied optimistically:
/// ``changeStatus(of:to:)`` writes through the repository and lets the
/// observation re-emit the persisted row, so the color indicator updates only
/// after the change is committed (the re-emit lands well within the 500 ms
/// budget — Req 8.3). When the write fails the store rolls back and the stream
/// does not emit, so the displayed status and its indicator stay exactly as they
/// were (Req 8.4); the view model surfaces ``statusErrorMessage`` as the
/// required error indication.
@MainActor
final class BoardViewModel: ObservableObject {

    /// The aggregated board the view renders: groups ordered for display, each
    /// item carrying its resolved per-status color, plus the completion counter
    /// (Req 8.1, 8.2, 8.5).
    @Published private(set) var board: BoardState

    /// A user-facing error indication shown when a status change fails to
    /// persist (Req 8.4). `nil` when there is nothing to report.
    @Published var statusErrorMessage: String?

    private let itemRepository: ActionItemRepository
    private let bucketRepository: BucketRepository

    /// Used to cancel an item's pending reminders when it is marked completed
    /// (Req 7.8). Optional so the board can be previewed/tested without it.
    private let notificationService: NotificationService?

    /// Latest values from each stream, combined on every emission so the board
    /// always reflects both the current items and the current buckets.
    private var latestItems: [ActionItem] = []
    private var latestBuckets: [Bucket] = []

    init(
        itemRepository: ActionItemRepository,
        bucketRepository: BucketRepository,
        notificationService: NotificationService? = nil
    ) {
        self.itemRepository = itemRepository
        self.bucketRepository = bucketRepository
        self.notificationService = notificationService
        self.board = BoardState(groups: [], completionCount: 0)
    }

    // MARK: - Reactive observation (Req 8.1, 8.2, 8.5)

    /// Observes the item and bucket streams until the surrounding task is
    /// cancelled (e.g. the view disappears). Each emission rebuilds the board,
    /// so inserts, edits, deletes, and status changes — including writes made by
    /// the Share Extension process — flow into the UI reactively.
    func observe() async {
        await withTaskGroup(of: Void.self) { group in
            group.addTask { [weak self] in await self?.observeItems() }
            group.addTask { [weak self] in await self?.observeBuckets() }
        }
    }

    private func observeItems() async {
        do {
            for try await items in itemRepository.itemsStream() {
                latestItems = items
                rebuild()
            }
        } catch {
            // A failed observation leaves the last good board in place rather
            // than clearing the screen; the next successful emission recovers.
        }
    }

    private func observeBuckets() async {
        do {
            for try await buckets in bucketRepository.bucketsStream() {
                latestBuckets = buckets
                rebuild()
            }
        } catch {
            // See `observeItems`: keep the last good board on failure.
        }
    }

    /// Recomputes ``board`` from the latest items and buckets via the portable
    /// domain logic. Buckets are presented in case-insensitive name order for a
    /// stable, predictable board layout; ordering *within* a bucket (ascending
    /// `createdAt`, tie-broken by id) and the per-status colors are decided by
    /// `Domain.buildBoard` (Req 8.1, 8.2).
    private func rebuild() {
        let orderedBuckets = latestBuckets.sorted {
            $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
        }
        board = Domain.buildBoard(items: latestItems, buckets: orderedBuckets)
    }

    // MARK: - Status changes (Req 8.3, 8.4)

    /// Persists a new ``ActionStatus`` for `item` (Req 8.3, 8.4).
    ///
    /// On success the repository commits the edit and the observation re-emits,
    /// which rebuilds the board and updates the item's color indicator to match
    /// the new status (Req 8.3). On failure the store is left untouched and the
    /// stream does not emit, so the prior status and its indicator remain
    /// unchanged; the view model publishes ``statusErrorMessage`` so the view
    /// can show that the update did not save (Req 8.4).
    func changeStatus(of item: ActionItem, to newStatus: ActionStatus) {
        guard item.status != newStatus else { return }

        var updated = item
        updated.status = newStatus
        do {
            try itemRepository.update(updated)
            statusErrorMessage = nil
            // Completing an item cancels all of its pending reminders (Req 7.8).
            if newStatus == .completed, let notificationService {
                let itemId = item.id
                Task { await notificationService.cancelReminders(for: itemId) }
            }
        } catch {
            statusErrorMessage = "Couldn’t update the status. Your change wasn’t saved — please try again."
        }
    }

    /// Clears the status-change error indication once the user has acknowledged
    /// it (Req 8.4).
    func dismissStatusError() {
        statusErrorMessage = nil
    }
}
