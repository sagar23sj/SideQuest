package com.sidequest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.sidequest.data.local.entity.BucketEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for [BucketEntity].
 *
 * Reactive reads are exposed as [Flow]; tombstoned rows are excluded. Writes
 * cover insert, update, upsert and delete so bucket CRUD (Req 2.x) and edit
 * persistence (Req 14.3) are supported.
 */
@Dao
interface BucketDao {

    @Query("SELECT * FROM buckets WHERE deleted = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<BucketEntity>>

    @Query("SELECT * FROM buckets WHERE accountId = :accountId AND deleted = 0 ORDER BY name ASC")
    fun observeByAccount(accountId: String): Flow<List<BucketEntity>>

    @Query("SELECT * FROM buckets WHERE id = :id")
    fun observeById(id: String): Flow<BucketEntity?>

    @Query("SELECT * FROM buckets WHERE id = :id")
    suspend fun getById(id: String): BucketEntity?

    @Query("SELECT * FROM buckets WHERE accountId = :accountId AND deleted = 0")
    suspend fun getByAccount(accountId: String): List<BucketEntity>

    @Query("SELECT * FROM buckets ORDER BY name ASC")
    suspend fun getAll(): List<BucketEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bucket: BucketEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(buckets: List<BucketEntity>)

    @Update
    suspend fun update(bucket: BucketEntity)

    @Upsert
    suspend fun upsert(bucket: BucketEntity)

    @Upsert
    suspend fun upsertAll(buckets: List<BucketEntity>)

    @Delete
    suspend fun delete(bucket: BucketEntity)

    @Query("DELETE FROM buckets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM buckets")
    suspend fun clear()
}
