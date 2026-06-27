package com.sidequest.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.ContentType
import com.sidequest.domain.model.LinkPreview
import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.model.TaskReminder
import com.sidequest.domain.model.Timeframe

/**
 * Room representation of [com.sidequest.domain.model.ActionItem].
 *
 * Structured fields ([timeframe], [preview]) are persisted via the
 * [com.sidequest.data.local.converters.Converters] type converters. The
 * [SyncMeta] is embedded as flat columns (no prefix) so it round-trips with the
 * rest of the row. Indexed on [accountId], [bucketId], and [createdAt] to keep
 * board reads (grouped by bucket, ordered by creation time) efficient.
 */
@Entity(
    tableName = "action_items",
    indices = [
        Index("accountId"),
        Index("bucketId"),
        Index("createdAt"),
    ],
)
data class ActionItemEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val bucketId: String,
    val title: String,
    val description: String?,
    val contentType: ContentType,
    val sourceContent: String?,
    val preview: LinkPreview?,
    val timeframe: Timeframe,
    val status: ActionStatus,
    val createdAt: Long,
    val reminder: TaskReminder?,
    @Embedded val sync: SyncMeta,
)
