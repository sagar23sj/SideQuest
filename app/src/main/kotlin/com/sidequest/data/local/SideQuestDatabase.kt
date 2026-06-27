package com.sidequest.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sidequest.data.local.converters.Converters
import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.dao.ActionPlanDao
import com.sidequest.data.local.dao.BucketDao
import com.sidequest.data.local.dao.VoiceJournalDao
import com.sidequest.data.local.entity.ActionItemEntity
import com.sidequest.data.local.entity.ActionPlanEntity
import com.sidequest.data.local.entity.BucketEntity
import com.sidequest.data.local.entity.VoiceJournalEntryEntity

/**
 * The on-device Room database — the local source of truth for the offline-first
 * client. All display reads come from here (Req 14.2), and edits/deletes are
 * persisted through the DAOs (Req 14.3).
 *
 * Structured/sealed fields are persisted via [Converters] (e.g. the [
 * com.sidequest.domain.model.Timeframe] discriminator + payload). Version 2
 * adds the `voice_journal_entries` table for Voice_Journal_Entries (Req 10.4)
 * via [MIGRATION_1_2]; later milestones (games, leaderboards) add further
 * migrations as the schema evolves. Version 3 drops the removed wishlist/
 * shopping columns from `action_items` and `buckets` (a wishlist is now just a
 * normal user-named bucket); this is a pre-release destructive schema change.
 */
@Database(
    entities = [
        ActionItemEntity::class,
        BucketEntity::class,
        ActionPlanEntity::class,
        VoiceJournalEntryEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SideQuestDatabase : RoomDatabase() {

    abstract fun actionItemDao(): ActionItemDao

    abstract fun bucketDao(): BucketDao

    abstract fun actionPlanDao(): ActionPlanDao

    abstract fun voiceJournalDao(): VoiceJournalDao

    companion object {
        const val DATABASE_NAME = "sidequest.db"

        /**
         * Adds the `voice_journal_entries` table (Req 10.4). The column set and
         * types mirror [VoiceJournalEntryEntity] (including the embedded
         * [com.sidequest.domain.model.SyncMeta] columns and the JSON-encoded
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

        /**
         * Drops the removed wishlist/shopping columns: `isWishlistItem` and
         * `wishlist` from `action_items`, and `isShopping` from `buckets`. A
         * "wishlist" is now just a normal user-named bucket, so these columns no
         * longer exist on the entities.
         *
         * SQLite on older Android API levels does not support `ALTER TABLE DROP
         * COLUMN`, so each table is rebuilt with the new column set, data is
         * copied across, the old table is dropped, the new one renamed, and the
         * indices are recreated. Wrapped in the migration's implicit
         * transaction so a failure leaves the original tables intact.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- action_items: drop isWishlistItem + wishlist ---
                db.execSQL(
                    """
                    CREATE TABLE `action_items_new` (
                        `id` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `bucketId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `contentType` TEXT NOT NULL,
                        `sourceContent` TEXT,
                        `preview` TEXT,
                        `timeframe` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `dirty` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `action_items_new` (
                        `id`, `accountId`, `bucketId`, `title`, `description`,
                        `contentType`, `sourceContent`, `preview`, `timeframe`,
                        `status`, `createdAt`, `updatedAt`, `version`, `deleted`, `dirty`
                    )
                    SELECT
                        `id`, `accountId`, `bucketId`, `title`, `description`,
                        `contentType`, `sourceContent`, `preview`, `timeframe`,
                        `status`, `createdAt`, `updatedAt`, `version`, `deleted`, `dirty`
                    FROM `action_items`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `action_items`")
                db.execSQL("ALTER TABLE `action_items_new` RENAME TO `action_items`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_action_items_accountId` " +
                        "ON `action_items` (`accountId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_action_items_bucketId` " +
                        "ON `action_items` (`bucketId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_action_items_createdAt` " +
                        "ON `action_items` (`createdAt`)",
                )

                // --- buckets: drop isShopping ---
                db.execSQL(
                    """
                    CREATE TABLE `buckets_new` (
                        `id` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `notStartedColor` TEXT NOT NULL,
                        `inProgressColor` TEXT NOT NULL,
                        `completedColor` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `deleted` INTEGER NOT NULL,
                        `dirty` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `buckets_new` (
                        `id`, `accountId`, `name`, `notStartedColor`,
                        `inProgressColor`, `completedColor`, `updatedAt`,
                        `version`, `deleted`, `dirty`
                    )
                    SELECT
                        `id`, `accountId`, `name`, `notStartedColor`,
                        `inProgressColor`, `completedColor`, `updatedAt`,
                        `version`, `deleted`, `dirty`
                    FROM `buckets`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `buckets`")
                db.execSQL("ALTER TABLE `buckets_new` RENAME TO `buckets`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_buckets_accountId` " +
                        "ON `buckets` (`accountId`)",
                )
            }
        }

        /**
         * Adds the nullable `reminder` column to `action_items` for optional
         * per-task reminders (Req 6). The column stores the JSON-encoded
         * [com.sidequest.domain.model.TaskReminder] (or NULL when the item has
         * no reminder). A simple additive `ALTER TABLE ADD COLUMN` is safe here
         * because the column is nullable with no default.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `action_items` ADD COLUMN `reminder` TEXT")
            }
        }
    }
}
