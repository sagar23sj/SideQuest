package com.sidequest.domain.plan

import com.sidequest.domain.model.ActionPlan
import com.sidequest.domain.model.SubAction
import com.sidequest.domain.model.SyncMeta
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test for the "mark parent complete" prompt (Property 17).
 *
 * For any non-empty [ActionPlan],
 * [ActionPlanOperations.shouldPromptParentComplete] returns true if and only if
 * every sub-action is completed (Req 9.4).
 *
 * The generator builds NON-EMPTY plans with random `completed` flags (covering
 * both the all-complete and at-least-one-incomplete cases). An additional
 * forced all-complete variant guarantees the all-complete branch is exercised
 * even if random flags rarely produce it. The empty-plan case (=> false) is
 * also asserted.
 *
 * _Requirements: 9.4_
 */
class MarkCompletePromptPropertyTest : StringSpec({

    fun syncMeta(): SyncMeta =
        SyncMeta(updatedAt = 0L, version = 1L, deleted = false, dirty = false)

    fun plan(subActions: List<SubAction>): ActionPlan =
        ActionPlan(
            id = "plan-1",
            actionItemId = "item-1",
            subActions = subActions,
            sync = syncMeta(),
        )

    // A NON-EMPTY plan with unique sub-action ids and random completed flags.
    val arbNonEmptyPlan: Arb<ActionPlan> =
        Arb.list(Arb.bind(Arb.string(0..20), Arb.boolean()) { text, completed ->
            text to completed
        }, 1..30).map { rawSubActions ->
            plan(rawSubActions.mapIndexed { index, (text, completed) ->
                SubAction(id = "sub-$index", text = text, order = index, completed = completed)
            })
        }

    // Feature: action-tracker-app, Property 17: The "mark complete" prompt appears exactly when all sub-actions are done
    "Property 17: prompt is shown iff all sub-actions of a non-empty plan are completed" {
        checkAll(100, arbNonEmptyPlan) { plan ->
            val expected = plan.subActions.all { it.completed }
            ActionPlanOperations.shouldPromptParentComplete(plan) shouldBe expected

            // Forced all-complete variant: the prompt must always be shown.
            val allComplete = plan.copy(
                subActions = plan.subActions.map { it.copy(completed = true) },
            )
            ActionPlanOperations.shouldPromptParentComplete(allComplete) shouldBe true
        }
    }

    "Property 17: an empty plan never prompts for parent completion" {
        ActionPlanOperations.shouldPromptParentComplete(plan(emptyList())) shouldBe false
    }
})
