package com.actiontracker.data.preview

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules background link-preview enrichment for a captured Action_Item
 * (Req 1a.3, 1a.5).
 *
 * Extracting this seam keeps [com.actiontracker.data.repository.CaptureRepository]
 * free of a hard WorkManager dependency: capture just asks the enqueuer to
 * schedule work and returns immediately, so enqueuing never blocks the capture
 * critical path (Req 1a.5). The default [WorkManagerPreviewEnqueuer] enqueues a
 * [PreviewFetchWorker] request; tests can substitute a no-op or recording
 * implementation.
 */
interface PreviewEnqueuer {

    /**
     * Schedules a preview fetch for the LINK Action_Item identified by
     * [actionItemId], optionally carrying the [url] to fetch. Implementations
     * must return promptly (only scheduling work, not performing it) so the
     * caller's capture flow completes without blocking (Req 1a.5).
     */
    fun enqueue(actionItemId: String, url: String?)
}

/**
 * [PreviewEnqueuer] backed by [WorkManager].
 *
 * Enqueues a one-time [PreviewFetchWorker] request constrained to require
 * network connectivity (the preview fetch is a network call) and retried with
 * exponential backoff on transient failure. The work is enqueued as unique work
 * keyed on the Action_Item id ([ExistingWorkPolicy.REPLACE]) so re-capturing or
 * re-enqueuing the same item coalesces rather than piling up duplicate fetches.
 */
@Singleton
class WorkManagerPreviewEnqueuer @Inject constructor(
    private val workManager: WorkManager,
) : PreviewEnqueuer {

    override fun enqueue(actionItemId: String, url: String?) {
        val inputBuilder = Data.Builder()
            .putString(PreviewFetchWorker.KEY_ACTION_ITEM_ID, actionItemId)
        if (!url.isNullOrBlank()) {
            inputBuilder.putString(PreviewFetchWorker.KEY_URL, url)
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<PreviewFetchWorker>()
            .setInputData(inputBuilder.build())
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                DEFAULT_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()

        workManager.enqueueUniqueWork(
            PreviewFetchWorker.WORK_NAME_PREFIX + actionItemId,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val DEFAULT_BACKOFF_SECONDS = 30L
    }
}
