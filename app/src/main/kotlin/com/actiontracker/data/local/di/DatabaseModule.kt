package com.actiontracker.data.local.di

import android.content.Context
import androidx.room.Room
import com.actiontracker.data.local.ActionTrackerDatabase
import com.actiontracker.data.local.dao.ActionItemDao
import com.actiontracker.data.local.dao.ActionPlanDao
import com.actiontracker.data.local.dao.BucketDao
import com.actiontracker.data.local.dao.VoiceJournalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the singleton [ActionTrackerDatabase] and its DAOs
 * for injection into repositories and other singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): ActionTrackerDatabase =
        Room.databaseBuilder(
            context,
            ActionTrackerDatabase::class.java,
            ActionTrackerDatabase.DATABASE_NAME,
        ).addMigrations(
            ActionTrackerDatabase.MIGRATION_1_2,
            ActionTrackerDatabase.MIGRATION_2_3,
        )
            .build()

    @Provides
    fun provideActionItemDao(database: ActionTrackerDatabase): ActionItemDao =
        database.actionItemDao()

    @Provides
    fun provideBucketDao(database: ActionTrackerDatabase): BucketDao =
        database.bucketDao()

    @Provides
    fun provideActionPlanDao(database: ActionTrackerDatabase): ActionPlanDao =
        database.actionPlanDao()

    @Provides
    fun provideVoiceJournalDao(database: ActionTrackerDatabase): VoiceJournalDao =
        database.voiceJournalDao()
}
