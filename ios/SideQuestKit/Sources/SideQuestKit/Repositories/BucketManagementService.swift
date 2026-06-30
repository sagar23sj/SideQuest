import Foundation

// MARK: - Bucket management service (task 7.1)
//
// A UI-free coordinator that ties together the pure bucket-name validation
// (task 4.1, `Domain.validateBucketName`), the pure delete decision/accounting
// (`Domain.bucketDeleteDecision` / `Domain.applyBucketDeletion`), and the
// `BucketRepository` / `ActionItemRepository` (task 6.1). It lives in the shared
// `SideQuestKit` module — no SwiftUI dependency — so both the main app and the
// Share Extension can drive bucket CRUD, and so the flow is host-testable.
//
// Responsibilities (Req 9.1, 9.4, 9.5):
//   * Create / rename buckets, rejecting invalid lengths and duplicate names
//     using the task-4.1 validation rather than reimplementing it.
//   * Delete buckets through an explicit two-step decision:
//       1. `beginDelete` classifies the delete (direct / reassign-or-delete /
//          confirm-delete-items) WITHOUT mutating anything.
//       2. `confirmDelete` applies the user's confirmed choice — reassigning
//          contained items to a target bucket or deleting them, then tombstoning
//          the bucket. Empty buckets delete directly.

/// Outcome of a create or rename command. Distinguishes the two validation
/// failures (Req 9.2, 9.3) so a view model can show the matching message, and
/// `bucketNotFound` for a rename targeting an unknown/tombstoned bucket.
public enum BucketCommandResult: Equatable {

    /// A new bucket was created; `bucket` carries the trimmed name and the
    /// stored sync metadata (Req 9.1).
    case created(Bucket)

    /// An existing bucket was renamed; `bucket` carries the trimmed new name
    /// and the bumped sync metadata (Req 9.1).
    case renamed(Bucket)

    /// The trimmed name is empty/whitespace-only or longer than
    /// ``Domain/maxBucketNameLength`` characters (Req 9.3).
    case invalidLength

    /// The normalized name already belongs to a live bucket for the account
    /// (Req 9.2). Existing buckets are left unchanged.
    case duplicateName

    /// A rename referenced a bucket id that no live bucket matches.
    case bucketNotFound
}

/// Errors raised while *applying* a confirmed bucket deletion. Validation
/// failures for create/rename surface as ``BucketCommandResult`` cases instead;
/// these cover the apply step where there is no user-facing alternative.
public enum BucketManagementError: Error, Equatable {

    /// `confirmDelete` was called for a non-empty bucket without a strategy,
    /// i.e. before the user confirmed reassign-or-delete (Req 9.4, 9.5).
    case confirmationRequired

    /// A reassign strategy named a target that is the bucket being deleted or
    /// is not a live bucket for the same account.
    case invalidReassignTarget
}

/// UI-free coordinator for bucket CRUD and the delete-decision flow (task 7.1).
public final class BucketManagementService {

    private let buckets: BucketRepository
    private let items: ActionItemRepository

    public init(buckets: BucketRepository, items: ActionItemRepository) {
        self.buckets = buckets
        self.items = items
    }

    // MARK: - Create / rename (Req 9.1, 9.2, 9.3)

    /// Validates and creates `candidate` (Req 9.1). The candidate's
    /// `accountId`, colors, and (caller-generated) `id` are used as supplied;
    /// the name is validated with the task-4.1 rules against the live buckets.
    ///
    /// - Returns: ``BucketCommandResult/created(_:)`` with the persisted bucket
    ///   (trimmed name, fresh sync metadata) on success; otherwise
    ///   ``BucketCommandResult/invalidLength`` (Req 9.3) or
    ///   ``BucketCommandResult/duplicateName`` (Req 9.2) with the store
    ///   unchanged.
    /// - Throws: ``RepositoryError/notSaved(underlying:)`` if the commit fails.
    public func createBucket(_ candidate: Bucket) throws -> BucketCommandResult {
        let existing = try buckets.fetchAll()
        switch Domain.validateBucketName(
            candidate.name,
            accountId: candidate.accountId,
            existing: existing,
            excludingBucketId: nil
        ) {
        case .invalidLength:
            return .invalidLength
        case .duplicateName:
            return .duplicateName
        case .valid(let normalizedName):
            var toStore = candidate
            toStore.name = normalizedName
            let stored = try buckets.create(toStore)
            return .created(stored)
        }
    }

