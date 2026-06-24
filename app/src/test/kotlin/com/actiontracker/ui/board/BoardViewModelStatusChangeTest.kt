package com.actiontracker.ui.board

import com.actiontracker.data.repository.BoardRepository
import com.actiontracker.data.repository.StatusChangeResult
import com.actiontracker.domain.board.BoardState
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.ui.capture.CurrentAccountProvider
import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Example test proving a status change made on the board is persisted through
 * the repository (Req 4.6).
 *
 * [BoardViewModel.onStatusChange] is the single entry point the board rows call
 * when the user picks a new [ActionStatus] from the status menu. It must forward
 * the chosen status for the tapped item to [BoardRepository.changeStatus] inside
 * [androidx.lifecycle.viewModelScope]; that write is what makes the change
 * survive (the indicator color and Completion_Counter then update reactively
 * through the Room-backed [BoardRepository.observeBoard] flow).
 *
 * Driving the view model directly keeps this a pure-JVM test with no Compose
 * runtime: the repository and [CurrentAccountProvider] are mocked, and an
 * [UnconfinedTestDispatcher] is installed as Main so the launched coroutine runs
 * eagerly and the persistence call can be verified synchronously.
 *
 * _Requirements: 4.6_
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelStatusChangeTest : StringSpec({

    beforeTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    afterTest {
        Dispatchers.resetMain()
    }

    fun viewModelWith(boardRepository: BoardRepository): BoardViewModel {
        val accountProvider: CurrentAccountProvider = mockk {
            every { currentAccountId() } returns CurrentAccountProvider.LOCAL_ACCOUNT_ID
        }
        return BoardViewModel(
            boardRepository = boardRepository,
            accountProvider = accountProvider,
        )
    }

    "onStatusChange persists the new status for the tapped item" {
        // The board is observed (empty here — the persistence path is what we
        // assert), and changeStatus reports the change as persisted (Req 4.6).
        val boardRepository: BoardRepository = mockk {
            every { observeBoard(CurrentAccountProvider.LOCAL_ACCOUNT_ID) } returns
                flowOf(BoardState(groups = emptyList(), completionCount = 0))
            coEvery { changeStatus("item-7", ActionStatus.COMPLETED) } returns
                StatusChangeResult.Changed("item-7", ActionStatus.COMPLETED)
        }

        val viewModel = viewModelWith(boardRepository)
        viewModel.onStatusChange("item-7", ActionStatus.COMPLETED)

        // The write that makes the status change survive must reach the
        // repository with exactly the tapped item id and chosen status.
        coVerify(exactly = 1) { boardRepository.changeStatus("item-7", ActionStatus.COMPLETED) }
    }

    "onStatusChange forwards each distinct status selection to the repository" {
        // Selecting a different status for a different item must persist that
        // exact (id, status) pair — the menu offers every ActionStatus (Req 4.6).
        val boardRepository: BoardRepository = mockk {
            every { observeBoard(CurrentAccountProvider.LOCAL_ACCOUNT_ID) } returns
                flowOf(BoardState(groups = emptyList(), completionCount = 0))
            coEvery { changeStatus(any(), any()) } returns StatusChangeResult.NotFound
        }

        val viewModel = viewModelWith(boardRepository)
        viewModel.onStatusChange("item-a", ActionStatus.IN_PROGRESS)

        coVerify(exactly = 1) { boardRepository.changeStatus("item-a", ActionStatus.IN_PROGRESS) }
    }
})
