package com.sidequest.domain.plan

import com.sidequest.domain.model.ActionPlan
import com.sidequest.domain.model.SubAction
import com.sidequest.domain.model.SyncMeta
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test for sub-action progress counting (Property 16).
 *
 * For any [ActionPlan], [ActionPlanOperations.progress] reports a `completed`
 * count equal to the number of completed sub-actions and a `total` equal to the
 * number of sub-actions, with `0 <= completed <= total` (Req 9.3).
 *
 * The generator builds plans with an arbitrary list of sub-actions (random
 * `completed` flags, unique ids) so the count is exercised across empty plans,
 * all-incomplete, all-complete, and mixed plans.
 *
 * _Requirements: 9.3_
 */
class SubActionProgressCountPropertyTest : StringSpec({

    fun syncMeta(): SyncMeta =
        SyncMeta(updatedAt = 0L, version = 1L, deleted = false, dirty = false)

    // An arbitrary plan with a list of sub-actions that carry unique ids
    // ("sub-0", "sub-1", ...) and random completed flags.
    val arbPlan: Arb<ActionPlan> =
        Arb.list(Arb.bind(Arb.string(0..20), Arb.boolean()) { text, completed ->
            // text and completed; id/order assigned positionally below.
            text to completed
        }, 0..30).map { rawSubActions ->
            val subActions = rawSubActions.mapIndexed { index, (text, completed) ->
                SubAction(id = "sub-$index", text = text, order = index, completed = completed)
            }
            ActionPlan(
                id = "plan-1",
                actionItemId = "item-1",
                subActions = subActions,
                sync = syncMeta(),
            )
        }

    // Feature: action-tracker-app, Property 16: Sub-action progress count is accurate
    "Property 16: progress completed equals completed sub-actions and total equals sub-action count" {
        checkAll(100, arbPlan) { plan ->
            val progress = ActionPlanOperations.progress(plan)

            progress.completed shouldBe plan.subActions.count { it.completed }
            progress.total shouldBe plan.subActions.size

            // 0 <= completed <= total
            progress.completed shouldBeGreaterThanOrEqual 0
            progress.completed shouldBeLessThanOrEqual progress.total
        }
    }
})