    /// Validates and renames the bucket `id` to `newName` (Req 9.1). The bucket
    /// being renamed is excluded from the uniqueness check so it may keep its
    /// own name or change only its casing.
    ///
    /// - Returns: ``BucketCommandResult/renamed(_:)`` on success; otherwise
    ///   ``BucketCommandResult/invalidLength`` (Req 9.3),
    ///   ``BucketCommandResult/duplicateName`` (Req 9.2), or
    ///   ``BucketCommandResult/bucketNotFound`` — each leaving the store
    ///   unchanged.
    /// - Throws: ``RepositoryError/notSaved(underlying:)`` if the commit fails.
    public func renameBucket(id: String, to newName: String) throws -> BucketCommandResult {
        let existing = try buckets.fetchAll()
        guard let current = existing.first(where: { $0.id == id }) else {
            return .bucketNotFound
        }
        switch Domain.validateBucketName(
            newName,
            accountId: current.accountId,
            existing: existing,
            excludingBucketId: id
        ) {
        case .invalidLength:
            return .invalidLength
        case .duplicateName:
            return .duplicateName
        case .valid(let normalizedName):
            var updated = current
            updated.name = normalizedName
            let stored = try buckets.update(updated)
            return .renamed(stored)
        }
    }

    // MARK: - Delete decision (Req 9.4, 9.5)

    /// Classifies what deleting the bucket `id` requires, reading the live
    /// buckets and items from the store. Mutates nothing — the caller shows the
    /// matching prompt and then calls ``confirmDelete(id:strategy:)``.
    ///
    /// - Returns: `nil` if no live bucket matches `id`; otherwise the
    ///   ``BucketDeleteDecision`` (direct delete, reassign-or-delete, or
    ///   confirm-delete-items).
    public func beginDelete(id: String) throws -> BucketDeleteDecision? {
        let liveBuckets = try buckets.fetchAll()
        let liveItems = try items.fetchAll()
        return Domain.bucketDeleteDecision(
            bucketId: id,
            buckets: liveBuckets,
            items: liveItems
        )
    }

    /// Applies a confirmed bucket deletion (Req 9.4, 9.5). Call only after the
    /// user has confirmed the prompt returned by ``beginDelete(id:)``.
    ///
    /// Behavior by case:
    /// - **Empty bucket**: `strategy` is ignored; the bucket is tombstoned
    ///   directly.
    /// - **Non-empty, `strategy == .reassign(target)`**: every contained item
    ///   is moved to `target` (its `bucketId` updated, re-marked dirty so the
    ///   move syncs), then the bucket is tombstoned (Req 9.4).
    /// - **Non-empty, `strategy == .deleteItems`**: every contained item is
    ///   tombstoned, then the bucket is tombstoned (Req 9.4, 9.5).
    /// - **Non-empty, `strategy == nil`**: throws
    ///   ``BucketManagementError/confirmationRequired`` — the choice must be
    ///   confirmed first.
    ///
    /// - Returns: the ``BucketDeletionOutcome`` describing the item changes
    ///   that were applied (empty lists for a direct delete).
    /// - Throws: ``BucketManagementError/confirmationRequired`` or
    ///   ``BucketManagementError/invalidReassignTarget`` for an invalid request;
    ///   ``RepositoryError/notSaved(underlying:)`` if a commit fails.
    @discardableResult
    public func confirmDelete(
        id: String,
        strategy: BucketDeletionStrategy?
    ) throws -> BucketDeletionOutcome {
        let liveBuckets = try buckets.fetchAll()
        let liveItems = try items.fetchAll()
        let contained = liveItems.filter { $0.bucketId == id }

        // Empty bucket → delete directly, ignoring any strategy (Req 9.4/9.5
        // prompts only apply to non-empty buckets).
        guard !contained.isEmpty else {
            try buckets.delete(id: id)
            return BucketDeletionOutcome(items: liveItems, reassignedItemIds: [], deletedItemIds: [])
        }

        guard let strategy else {
            throw BucketManagementError.confirmationRequired
        }

        if case .reassign(let targetBucketId) = strategy {
            let isValidTarget = targetBucketId != id
                && liveBuckets.contains { $0.id == targetBucketId && !$0.sync.deleted }
            guard isValidTarget else {
                throw BucketManagementError.invalidReassignTarget
            }
        }

        let outcome = Domain.applyBucketDeletion(
            items: liveItems,
            bucketId: id,
            strategy: strategy
        )

        // Persist the item changes first so the bucket is only tombstoned once
        // its contents have been handled.
        switch strategy {
        case .reassign(let targetBucketId):
            for item in contained {
                var moved = item
                moved.bucketId = targetBucketId
                try items.update(moved)
            }
        case .deleteItems:
            for item in contained {
                try items.delete(id: item.id)
            }
        }

        try buckets.delete(id: id)
        return outcome
    }
}
