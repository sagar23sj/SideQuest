package com.sidequest.domain.model

import kotlinx.serialization.Serializable

/**
 * Sync metadata embedded in every syncable entity. Drives offline-first,
 * last-writer-wins synchronization with the backend.
 */
@Serializable
data class SyncMeta(
    /** Epoch milliseconds; server-authoritative after a push is acknowledged. */
    val updatedAt: Long,
    /** Increments on every update; used for concurrency detection. */
    val version: Long,
    /** Tombstone flag: true once the entity has been deleted. */
    val deleted: Boolean,
    /** Client-only flag: true while the entity has changes pending a push. */
    val dirty: Boolean,
)
