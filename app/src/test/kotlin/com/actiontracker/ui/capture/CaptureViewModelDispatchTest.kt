package com.actiontracker.ui.capture

import app.cash.turbine.test
import com.actiontracker.data.repository.BeginCaptureResult
import com.actiontracker.data.repository.BucketRepository
import com.actiontracker.data.repository.CaptureRepository
import com.actiontracker.domain.capture.CaptureDraft
import com.actiontracker.domain.capture.SharedIntentData
import com.actiontracker.domain.model.ContentType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Example tests proving that share-intent dispatch routes into the
 * categorization flow without standing up the real Activity/UI (Req 1.2, 1.3).
 *
 * The [ShareTargetActivity] maps an incoming share intent to a
 * [SharedIntentData] and hands it to [CaptureViewModel.onShared]; this test
 * drives that same entry point directly. Supported content must move the screen
 * into [CaptureUiState.Categorizing] (the bucket + timeframe selection flow,
 * Req 1.2/1.3); unsupported content must move into [CaptureUiState.Unsupported]
 * (Req 1.4 boundary of the dispatch).
 *
 * The view model's coroutines run on [androidx.lifecycle.viewModelScope]
 * (the Main dispatcher), so each test installs an [UnconfinedTestDispatcher] as
 * Main. The repositories are mocked with mockk and a fixed
 * [CurrentAccountProvider] + deterministic `today` clock are injected, keeping
 * the test pure JVM logic with no Android/UI dependency.
 *
 * _Requirements: 1.2, 1.3_
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelDispatchTest : StringSpec({

    val fixedToday = LocalDate.of(2025, 6, 14)

    beforeTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    afterTest {
        Dispatchers.resetMain()
    }

    fun bucketRepositoryReturningNoBuckets(): BucketRepository = mockk {
        every { observeBuckets(any()) } returns flowOf(emptyList())
    }

    fun viewModelWith(
        captureRepository: CaptureRepository,
        bucketRepository: BucketRepository = bucketRepositoryReturningNoBuckets(),
    ): CaptureViewModel {
        val accountProvider: CurrentAccountProvider = mockk {
            every { currentAccountId() } returns CurrentAccountProvider.LOCAL_ACCOUNT_ID
        }
        return CaptureViewModel(
            captureRepository = captureRepository,
            bucketRepository = bucketRepository,
            accountProvider = accountProvider,
            today = { fixedToday },
        )
    }

    "supported share dispatch enters the categorization flow" {
        // A text/plain share carrying a URL classifies as a supported LINK; the
        // repository returns a draft, so dispatch must open the categorization
        // flow carrying that draft (Req 1.2, 1.3).
        val draft = CaptureDraft(
            accountId = CurrentAccountProvider.LOCAL_ACCOUNT_ID,
            title = "Check this out https://example.com/post",
            contentType = ContentType.LINK,
            sourceContent = "Check this out https://example.com/post",
        )
        val intentData = SharedIntentData(
            mimeType = "text/plain",
            text = "Check this out https://example.com/post",
        )
        val captureRepository: CaptureRepository = mockk {
            coEvery { beginCapture(CurrentAccountProvider.LOCAL_ACCOUNT_ID, intentData) } returns
                BeginCaptureResult.Draft(draft)
        }

        val viewModel = viewModelWith(captureRepository)
        viewModel.onShared(intentData)

        val state = viewModel.uiState.value
        state.shouldBeInstanceOf<CaptureUiState.Categorizing>()
        state.draft shouldBe draft
    }

    "unsupported share dispatch enters the unsupported state" {
        // An application/pdf share is outside the registered text/image/video
        // filters; the repository classifies it as unsupported, so dispatch
        // must route to the not-supported state rather than the flow (Req 1.4).
        val intentData = SharedIntentData(mimeType = "application/pdf")
        val captureRepository: CaptureRepository = mockk {
            coEvery { beginCapture(CurrentAccountProvider.LOCAL_ACCOUNT_ID, intentData) } returns
                BeginCaptureResult.Unsupported
        }

        val viewModel = viewModelWith(captureRepository)
        viewModel.onShared(intentData)

        viewModel.uiState.value shouldBe CaptureUiState.Unsupported
    }

    "plain text without a link still dispatches into the categorization flow" {
        // Plain text (no URL) classifies as supported TEXT; dispatch still opens
        // the categorization flow so the user can bucket it (Req 1.2, 1.3).
        val draft = CaptureDraft(
            accountId = CurrentAccountProvider.LOCAL_ACCOUNT_ID,
            title = "Remember to plan the trip",
            contentType = ContentType.TEXT,
            sourceContent = "Remember to plan the trip",
        )
        val intentData = SharedIntentData(
            mimeType = "text/plain",
            text = "Remember to plan the trip",
        )
        val captureRepository: CaptureRepository = mockk {
            coEvery { beginCapture(CurrentAccountProvider.LOCAL_ACCOUNT_ID, intentData) } returns
                BeginCaptureResult.Draft(draft)
        }

        val viewModel = viewModelWith(captureRepository)

        viewModel.uiState.test {
            // Initial Loading state before dispatch completes.
            awaitItem() shouldBe CaptureUiState.Loading
            viewModel.onShared(intentData)
            val categorizing = awaitItem()
            categorizing.shouldBeInstanceOf<CaptureUiState.Categorizing>()
            categorizing.draft shouldBe draft
        }
    }
})
