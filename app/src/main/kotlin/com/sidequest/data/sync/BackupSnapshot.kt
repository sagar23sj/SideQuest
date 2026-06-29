package com.sidequest.data.sync

import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionPlan
import com.sidequest.domain.model.Bucket
import kotlinx.serialization.Serializable

/**
 * A whole-account snapshot of the user's planner data, uploaded to the backend
 * so nothing is lost on uninstall / clear-data / new device, and restored on a
 * fresh install. The payload is intentionally the portable `:domain` models
 * (already `@Serializable`), so the backup format is decoupled from the Room
 * schema and easy to evolve.
 *
 * Tombstoned (deleted) rows are included so deletions also propagate on restore.
 */
@Serializable
data class BackupSnapshot(
    val version: Int = SCHEMA_VERSION,
    val buckets: List<Bucket> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val actionPlans: List<ActionPlan> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}
