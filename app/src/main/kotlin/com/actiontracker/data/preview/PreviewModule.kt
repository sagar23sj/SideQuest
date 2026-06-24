package com.actiontracker.data.preview

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Hilt bindings for link-preview enrichment (Req 1a).
 *
 * Binds the [PreviewService] seam to its [OkHttpPreviewService] implementation,
 * binds the [PreviewEnqueuer] seam to its [WorkManagerPreviewEnqueuer], and
 * provides the shared [OkHttpClient] and [WorkManager] those depend on. Per-call
 * timeouts are applied by the service from its `timeoutMs` argument (Req 1a.5),
 * so this base client only needs sensible connect/read defaults.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreviewModule {

    @Binds
    @Singleton
    abstract fun bindPreviewService(impl: OkHttpPreviewService): PreviewService

    @Binds
    @Singleton
    abstract fun bindPreviewEnqueuer(impl: WorkManagerPreviewEnqueuer): PreviewEnqueuer

    companion object {

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

        @Provides
        @Singleton
        fun provideWorkManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)
    }
}
