import Foundation

// MARK: - Bucket-name validation (Req 9.2, 9.3)
//
// Pure, portable bucket-name validation. Mirrors the Android client's
// `com.sidequest.domain.bucket.BucketOperations` normalization and
// per-account uniqueness rule so the iOS Swift implementation produces
// field-by-field equivalent results (Req 3.3, cross-implementation
// equivalence validated by task 4.19; reused sibling Property 5).
//
// Scope of this task (4.1):
//   * Per-account uniqueness after normalization (trim + case-insensitive)
//     (Req 9.2, reused Property 5).
//   * The 1–50 character length rule on the trimmed name (Req 9.3,
//     Property 19).
//
// The validator is a free function (no I/O): it takes the candidate name plus
// the existing buckets for the account and returns a result that distinguishes
// the failure reasons so the UI (task 7) can show the right message. The
// reassign/delete decision for non-empty buckets is handled separately by
// task 7.1.

/// The outcome of validating a candidate bucket name for create or rename
/// (Req 9.2, 9.3). The failure cases are distinguished so the UI can show the
/// matching message; ``valid`` carries the trimmed name that should be stored
/// (matching the Android behavior of persisting the user's casing, trimmed).
public enum BucketNameValidation: Equatable {

    /// The name is acceptable. `normalizedName` is the candidate trimmed of
    /// surrounding whitespace (user casing preserved) — the value to persist.
    case valid(normalizedName: String)

    /// The trimmed name is empty / whitespace-only or longer than
    /// ``Domain/maxBucketNameLength`` characters (Req 9.3).
    case invalidLength

    /// After normalization (trim + case-insensitive) the name matches an
    /// existing, non-deleted bucket for the same account (Req 9.2).
    case duplicateName
}

extension Domain {

    /// The minimum allowed bucket-name length, measured on the trimmed name
    /// (Req 9.3).
    public static let minBucketNameLength = 1

    /// The maximum allowed bucket-name length, measured on the trimmed name
    /// (Req 9.3).
    public static let maxBucketNameLength = 50

    /// Normalizes a bucket name for uniqueness comparison: trims surrounding
    /// whitespace and lowercases so comparison is case-insensitive, so
    /// "Travel", "travel ", and " TRAVEL" are treated as the same name
    /// (Req 9.2). Mirrors the Android `BucketOperations.normalizeName`.
    public static func normalizeBucketName(_ name: String) -> String {
        name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    /// Validates a candidate bucket `name` for an account against `existing`
    /// buckets (Req 9.2, 9.3). Pure and total: it never mutates its inputs and
    /// never throws for any input.
    ///
    /// Checks are applied in order:
    /// 1. **Length** (Req 9.3): the name trimmed of surrounding whitespace must
    ///    contain between ``minBucketNameLength`` and ``maxBucketNameLength``
    ///    characters inclusive; otherwise ``BucketNameValidation/invalidLength``.
    /// 2. **Uniqueness** (Req 9.2): no non-deleted bucket belonging to
    ///    `accountId` may share the same normalized name; otherwise
    ///    ``BucketNameValidation/duplicateName``. Tombstoned (deleted) buckets
    ///    do not reserve their name.
    ///
    /// - Parameters:
    ///   - name: The candidate name as entered by the user (untrimmed).
    ///   - accountId: The account the bucket belongs to; only buckets for this
    ///     account participate in the uniqueness check (Req 9.2).
    ///   - existing: The buckets to check against (typically all buckets in the
    ///     local store).
    ///   - excludingBucketId: When renaming, the id of the bucket being renamed
    ///     so keeping its own name (or changing only its casing) is not treated
    ///     as a self-collision. Pass `nil` when creating.
    /// - Returns: ``BucketNameValidation/valid(normalizedName:)`` carrying the
    ///   trimmed name to persist, or a failure case describing why the name was
    ///   rejected.
    public static func validateBucketName(
        _ name: String,
        accountId: String,
        existing: [Bucket],
        excludingBucketId: String? = nil
    ) -> BucketNameValidation {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)

        guard trimmed.count >= minBucketNameLength,
              trimmed.count <= maxBucketNameLength else {
            return .invalidLength
        }

        guard isBucketNameAvailable(
            trimmed,
            accountId: accountId,
            existing: existing,
            excludingBucketId: excludingBucketId
        ) else {
            return .duplicateName
        }

        return .valid(normalizedName: trimmed)
    }

    /// Returns `true` when `name` is available for `accountId` — that is, no
    /// non-deleted bucket belonging to that account has the same normalized
    /// name (Req 9.2). `excludingBucketId` lets a rename ignore the bucket being
    /// renamed so keeping its own name (or only changing case) is not a
    /// self-collision. Mirrors the Android `BucketOperations.isNameAvailable`.
    public static func isBucketNameAvailable(
        _ name: String,
        accountId: String,
        existing: [Bucket],
        excludingBucketId: String? = nil
    ) -> Bool {
        let normalized = normalizeBucketName(name)
        return !existing.contains { bucket in
            bucket.accountId == accountId
                && !bucket.sync.deleted
                && bucket.id != excludingBucketId
                && normalizeBucketName(bucket.name) == normalized
        }
    }
}
