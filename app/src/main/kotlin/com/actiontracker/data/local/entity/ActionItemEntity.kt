package com.actiontracker.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.LinkPreview
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe

/**
 * Room representation of [com.actiontracker.domain.model.ActionItem].
 *
 * Structured fields ([timeframe], [preview]) are persisted via the
 * [com.actiontracker.data.local.converters.Converters] type converters. The
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
    @Embedded val sync: SyncMeta,
)
