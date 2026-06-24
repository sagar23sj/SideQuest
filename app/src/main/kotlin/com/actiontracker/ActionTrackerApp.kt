package com.actiontracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Annotated with [HiltAndroidApp] so Hilt can generate
 * the dependency graph for the app.
 *
 * Implements [Configuration.Provider] so WorkManager initializes on demand with
 * a [HiltWorkerFactory]. That factory lets Hilt construct `@HiltWorker` workers
 * (e.g. [com.actiontracker.data.preview.PreviewFetchWorker]) with their injected
 * dependencies. The default `WorkManagerInitializer` is removed in the manifest
 * (via `androidx.startup`) so this custom configuration is used instead.
 */
@HiltAndroidApp
class ActionTrackerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
