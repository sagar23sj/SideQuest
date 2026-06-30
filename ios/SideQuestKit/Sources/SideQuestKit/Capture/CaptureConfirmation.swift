import Foundation

// MARK: - Confirm-capture write to the shared store (task 8.3, Req 4.5, 4.6, 4.10)
//
// This is the I/O step of the Share Extension capture flow (design:
// "CaptureService" step 5 & 6). The pure pieces — classification, drafting, and
// the selection gate — live in `CaptureService`/`CategorizationSelection`. Here
// we take a confirmed `(bucket, timeframe)` selection plus the `CaptureDraft`,
// build the not-started `ActionItem` (via the pure `CaptureDraft.makeActionItem`
// builder), and write it to the shared App Group store through
// `ActionItemRepository.create(_:)`.
//
// Because the repository writes inside a GRDB transaction that rolls back on
// failure, a failed write leaves the store at its prior state — no partial item
// is ever persisted (Req 4.6). The confirmer never mutates or discards the
// caller's `CategorizationSelection`, and on failure it hands the selection
// back in the result, so the categorization sheet can keep the user's chosen
// bucket and timeframe for a retry (Req 4.6).
//
// The confirmer is host-testable: it depends only on `SideQuestKit` types
// (`CaptureDraft`, `CategorizationSelection`, `ActionItemRepository`), so the
// Share Extension's view controller stays a thin shell that calls `confirm(...)`
// and renders the returned `CaptureConfirmationResult`.

/// The outcome of confirming a capture.
///
/// The categorization sheet maps this to its UI: `.saved` ends the extension
/// request, `.failed` shows the error while keeping the (returned) selection in
/// place, and `.incompleteSelection` is a defensive case the gated Save button
/// should already prevent.
public enum CaptureConfirmationResult: Equatable {

    /// The store write committed durably; the persisted item is returned. The
    /// item has status ``ActionStatus/notStarted`` and carries the confirmed
    /// bucket and timeframe (Req 4.5), and — when the database is the shared
    /// App Group store — is now visible to the main app (Req 4.10).
    case saved(ActionItem)

    /// The store write failed. No partial item was created (the transaction
    /// rolled back) and the user's selections are returned unchanged so they
    /// can be retained for a retry (Req 4.6). `message` is a user-facing error
    /// indicating the shared item could not be saved.
    case failed(message: String, retained: CategorizationSelection)

    /// The selection was not complete (missing a bucket and/or a timeframe), so
    /// nothing was written. The sheet gates Save on
    /// ``CategorizationSelection/canSave``, so this should not occur in normal
    /// use; it is surfaced defensively with the partial selection retained.
    case incompleteSelection(retained: CategorizationSelection)
}

/// Coordinates the confirm step of the capture flow: build the not-started
/// item and write it to the shared store (task 8.3).
public protocol CaptureConfirming {

    /// Confirms a capture: on a complete `selection`, build the `ActionItem`
    /// from `draft` and persist it; otherwise report the selection incomplete.
    ///
    /// - Parameters:
    ///   - draft: The categorization draft produced by
    ///     ``CaptureService/beginCapture(_:)``.
    ///   - selection: The user's bucket + timeframe selection from the sheet.
    ///   - accountId: The signed-in account the new item belongs to.
    ///   - preview: A resolved link preview when one is already available; when
    ///     `nil` a link draft persists an unresolved fallback preview (Req 4.9).
    /// - Returns: The capture outcome (saved / failed / incomplete).
    func confirm(
        draft: CaptureDraft,
        selection: CategorizationSelection,
        accountId: String,
        preview: LinkPreview?
    ) -> CaptureConfirmationResult
}

/// Default ``CaptureConfirming`` used by the Share Extension. Writes through an
/// ``ActionItemRepository`` over the shared App Group store.
public struct CaptureConfirmer: CaptureConfirming {

    /// Default user-facing message when the shared item could not be saved
    /// (Req 4.6). The view layer may localize/override this.
    public static let defaultFailureMessage =
        "The shared item couldn’t be saved. Please try again."

    private let repository: ActionItemRepository
    private let now: RepositoryClock
    private let failureMessage: String

    /// - Parameters:
    ///   - repository: Repository over the shared store; its `create(_:)`
    ///     performs the durable, atomic write (Req 4.10, 5.5).
    ///   - now: Creation instant for the item's `createdAt`; injected for
    ///     deterministic tests. Defaults to the wall clock.
    ///   - failureMessage: User-facing copy shown on a store-write failure.
    public init(
        repository: ActionItemRepository,
        now: @escaping RepositoryClock = Date.init,
        failureMessage: String = CaptureConfirmer.defaultFailureMessage
    ) {
        self.repository = repository
        self.now = now
        self.failureMessage = failureMessage
    }

    public func confirm(
        draft: CaptureDraft,
        selection: CategorizationSelection,
        accountId: String,
        preview: LinkPreview? = nil
    ) -> CaptureConfirmationResult {
        // Gate on a fully-specified selection (Req 4.3). The sheet keeps Save
        // disabled until `canSave`, so this guard is defensive.
        guard let confirmed = selection.confirmed else {
            return .incompleteSelection(retained: selection)
        }

        // Build the not-started item, preserving the confirmed bucket and
        // timeframe (Req 4.5). `id` is a fresh client-generated identifier
        // (Req 5.7); the repository stamps the authoritative dirty sync metadata
        // on create.
        let item = draft.makeActionItem(
            id: repository.newIdentifier(),
            accountId: accountId,
            bucketId: confirmed.bucketId,
            timeframe: confirmed.timeframe,
            now: now(),
            preview: preview
        )

        do {
            // Atomic, durable write to the shared App Group store (Req 4.10,
            // 5.5). On success the item is visible to the main app.
            let saved = try repository.create(item)
            return .saved(saved)
        } catch {
            // The write rolled back, so no partial item exists (Req 4.6). Hand
            // the selection back so the sheet retains it for a retry.
            return .failed(message: failureMessage, retained: selection)
        }
    }
}
