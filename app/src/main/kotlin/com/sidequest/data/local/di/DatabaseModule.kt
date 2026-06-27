package com.sidequest.data.local.di

import android.content.Context
import androidx.room.Room
import com.sidequest.data.local.SideQuestDatabase
import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.dao.ActionPlanDao
import com.sidequest.data.local.dao.BucketDao
import com.sidequest.data.local.dao.VoiceJournalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the singleton [SideQuestDatabase] and its DAOs
 * for injection into repositories and other singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): SideQuestDatabase =
        Room.databaseBuilder(
            context,
            SideQuestDatabase::class.java,
            SideQuestDatabase.DATABASE_NAME,
        ).addMigrations(
            SideQuestDatabase.MIGRATION_1_2,
            SideQuestDatabase.MIGRATION_2_3,
            SideQuestDatabase.MIGRATION_3_4,
        )
            .build()

    @Provides
    fun provideActionItemDao(database: SideQuestDatabase): ActionItemDao =
        database.actionItemDao()

    @Provides
    fun provideBucketDao(database: SideQuestDatabase): BucketDao =
        database.bucketDao()

    @Provides
    fun provideActionPlanDao(database: SideQuestDatabase): ActionPlanDao =
        database.actionPlanDao()

    @Provides
    fun provideVoiceJournalDao(database: SideQuestDatabase): VoiceJournalDao =
        database.voiceJournalDao()
}
