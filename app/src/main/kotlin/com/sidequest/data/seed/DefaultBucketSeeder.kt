package com.sidequest.data.seed

import com.sidequest.data.local.dao.BucketDao
import com.sidequest.data.local.entity.toEntity
import com.sidequest.domain.model.Bucket
import com.sidequest.domain.model.SyncMeta
import com.sidequest.ui.capture.CurrentAccountProvider
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the [DEFAULT_BUCKETS] starter buckets on first launch so a brand-new
 * user opens to a populated "Your Quests" carousel of topical cover cards
 * instead of an empty board — without any sample tasks to clean up.
 *
 * Unlike [PreviewSeeder], this runs in **all** builds (including release) and
 * seeds buckets only. It is idempotent: [seedIfEmpty] does nothing once any
 * bucket exists, so it runs at most once and never clobbers user data. The
 * created buckets are marked dirty so they sync to the backend like any other
 * user-created bucket.
 */
@Singleton
class DefaultBucketSeeder @Inject constructor(
    private val bucketDao: BucketDao,
) {

    private val accountId = CurrentAccountProvider.LOCAL_ACCOUNT_ID

    /** Inserts the default buckets only if the bucket table is empty. */
    suspend fun seedIfEmpty() {
        if (bucketDao.getByAccount(accountId).isNotEmpty()) return
        val now = System.currentTimeMillis()
        val buckets = DEFAULT_BUCKETS.mapIndexed { index, spec ->
            Bucket(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                name = spec.name,
                notStartedColor = spec.notStartedColor,
                inProgressColor = spec.inProgressColor,
                completedColor = spec.completedColor,
                position = index,
                sync = SyncMeta(
                    updatedAt = now,
                    version = 1,
                    deleted = false,
                    dirty = true,
                ),
            )
        }
        bucketDao.insertAll(buckets.map { it.toEntity() })
    }
}
