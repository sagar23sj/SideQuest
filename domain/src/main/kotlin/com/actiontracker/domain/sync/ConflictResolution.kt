package com.actiontracker.domain.sync

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.SyncMeta

/**
 * Client-side mirror of the backend's deterministic last-writer-wins conflict
 * resolution (Req 14.4, Property 32). Lives in `:domain` so it is portable and
 * validated with the same Correctness Property the Go backend uses, keeping the
 * two implementations behaviorally identical.
 *
 * When two devices edit the same record offline, sync can surface two
 * concurrent versions of one entity. Resolution is keyed on
 * [SyncMeta.updatedAt]: the version with the greater `updatedAt` wins. To keep
 * the merge deterministic *and* order-independent (commutative) even when two
 * versions share the same `updatedAt`, ties are broken by stable secondary keys
 * — greater [SyncMeta.version], then a total order over a canonical string form
 * of the value. The loser is preserved separately so callers can write it to a
 * conflict log for auditing without it affecting the winning state.
 *
 * The functions here are pure and total: they never mutate their inputs and
 * never throw.
 */
object ConflictResolution {

    /**
     * The outcome of resolving two concurrent versions: the [winner] that
     * should be kept and the [loser] preserved for the conflict log.
     */
    data class Conflict<T>(val winner: T, val loser: T)

    /**
     * Resolves two concurrent versions of the same [ActionItem] via
     * deterministic last-writer-wins. The two versions are expected to share an
     * id; the full value is used as the final tie-breaker so the result is a
     * strict total order independent of argument order.
     */
    fun resolveActionItem(a: ActionItem, b: ActionItem): Conflict<ActionItem> =
        if (firstWins(a.sync, b.sync, { canonical(a) }, { canonical(b) })) {
            Conflict(winner = a, loser = b)
        } else {
            Conflict(winner = b, loser = a)
        }

    /**
     * Reports whether version `a` beats version `b`. [SyncMeta.updatedAt] is the
     * primary key; [SyncMeta.version] breaks an `updatedAt` tie; the canonical
     * string forms break a full metadata tie. The canonical suppliers are lazy
     * so serialization only happens on the rare full-tie path.
     *
     * When the two versions are byte-identical this returns true, but the winner
     * is then indistinguishable from the loser so argument order is irrelevant.
     */
    private inline fun firstWins(
        a: SyncMeta,
        b: SyncMeta,
        canonicalA: () -> String,
        canonicalB: () -> String,
    ): Boolean = when {
        a.updatedAt > b.updatedAt -> true
        a.updatedAt < b.updatedAt -> false
        a.version > b.version -> true
        a.version < b.version -> false
        else -> canonicalA() >= canonicalB()
    }

    /**
     * A deterministic string form of an [ActionItem], used only as the final
     * tie-breaker. `toString()` on a Kotlin data class lists every property in
     * declaration order, giving a stable, value-dependent ordering.
     */
    private fun canonical(item: ActionItem): String = item.toString()
}
