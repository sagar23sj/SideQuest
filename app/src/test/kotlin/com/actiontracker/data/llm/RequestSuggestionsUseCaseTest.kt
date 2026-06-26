package com.actiontracker.data.llm

import com.actiontracker.domain.llm.LlmResult
import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

/**
 * Example test proving that opening an Action_Item requests suggested actions
 * from the LLM_Service (Req 7.2).
 *
 * [RequestSuggestionsUseCase] is the trigger the item-detail view invokes when
 * the user opens an item. The behavior under test is the *request itself*: the
 * use case must call [LlmService.suggestActions] with exactly the opened item,
 * and surface the result through the pure fail-soft mapping
 * ([com.actiontracker.domain.llm.LlmFailSoft.listOrUnavailable]) so the UI
 * always receives a completed outcome. The [LlmService] is mocked so the test
 * verifies the trigger without any network.
 *
 * _Requirements: 7.2_
 */
class RequestSuggestionsUseCaseTest : StringSpec({

    fun actionItem(id: String = "item-1"): ActionItem =
        ActionItem(
            id = id,
            accountId = "account-1",
            bucketId = "bucket-1",
            title = "Plan trip to Kyoto",
            contentType = ContentType.LINK,
            sourceContent = "https://example.com/kyoto",
            timeframe = Timeframe.WithinAWeek,
            status = ActionStatus.NOT_STARTED,
            createdAt = 1_000L,
            sync = SyncMeta(updatedAt = 1_000L, version = 1, deleted = false, dirty = true),
        )

    "opening an item requests LLM suggestions for that item" {
        runTest {
            val item = actionItem()
            val llmService: LlmService = mockk {
                coEvery { suggestActions(item) } returns
                    LlmResult.Ok(listOf("Book flights", "Reserve a ryokan"))
            }

            val outcome = RequestSuggestionsUseCase(llmService).requestSuggestions(item)

            // The trigger must reach the LLM_Service with exactly the opened item.
            coVerify(exactly = 1) { llmService.suggestActions(item) }
            // ...and surface the suggestions through fail-soft mapping.
            outcome.unavailable shouldBe false
            outcome.values shouldBe listOf("Book flights", "Reserve a ryokan")
        }
    }

    "opening an item completes with an unavailable outcome when the LLM is unavailable" {
        runTest {
            val item = actionItem("item-2")
            val llmService: LlmService = mockk {
                coEvery { suggestActions(item) } returns LlmResult.Unavailable
            }

            val outcome = RequestSuggestionsUseCase(llmService).requestSuggestions(item)

            // The request is still made on open; the flow fails soft rather than
            // blocking or erroring (Req 7.5).
            coVerify(exactly = 1) { llmService.suggestActions(item) }
            outcome.unavailable shouldBe true
            outcome.values shouldBe emptyList()
        }
    }
})
