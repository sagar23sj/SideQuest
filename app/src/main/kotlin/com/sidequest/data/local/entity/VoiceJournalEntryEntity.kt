package com.sidequest.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sidequest.domain.model.SyncMeta

/**
 * Room representation of [com.sidequest.domain.model.VoiceJournalEntry]
 * (Req 10.4).
 *
 * The embedded [SyncMeta] flattens into the row. The
 * [extractedActionItemIds] list is stored as a single JSON column via the
 * [com.sidequest.data.local.converters.Converters] `List<String>` converter,
 * keeping the entry a self-contained row that mirrors the domain model. Indexed
 * on [accountId] for per-account reads and [createdAt] so entries can be listed
 * newest-first.
 */
@Entity(
    tableName = "voice_journal_entries",
    indices = [
        Index("accountId"),
        Index("createdAt"),
    ],
)
data class VoiceJournalEntryEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val audioRef: String,
    val transcript: String?,
    val transcriptionFailed: Boolean,
    val createdAt: Long,
    val extractedActionItemIds: List<String>,
    @Embedded val sync: SyncMeta,
)
