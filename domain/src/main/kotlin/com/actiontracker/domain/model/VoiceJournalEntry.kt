package com.actiontracker.domain.model

import kotlinx.serialization.Serializable

/**
 * A voice journal recording captured in the app together with its generated
 * transcript and any Action_Items extracted from it (Req 10.x).
 *
 * IDs are client-generated UUIDs so an entry can be created offline without a
 * server round-trip. [audioRef] points at the recorded audio: a local file path
 * until the sync job uploads it to object storage, after which it points at the
 * storage key.
 *
 * Transcription is a later step (Req 10.3): a freshly captured entry stores the
 * audio with [transcript] null and [transcriptionFailed] false. On a successful
 * transcription the [transcript] is filled in; on failure the audio is retained
 * with [transcript] null and [transcriptionFailed] true (Req 10.8). Confirmed
 * extracted actions are linked back here via [extractedActionItemIds] (Req 10.7).
 */
@Serializable
data class VoiceJournalEntry(
    val id: String,
    val accountId: String,
    /** Object-storage key or on-device path to the recorded audio. */
    val audioRef: String,
    /** Generated transcript text; null until transcription succeeds (Req 10.4, 10.8). */
    val transcript: String? = null,
    /** True when transcription was attempted and failed (Req 10.8). */
    val transcriptionFailed: Boolean = false,
    val createdAt: Long,
    /** Ids of Action_Items created from confirmed extracted actions (Req 10.7). */
    val extractedActionItemIds: List<String> = emptyList(),
    val sync: SyncMeta,
)
