package com.sidequest.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sidequest.domain.model.SubAction
import com.sidequest.domain.model.SyncMeta

/**
 * Room representation of [com.sidequest.domain.model.ActionPlan].
 *
 * The ordered [subActions] list is stored as a single JSON column via the
 * [com.sidequest.data.local.converters.Converters] type converter. This
 * keeps the plan a self-contained row that mirrors the domain model and avoids
 * a separate child table while preserving sub-action order. Indexed on
 * [actionItemId] so a plan can be looked up by its parent item.
 */
@Entity(
    tableName = "action_plans",
    indices = [Index("actionItemId")],
)
data class ActionPlanEntity(
    @PrimaryKey val id: String,
    val actionItemId: String,
    val subActions: List<SubAction>,
    @Embedded val sync: SyncMeta,
)
