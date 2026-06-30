import Foundation
import SideQuestKit

/// Drives the bucket-management screen (Req 9.1–9.5).
///
/// Binds to the repository's bucket `ValueObservation` stream (Req 5.2) and
/// performs create / rename / delete through the `BucketManagementService`
/// (task 7.1), which applies the portable name validation (Req 9.2, 9.3) and the
/// reassign-or-delete / confirm-delete-items decision flow (Req 9.4, 9.5). New
/// buckets are associated with the current account when signed in, falling back
/// to a stable local account id for offline use, and given default per-status
/// colors that are distinct (Req 8.2).
@MainActor
final class BucketsViewModel: ObservableObject {

    /// A pending delete that needs user confirmation, carrying the bucket and
    /// the classified decision (Req 9.4, 9.5).
    struct PendingDelete: Identifiable {
        let id: String
        let bucket: Bucket
        let decision: BucketDeleteDecision
    }

    @Published private(set) var buckets: [Bucket] = []
    @Published var newBucketName: String = ""
    @Published var errorMessage: String?
    @Published var pendingDelete: PendingDelete?

    private let bucketRepository: BucketRepository
    private let bucketManagement: BucketManagementService
    private let authService: AuthService?

    /// Default per-status colors for a new bucket — distinct so the status
    /// indicator mapping is injective (Req 8.2).
    private static let defaultNotStartedColor = "#9E9E9E"
    private static let defaultInProgressColor = "#1E88E5"
    private static let defaultCompletedColor = "#43A047"

    /// Fallback account id used offline before sign-in. The sync layer associates
    /// data with the real account once signed in (Req 10.2).
    private static let localAccountId = "local-account"

    init(
        bucketRepository: BucketRepository,
        bucketManagement: BucketManagementService,
        authService: AuthService? = nil
    ) {
        self.bucketRepository = bucketRepository
        self.bucketManagement = bucketManagement
        self.authService = authService
    }

    /// Whether the typed name can be submitted (non-empty after trimming).
    var canCreate: Bool {
        !newBucketName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    // MARK: - Observation (Req 5.2)

    /// Observes the bucket stream until the surrounding task is cancelled,
    /// presenting buckets in case-insensitive name order.
    func observe() async {
        do {
            for try await live in bucketRepository.bucketsStream() {
                buckets = live.sorted {
                    $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
                }
            }
        } catch {
            // Keep the last good list on a transient observation failure.
        }
    }

    // MARK: - Create (Req 9.1, 9.2, 9.3)

    /// Creates a bucket from the typed name, validating it through the service.
    func createBucket() async {
        let name = newBucketName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }

        let accountId = await currentAccountId()
        let candidate = Bucket(
            id: bucketRepository.newIdentifier(),
            accountId: accountId,
            name: name,
            notStartedColor: Self.defaultNotStartedColor,
            inProgressColor: Self.defaultInProgressColor,
            completedColor: Self.defaultCompletedColor,
            sync: SyncMeta(updatedAt: Date(), version: 0, deleted: false, dirty: false)
        )

        do {
            switch try bucketManagement.createBucket(candidate) {
            case .created:
                newBucketName = ""
                errorMessage = nil
            case .duplicateName:
                errorMessage = "A bucket with that name already exists."
            case .invalidLength:
                errorMessage = "Bucket names must be 1–50 characters."
            case .renamed, .bucketNotFound:
                errorMessage = "Couldn’t create the bucket. Please try again."
            }
        } catch {
            errorMessage = "Couldn’t save the bucket. Please try again."
        }
    }

    // MARK: - Rename (Req 9.1, 9.2, 9.3)

    /// Renames `bucket` to `newName`.
    func rename(_ bucket: Bucket, to newName: String) {
        do {
            switch try bucketManagement.renameBucket(id: bucket.id, to: newName) {
            case .renamed:
                errorMessage = nil
            case .duplicateName:
                errorMessage = "A bucket with that name already exists."
            case .invalidLength:
                errorMessage = "Bucket names must be 1–50 characters."
            case .created, .bucketNotFound:
                errorMessage = "Couldn’t rename the bucket. Please try again."
            }
        } catch {
            errorMessage = "Couldn’t save the bucket. Please try again."
        }
    }

    // MARK: - Delete decision flow (Req 9.4, 9.5)

    /// Classifies a delete and either applies it directly (empty bucket) or
    /// raises the matching confirmation prompt.
    func beginDelete(_ bucket: Bucket) {
        do {
            guard let decision = try bucketManagement.beginDelete(id: bucket.id) else { return }
            switch decision {
            case .deleteDirectly:
                try bucketManagement.confirmDelete(id: bucket.id, strategy: nil)
                errorMessage = nil
            case .requiresReassignOrDelete, .requiresConfirmDeleteItems:
                pendingDelete = PendingDelete(id: bucket.id, bucket: bucket, decision: decision)
            }
        } catch {
            errorMessage = "Couldn’t delete the bucket. Please try again."
        }
    }

    /// Applies a confirmed delete with the chosen strategy (Req 9.4, 9.5).
    func confirmDelete(_ pending: PendingDelete, strategy: BucketDeletionStrategy) {
        do {
            try bucketManagement.confirmDelete(id: pending.id, strategy: strategy)
            errorMessage = nil
        } catch {
            errorMessage = "Couldn’t delete the bucket. Please try again."
        }
        pendingDelete = nil
    }

    /// Cancels a pending delete prompt without changing anything.
    func cancelDelete() {
        pendingDelete = nil
    }

    func dismissError() {
        errorMessage = nil
    }

    // MARK: - Helpers

    private func currentAccountId() async -> String {
        if let authService, let account = await authService.currentAccount {
            return account.id
        }
        return Self.localAccountId
    }
}
