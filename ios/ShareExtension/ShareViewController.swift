import UIKit
import SwiftUI
import SideQuestKit

/// Principal view controller for the SideQuest Share Extension.
///
/// The extension registers SideQuest_iOS as a share target (Req 4.1, declared by
/// `NSExtensionActivationRule` in `Info.plist` for links, text, images, and
/// movies) and runs in a **separate process** from the main app, writing
/// captured items into the shared App Group SQLite store so they appear in the
/// main app (Req 4.10, 13.2). It links `SideQuestKit` to share the exact same
/// models, store, and domain logic as the main app.
///
/// Responsibilities (task 8.1):
///   1. Read the shared attachments and classify them (Req 4.2).
///   2. For an unsupported type, show "content type not supported", discard the
///      item, and end the extension request (Req 4.4).
///   3. Otherwise host the SwiftUI categorization sheet that requires exactly
///      one Bucket and one Timeframe before saving (Req 4.3); cancelling
///      discards without creating an item (Req 4.7).
///   4. On confirm, build the not-started item and write it to the shared store
///      via `CaptureConfirmer` (task 8.3); a store-write failure keeps the sheet
///      so the user's selections are retained for a retry (Req 4.5, 4.6).
///
/// The controller is a thin shell: classification and capture logic live in
/// `SideQuestKit`, and reading the system payload lives in `SharedItemReader`.
final class ShareViewController: UIViewController {

    private let captureService: CaptureService = DefaultCaptureService()

    /// The shared App Group store, opened once. `nil` when the App Group
    /// container is unavailable (a distribution misconfiguration — Req 13.7);
    /// in that case there are no buckets to choose and Save stays disabled.
    private lazy var database: SideQuestDatabase? = try? SideQuestDatabase.openShared()

    private var hostingController: UIViewController?

    override func viewDidLoad() {
        super.viewDidLoad()
        Task { await begin() }
    }

    // MARK: - Flow

    @MainActor
    private func begin() async {
        let extensionItems = (extensionContext?.inputItems as? [NSExtensionItem]) ?? []
        let sharedItem = await SharedItemReader.read(extensionItems)

        // Classify and lower into a draft; `nil` means the type is not one of
        // links, text, images, or video references (Req 4.2, 4.4).
        guard let draft = captureService.beginCapture(sharedItem) else {
            host(
                UnsupportedContentView(onDismiss: { [weak self] in self?.discardAndFinish() })
            )
            return
        }

        let buckets = loadBuckets()
        host(
            CategorizationSheet(
                draft: draft,
                buckets: buckets,
                onCancel: { [weak self] in self?.discardAndFinish() },
                onSave: { [weak self] selection in
                    self?.confirm(draft: draft, selection: selection, buckets: buckets)
                }
            )
        )
    }

    /// Reads the account's buckets from the shared store for the sheet's bucket
    /// picker. Returns an empty list when the store is unavailable or the read
    /// fails — the sheet then shows a "no buckets" hint and keeps Save disabled.
    private func loadBuckets() -> [Bucket] {
        guard let database else { return [] }
        return (try? BucketRepository(database: database).fetchAll()) ?? []
    }

    /// Confirms a complete selection: build the not-started item and write it to
    /// the shared store (task 8.3). The account is the selected bucket's account,
    /// since buckets are account-scoped in the local store.
    private func confirm(
        draft: CaptureDraft,
        selection: CategorizationSelection,
        buckets: [Bucket]
    ) {
        guard let database,
              let confirmed = selection.confirmed,
              let bucket = buckets.first(where: { $0.id == confirmed.bucketId }) else {
            // Save is gated on a complete selection, so this is defensive.
            return
        }

        let confirmer = CaptureConfirmer(repository: ActionItemRepository(database: database))
        let result = confirmer.confirm(
            draft: draft,
            selection: selection,
            accountId: bucket.accountId,
            preview: nil
        )

        switch result {
        case .saved:
            // Item committed to the shared store; it is now visible to the main
            // app (Req 4.10).
            completeRequest()
        case .failed(let message, _):
            // No partial item was written; keep the sheet so the user's
            // selections are retained for a retry (Req 4.6).
            presentSaveError(message)
        case .incompleteSelection:
            break
        }
    }

    // MARK: - Hosting

    /// Replaces the current hosted SwiftUI content with `rootView`.
    @MainActor
    private func host<Content: View>(_ rootView: Content) {
        hostingController?.willMove(toParent: nil)
        hostingController?.view.removeFromSuperview()
        hostingController?.removeFromParent()

        let host = UIHostingController(rootView: rootView)
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
        hostingController = host
    }

    /// Shows a non-blocking error over the categorization sheet without
    /// dismissing it, so the user's bucket/timeframe selections are retained for
    /// a retry (Req 4.6).
    @MainActor
    private func presentSaveError(_ message: String) {
        let alert = UIAlertController(
            title: "Couldn’t save",
            message: message,
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    // MARK: - Extension lifecycle

    /// Ends the extension request after a successful save, returning control to
    /// the host app.
    private func completeRequest() {
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }

    /// Discards the shared item without creating an Action_Item and ends the
    /// request (Req 4.4 unsupported, Req 4.7 cancel). Reported as a user
    /// cancellation so the host app shows no error.
    private func discardAndFinish() {
        let error = NSError(domain: NSCocoaErrorDomain, code: NSUserCancelledError)
        extensionContext?.cancelRequest(withError: error)
    }
}
