package com.sidequest

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sidequest.data.seed.DefaultBucketSeeder
import com.sidequest.data.seed.PreviewSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. Annotated with [HiltAndroidApp] so Hilt can generate
 * the dependency graph for the app.
 *
 * Implements [Configuration.Provider] so WorkManager initializes on demand with
 * a [HiltWorkerFactory]. That factory lets Hilt construct `@HiltWorker` workers
 * (e.g. [com.sidequest.data.preview.PreviewFetchWorker]) with their injected
 * dependencies. The default `WorkManagerInitializer` is removed in the manifest
 * (via `androidx.startup`) so this custom configuration is used instead.
 *
 * On first launch it seeds starter content: debug builds get full preview data
 * (see [PreviewSeeder]) for exploration, while release builds get the default
 * starter buckets only (see [DefaultBucketSeeder]) so new users open to a
 * populated board with no fake tasks. Both seeds are idempotent.
 */
@HiltAndroidApp
class SideQuestApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var previewSeeder: PreviewSeeder

    @Inject
    lateinit var defaultBucketSeeder: DefaultBucketSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Seed starter content on first launch. Debug builds get the full
        // preview data (buckets + sample quests) for exploration; release builds
        // get the default buckets only (no fake tasks), so new users open to a
        // populated "Your Quests" carousel instead of an empty board. Both are
        // idempotent and run at most once.
        appScope.launch {
            if (BuildConfig.DEBUG) {
                previewSeeder.seedIfEmpty()
            } else {
                defaultBucketSeeder.seedIfEmpty()
            }
        }
        // Kick off background backup/restore (provisions a silent account,
        // restores on a fresh install, uploads the latest snapshot). Runs off
        // the UX path and is fail-soft, so the app stays instant.
        com.sidequest.data.sync.BackupWorker.schedule(this)
    }
}
