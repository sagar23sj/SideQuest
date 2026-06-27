package com.sidequest.domain.capture

import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.model.Timeframe

/**
 * Pure confirm-capture logic: building the Action_Item a user gets when they
 * confirm a [CaptureDraft] with a selected bucket and timeframe (Req 1.5, 3.4).
 *
 * Lives in `:domain` so it is portable and validated by the shared Correctness
 * Properties (Property 2) without any Android/Room dependency. The app's capture
 * repository reads the selected bucket, calls [buildActionItem], and persists
 * the result; the clock and id generator are injected so the computation stays
 * total, deterministic, and testable.
 */
object CaptureOperations {

    /**
     * Builds the [ActionItem] created when a user confirms a capture (Req 1.5).
     *
     * The new item always starts with status [ActionStatus.NOT_STARTED] and
     * stores the selected [bucketId] and [timeframe] exactly as chosen (Req 3.4),
     * which is the invariant exercised by Property 2. The remaining content
     * fields are carried over from [draft]. Sync metadata is initialized as a
     * fresh, dirty record (version 1, not deleted) so the offline-first sync
     * layer pushes the newly captured item.
     *
     * Determinism is preserved by injecting [id] and [now] rather than reading a
     * UUID source or wall clock here; callers supply real generators in
     * production and fixed values in tests.
     *
     * @param draft what was known before bucket/timeframe selection.
     * @param bucketId the bucket the user selected; stored verbatim.
     * @param timeframe the timeframe the user selected; stored verbatim. Callers
     *   are expected to have validated it via [validateTimeframe] (Req 3.2, 3.3).
     * @param id the client-generated UUID for the new item.
     * @param now epoch milliseconds used for both [ActionItem.createdAt] and the
     *   sync `updatedAt`.
     */
    fun buildActionItem(
        draft: CaptureDraft,
        bucketId: String,
        timeframe: Timeframe,
        id: String,
        now: Long,
    ): ActionItem = ActionItem(
        id = id,
        accountId = draft.accountId,
        bucketId = bucketId,
        title = draft.title,
        description = null,
        contentType = draft.contentType,
        sourceContent = draft.sourceContent,
        preview = draft.preview,
        timeframe = timeframe,
        status = ActionStatus.NOT_STARTED,
        createdAt = now,
        sync = SyncMeta(
            updatedAt = now,
            version = 1,
            deleted = false,
            dirty = true,
        ),
    )
}
