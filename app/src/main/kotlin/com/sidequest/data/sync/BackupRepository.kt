package com.sidequest.data.sync

import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.dao.ActionPlanDao
import com.sidequest.data.local.dao.BucketDao
import com.sidequest.data.local.entity.toActionItems
import com.sidequest.data.local.entity.toActionPlans
import com.sidequest.data.local.entity.toBuckets
import com.sidequest.data.local.entity.toEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds, uploads, and restores the account's planner snapshot
 * ([BackupSnapshot]). It reads/writes the local Room store directly and talks to
 * [BackupApi]; it is invoked only from the background [BackupWorker], so it
 * never sits on the UX path.
 *
 * Tombstoned rows are included (via the DAO `getAll`) so deletions also survive
 * a restore. Restore upserts, so it merges into whatever is already local.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val bucketDao: BucketDao,
    private val actionItemDao: ActionItemDao,
    private val actionPlanDao: ActionPlanDao,
    private val backupApi: BackupApi,
    private val deviceIdentity: DeviceIdentity,
) {
    /** True when there is no local planner data yet (a fresh install). */
    suspend fun isLocalEmpty(): Boolean =
        bucketDao.getAll().isEmpty() && actionItemDao.getAll().isEmpty()

    /** Snapshots all local planner data into a portable [BackupSnapshot]. */
    suspend fun snapshot(): BackupSnapshot {
        val items = actionItemDao.getAll().toActionItems()
        val itemIds = items.map { it.id }.toSet()
        val plans = actionPlanDao.getAll().toActionPlans().filter { it.actionItemId in itemIds }
        return BackupSnapshot(
            buckets = bucketDao.getAll().toBuckets(),
            actionItems = items,
            actionPlans = plans,
        )
    }

    /** Uploads the current snapshot. Returns true on success. */
    suspend fun upload(): Boolean = runCatching {
        backupApi.put(snapshot(), deviceIdentity.deviceId()).isSuccessful
    }.getOrDefault(false)

    /**
     * Restores the server snapshot into the local store. Returns true when a
     * snapshot existed and was applied; false when the server had none (204) or
     * the call failed.
     */
    suspend fun restore(): Boolean = runCatching {
        val response = backupApi.get()
        if (response.code() != 200) return false
        val snapshot = response.body() ?: return false
        if (snapshot.buckets.isNotEmpty()) {
            bucketDao.upsertAll(snapshot.buckets.map { it.toEntity() })
        }
        if (snapshot.actionItems.isNotEmpty()) {
            actionItemDao.upsertAll(snapshot.actionItems.map { it.toEntity() })
        }
        if (snapshot.actionPlans.isNotEmpty()) {
            actionPlanDao.upsertAll(snapshot.actionPlans.map { it.toEntity() })
        }
        true
    }.getOrDefault(false)
}
