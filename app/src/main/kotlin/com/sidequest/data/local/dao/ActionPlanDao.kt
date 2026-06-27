package com.sidequest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.sidequest.data.local.entity.ActionPlanEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for [ActionPlanEntity].
 *
 * Reactive reads are exposed as [Flow]; tombstoned rows are excluded. A plan is
 * normally fetched by its parent action item. Writes cover insert, update,
 * upsert and delete so plan edits (Req 14.3) persist.
 */
@Dao
interface ActionPlanDao {

    @Query("SELECT * FROM action_plans WHERE deleted = 0")
    fun observeAll(): Flow<List<ActionPlanEntity>>

    @Query("SELECT * FROM action_plans WHERE actionItemId = :actionItemId AND deleted = 0")
    fun observeByActionItem(actionItemId: String): Flow<ActionPlanEntity?>

    @Query("SELECT * FROM action_plans WHERE id = :id")
    suspend fun getById(id: String): ActionPlanEntity?

    @Query("SELECT * FROM action_plans WHERE actionItemId = :actionItemId AND deleted = 0")
    suspend fun getByActionItem(actionItemId: String): ActionPlanEntity?

    @Query("SELECT * FROM action_plans")
    suspend fun getAll(): List<ActionPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: ActionPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<ActionPlanEntity>)

    @Update
    suspend fun update(plan: ActionPlanEntity)

    @Upsert
    suspend fun upsert(plan: ActionPlanEntity)

    @Upsert
    suspend fun upsertAll(plans: List<ActionPlanEntity>)

    @Delete
    suspend fun delete(plan: ActionPlanEntity)

    @Query("DELETE FROM action_plans WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM action_plans")
    suspend fun clear()
}
