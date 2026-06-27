package com.sidequest

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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
 * On debug builds it also seeds the local database with preview data on first
 * launch (see [PreviewSeeder]) so the whole app can be explored in a working
 * state. The seed is idempotent and never runs in release builds.
 */
@HiltAndroidApp
class SideQuestApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var previewSeeder: PreviewSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Preview data for local exploration; debug-only and idempotent so it
        // runs at most once and never affects release builds or user data.
        if (BuildConfig.DEBUG) {
            appScope.launch {
                previewSeeder.seedIfEmpty()
            }
        }
    }
}
