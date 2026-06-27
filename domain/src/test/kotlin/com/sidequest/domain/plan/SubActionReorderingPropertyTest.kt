package com.sidequest.domain.plan

import com.sidequest.domain.model.ActionPlan
import com.sidequest.domain.model.SubAction
import com.sidequest.domain.model.SyncMeta
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test for sub-action reordering (Property 18).
 *
 * For any [ActionPlan] and any requested ordering,
 * [ActionPlanOperations.reorder] preserves the exact set of sub-actions (none
 * added or lost) and yields `order` indices forming a contiguous `0..n-1`
 * sequence, with the mentioned-and-existing ids appearing first in the
 * requested order (Req 9.5).
 *
 * The requested ordering is a list drawn from the plan's own ids (shuffled
 * subset), interleaved with unknown ids and duplicates, so the documented
 * total contract (ignore unknown/duplicate ids, append unmentioned, reindex)
 * is exercised.
 *
 * _Requirements: 9.5_
 */
class SubActionReorderingPropertyTest : StringSpec({

    fun syncMeta(): SyncMeta =
        SyncMeta(updatedAt = 0L, version = 1L, deleted = false, dirty = false)

    // An arbitrary plan paired with a requested ordering. The requested ordering
    // references existing sub-actions by index (yielding duplicates/reorderings)
    // and is interleaved with unknown ids to exercise the full reorder contract.
    val arbPlanAndRequest: Arb<Pair<ActionPlan, List<String>>> =
        Arb.bind(
            Arb.list(Arb.bind(Arb.string(0..20), Arb.boolean()) { text, completed ->
                text to completed
            }, 0..20),
            Arb.list(Arb.bind(Arb.int(0..40), Arb.boolean()) { idxSeed, useUnknown ->
                idxSeed to useUnknown
            }, 0..30),
        ) { rawSubActions, rawRequest ->
            val subActions = rawSubActions.mapIndexed { index, (text, completed) ->
                SubAction(id = "sub-$index", text = text, order = index, completed = completed)
            }
            val plan = ActionPlan(
                id = "plan-1",
                actionItemId = "item-1",
                subActions = subActions,
                sync = syncMeta(),
            )
            val existingIds = subActions.map { it.id }
            val requestedIds = rawRequest.map { (idxSeed, useUnknown) ->
                if (useUnknown || existingIds.isEmpty()) {
                    "unknown-$idxSeed"
                } else {
                    existingIds[idxSeed % existingIds.size]
                }
            }
            plan to requestedIds
        }

    // Feature: action-tracker-app, Property 18: Reordering sub-actions is a permutation with contiguous ordering
    "Property 18: reorder preserves the sub-action set, yields contiguous order, and fronts the requested ids" {
        checkAll(100, arbPlanAndRequest) { (plan, requestedIds) ->
            val result = ActionPlanOperations.reorder(plan, requestedIds)

            // (a) The set/multiset of sub-action ids is unchanged: none added or lost.
            val inputCounts = plan.subActions.groupingBy { it.id }.eachCount()
            val outputCounts = result.subActions.groupingBy { it.id }.eachCount()
            outputCounts shouldBe inputCounts
            result.subActions.size shouldBe plan.subActions.size

            // (b) Order indices form a contiguous 0..n-1 sequence.
            val n = result.subActions.size
            result.subActions.map { it.order }.sorted() shouldBe (0 until n).toList()
            // Stored in list order so element i has order == i.
            result.subActions.forEachIndexed { index, subAction ->
                subAction.order shouldBe index
            }

            // (c) Mentioned-and-existing ids appear first, in the requested order,
            // with duplicates collapsed to their first occurrence.
            val existingIdSet = plan.subActions.map { it.id }.toSet()
            val expectedFront = requestedIds
                .filter { it in existingIdSet }
                .distinct()
            val actualFront = result.subActions.map { it.id }.take(expectedFront.size)
            actualFront shouldBe expectedFront
        }
    }
})
