package com.actiontracker.domain.bucket

import com.actiontracker.domain.model.Bucket

/**
 * Outcome of a bucket create or rename operation.
 *
 * Per-account bucket names must be unique after normalization (trim +
 * case-insensitive). When a candidate name collides with an existing bucket the
 * operation is rejected with [DuplicateName] carrying a user-facing "name in
 * use" message (Req 2.6); otherwise the new or updated [Bucket] is returned.
 */
sealed interface BucketResult {

    /** A new bucket was created (Req 2.1, 2.2). [bucket] carries the trimmed name. */
    data class Created(val bucket: Bucket) : BucketResult

    /** An existing bucket was renamed (Req 2.3). [bucket] carries the trimmed new name. */
    data class Renamed(val bucket: Bucket) : BucketResult

    /**
     * The candidate name already exists for the account (Req 2.6). [message] is
     * suitable for display to the user.
     */
    data class DuplicateName(val message: String) : BucketResult
}
