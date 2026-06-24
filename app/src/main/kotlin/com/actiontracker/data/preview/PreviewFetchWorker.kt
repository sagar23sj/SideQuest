package com.actiontracker.data.preview

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.actiontracker.data.local.dao.ActionItemDao
import com.actiontracker.data.local.entity.toDomain
import com.actiontracker.data.local.entity.toEntity
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.preview.PreviewMerge
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background job that enriches a captured LINK Action_Item with a link preview
 * (Req 1a.3, 1a.5).
 *
 * Preview enrichment runs off the capture critical path: [CaptureRepository]
 * persists the Action_Item immediately and enqueues this worker, so capture
 * completes without waiting on the network (Req 1a.5). When the worker finishes
 * it upserts the merged item back into Room; because the Board reads from
 * Room-backed [kotlinx.coroutines.flow.Flow]s, the title and thumbnail appear on
 * the row reactively once the row changes (Req 1a.3 data path).
 *
 * The worker is a [HiltWorker] so its [PreviewService] and [ActionItemDao]
 * dependencies are injected through the app's [androidx.hilt.work.HiltWorkerFactory];
 * the [Context] and [WorkerParameters] are supplied by WorkManager via
 * [Assisted] injection.
 */
@HiltWorker
class PreviewFetchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val previewService: PreviewService,
    private val actionItemDao: ActionItemDao,
) : CoroutineWorker(appContext, params) {

    /**
     * Loads the target Action_Item, fetches its preview, and persists the merged
     * result.
     *
     * Returns [Result.success] without doing any work when the item is missing,
     * is not a LINK item, or has no source URL — there is nothing to enrich, and
     * the captured item already stores its raw content. The pure
     * [PreviewService] contract returns a [com.actiontracker.domain.preview.PreviewResult]
     * (Success/Failure/Timeout) rather than throwing, and [PreviewMerge] maps
     * every outcome to a well-defined preview (resolved or raw-link fallback),
     * so a single [Result.success] covers both enriched and fallback cases
     * (Req 1a.4, 1a.5).
     */
    override suspend fun doWork(): Result {
        val actionItemId = inputData.getString(KEY_ACTION_ITEM_ID)
            ?: return Result.success()

        val entity = actionItemDao.getById(actionItemId) ?: return Result.success()
        val item = entity.toDomain()

        if (item.contentType != ContentType.LINK) return Result.success()

        // Prefer an explicit URL from the input, falling back to the item's
        // stored source content (the raw link for LINK items).
        val url = inputData.getString(KEY_URL)?.takeIf { it.isNotBlank() }
            ?: item.sourceContent?.takeIf { it.isNotBlank() }
            ?: return Result.success()

        val result = previewService.fetchPreview(url)

        val merged = PreviewMerge.mergeInto(item, result, url)
        // Bump sync metadata for the enrichment write so the offline-first sync
        // layer pushes the now-enriched item.
        val enriched = merged.copy(
            sync = merged.sync.copy(
                updatedAt = System.currentTimeMillis(),
                version = merged.sync.version + 1,
                dirty = true,
            ),
        )

        actionItemDao.upsert(enriched.toEntity())
        return Result.success()
    }

    companion object {
        /** Input-data key carrying the id of the Action_Item to enrich. */
        const val KEY_ACTION_ITEM_ID = "action_item_id"

        /** Optional input-data key carrying the URL to fetch a preview for. */
        const val KEY_URL = "url"

        /** Unique work name prefix so per-item preview work can be deduplicated. */
        const val WORK_NAME_PREFIX = "preview_fetch_"
    }
}
