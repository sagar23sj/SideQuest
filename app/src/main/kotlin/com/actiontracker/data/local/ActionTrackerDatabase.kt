package com.actiontracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.actiontracker.data.local.converters.Converters
import com.actiontracker.data.local.dao.ActionItemDao
import com.actiontracker.data.local.dao.ActionPlanDao
import com.actiontracker.data.local.dao.BucketDao
import com.actiontracker.data.local.dao.VoiceJournalDao
import com.actiontracker.data.local.entity.ActionItemEntity
import com.actiontracker.data.local.entity.ActionPlanEntity
import com.actiontracker.data.local.entity.BucketEntity
import com.actiontracker.data.local.entity.VoiceJournalEntryEntity

/**
 * The on-device Room database — the local source of truth for the offline-first
 * client. All display reads come from here (Req 14.2), and edits/deletes are
 * persisted through the DAOs (Req 14.3).
 *
 * Structured/sealed fields are persisted via [Converters] (e.g. the [
 * com.actiontracker.domain.model.Timeframe] discriminator + payload). Version 2
 * adds the `voice_journal_entries` table for Voice_Journal_Entries (Req 10.4)
 * via [MIGRATION_1_2]; later milestones (games, leaderboards) add further
 * migrations as the schema evolves.
 */
@Database(
    entities = [
        ActionItemEntity::class,
        BucketEntity::class,
        ActionPlanEntity::class,
        VoiceJournalEntryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ActionTrackerDatabase : RoomDatabase() {

    abstract fun actionItemDao(): ActionItemDao

    abstract fun bucketDao(): BucketDao

    abstract fun actionPlanDao(): ActionPlanDao

    abstract fun voiceJournalDao(): VoiceJournalDao

    companion object {
        const val DATABASE_NAME = "action_tracker.db"

        /**
         * Adds the `voice_journal_entries` table (Req 10.4). The column set and
         * types mirror [VoiceJournalEntryEntity] (including the embedded
         * [com.actiontracker.domain.model.SyncMeta] columns and the JSON-encoded
         * `extractedActionItemIds` text column). A non-destructive migration
         * preserves any previously captured Action_Items and Buckets.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `voice_journal_entries` (
                        `id` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `audioRef` TEXT NOT NULL,
                        `transcript` TEXT,
                        `transcriptionFailed` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `extractedActionItemIds` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `dirty` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_voice_journal_entries_accountId` " +
                        "ON `voice_journal_entries` (`accountId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_voice_journal_entries_createdAt` " +
                        "ON `voice_journal_entries` (`createdAt`)",
                )
            }
        }
    }
}
