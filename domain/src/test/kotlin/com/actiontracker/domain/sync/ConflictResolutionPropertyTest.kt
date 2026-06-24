package com.actiontracker.domain.sync

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test for client-side sync conflict resolution (Property 32),
 * mirroring the Go backend's `TestResolveConflict_Property32`.
 *
 * For any two concurrent versions of the same record, the merged result equals
 * the version with the greater `updatedAt`; the merge is deterministic and
 * order-independent (commutative). The two versions share an id (the realistic
 * conflict shape), and `updatedAt`/`version` are drawn from a small range so
 * ties — which exercise the secondary/tertiary tie-breakers — occur often.
 *
 * _Requirements: 14.4_
 */
class ConflictResolutionPropertyTest : StringSpec({

    fun sync(updatedAt: Long, version: Long, deleted: Boolean): SyncMeta =
        SyncMeta(updatedAt = updatedAt, version = version, deleted = deleted, dirty = false)

    // Two concurrent versions of one item: same id, independently varied
    // metadata/content. updatedAt/version are kept small so collisions (and thus
    // the tie-break paths) are common.
    data class Pair2(val a: ActionItem, val b: ActionItem)

    val arbItemPair: Arb<Pair2> = Arb.bind(
        Arb.string(1..6),          // shared id
        Arb.long(0L..3L),          // a.updatedAt
        Arb.long(0L..3L),          // b.updatedAt
        Arb.long(0L..2L),          // a.version
        Arb.long(0L..2L),          // b.version
        Arb.enum<ActionStatus>(),  // a.status
        Arb.enum<ActionStatus>(),  // b.status
        Arb.boolean(),             // a.deleted
        Arb.boolean(),             // b.deleted
    ) { id, aUpd, bUpd, aVer, bVer, aStatus, bStatus, aDel, bDel ->
        fun item(updatedAt: Long, version: Long, status: ActionStatus, deleted: Boolean) =
            ActionItem(
                id = id,
                accountId = "acct-1",
                bucketId = "bucket-1",
                title = "item-$id",
                contentType = ContentType.TEXT,
                timeframe = Timeframe.Today,
                status = status,
                createdAt = 0L,
                isWishlistItem = false,
                sync = sync(updatedAt, version, deleted),
            )
        Pair2(
            a = item(aUpd, aVer, aStatus, aDel),
            b = item(bUpd, bVer, bStatus, bDel),
        )
    }

    // Feature: action-tracker-app, Property 32: Conflict resolution is deterministic last-writer-wins
    "Property 32: the winner has the greater updatedAt" {
        checkAll(100, arbItemPair) { (a, b) ->
            val c = ConflictResolution.resolveActionItem(a, b)
            when {
                a.sync.updatedAt > b.sync.updatedAt -> c.winner shouldBe a
                b.sync.updatedAt > a.sync.updatedAt -> c.winner shouldBe b
                // Equal updatedAt: the winner still carries the shared timestamp.
                else -> c.winner.sync.updatedAt shouldBe a.sync.updatedAt
            }
        }
    }

    "Property 32: resolution is order-independent (commutative)" {
        checkAll(100, arbItemPair) { (a, b) ->
            val ab = ConflictResolution.resolveActionItem(a, b)
            val ba = ConflictResolution.resolveActionItem(b, a)
            ab.winner shouldBe ba.winner
            ab.loser shouldBe ba.loser
        }
    }

    "Property 32: resolution is deterministic (repeatable)" {
        checkAll(100, arbItemPair) { (a, b) ->
            ConflictResolution.resolveActionItem(a, b) shouldBe
                ConflictResolution.resolveActionItem(a, b)
        }
    }

    "Property 32: winner and loser are exactly the two inputs" {
        checkAll(100, arbItemPair) { (a, b) ->
            val c = ConflictResolution.resolveActionItem(a, b)
            val matchesAB = c.winner == a && c.loser == b
            val matchesBA = c.winner == b && c.loser == a
            (matchesAB || matchesBA) shouldBe true
        }
    }
})
