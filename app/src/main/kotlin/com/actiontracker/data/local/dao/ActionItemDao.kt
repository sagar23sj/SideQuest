package com.actiontracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.actiontracker.data.local.entity.ActionItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for [ActionItemEntity].
 *
 * Reactive reads are exposed as [Flow] so the UI updates whenever the underlying
 * rows change. Reads exclude tombstoned rows (`deleted = 0`); writes cover
 * insert, update, upsert (Req 14.3 edits) and delete (both hard delete and
 * tombstone) so persistence reflects edits and deletes.
 */
@Dao
interface ActionItemDao {

    @Query("SELECT * FROM action_items WHERE deleted = 0 ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ActionItemEntity>>

    @Query("SELECT * FROM action_items WHERE accountId = :accountId AND deleted = 0 ORDER BY createdAt ASC")
    fun observeByAccount(accountId: String): Flow<List<ActionItemEntity>>

    @Query("SELECT * FROM action_items WHERE bucketId = :bucketId AND deleted = 0 ORDER BY createdAt ASC")
    fun observeByBucket(bucketId: String): Flow<List<ActionItemEntity>>

    @Query("SELECT * FROM action_items WHERE id = :id")
    fun observeById(id: String): Flow<ActionItemEntity?>

    @Query("SELECT * FROM action_items WHERE id = :id")
    suspend fun getById(id: String): ActionItemEntity?

    @Query("SELECT * FROM action_items ORDER BY createdAt ASC")
    suspend fun getAll(): List<ActionItemEntity>

    @Query("SELECT * FROM action_items WHERE bucketId = :bucketId AND deleted = 0 ORDER BY createdAt ASC")
    suspend fun getByBucket(bucketId: String): List<ActionItemEntity>

    @Query("SELECT COUNT(*) FROM action_items WHERE bucketId = :bucketId AND deleted = 0")
    suspend fun countByBucket(bucketId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ActionItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ActionItemEntity>)

    @Update
    suspend fun update(item: ActionItemEntity)

    @Upsert
    suspend fun upsert(item: ActionItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<ActionItemEntity>)

    @Delete
    suspend fun delete(item: ActionItemEntity)

    @Query("DELETE FROM action_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM action_items")
    suspend fun clear()
}
