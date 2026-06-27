package com.sidequest.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sidequest.domain.model.SyncMeta

/**
 * Room representation of [com.sidequest.domain.model.Bucket].
 *
 * The embedded [SyncMeta] flattens into the row. Indexed on [accountId] for
 * per-account reads; name uniqueness (Req 2.6) is enforced in repository logic
 * (normalized, case-insensitive) rather than by a DB constraint, since
 * normalization is part of the domain rule.
 */
@Entity(
    tableName = "buckets",
    indices = [Index("accountId")],
)
data class BucketEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val name: String,
    val notStartedColor: String,
    val inProgressColor: String,
    val completedColor: String,
    @Embedded val sync: SyncMeta,
)
