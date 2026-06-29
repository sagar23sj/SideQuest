package com.sidequest.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background worker that backs up planner data to the server and restores it on
 * a fresh install — entirely off the UX path (Req: never block the local-first
 * experience on the backend).
 *
 * On each run it:
 *  1. ensures a silent backup account exists (provisioning it if needed);
 *  2. on a fresh install (no local data) pulls and restores the server snapshot;
 *  3. uploads the current local snapshot so the server stays current.
 *
 * When offline or the account can't be provisioned, it returns [Result.retry]
 * so WorkManager retries with backoff — the user never waits and no data is lost.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val accountBootstrap: AccountBootstrap,
    private val backupRepository: BackupRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // No account yet (offline at first launch) → retry later; nothing lost.
        if (!accountBootstrap.ensureAccount()) return Result.retry()

        return runCatching {
            if (backupRepository.isLocalEmpty()) {
                // Fresh install: pull whatever the server has for this device.
                backupRepository.restore()
            }
            // Keep the server snapshot current with local state.
            backupRepository.upload()
            Result.success()
        }.getOrDefault(Result.retry())
    }

    companion object {
        private const val ONE_TIME_WORK = "backup_sync_now"
        private const val PERIODIC_WORK = "backup_sync_periodic"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Kicks off a one-time backup pass (e.g. when the app is opened) and
         * ensures the periodic pass is scheduled. Safe to call repeatedly.
         */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)

            val now = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            wm.enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, now)

            val periodic = PeriodicWorkRequestBuilder<BackupWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)
        }
    }
}
