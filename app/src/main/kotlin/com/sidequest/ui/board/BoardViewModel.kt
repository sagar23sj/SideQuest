package com.sidequest.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.repository.BoardRepository
import com.sidequest.domain.model.ActionStatus
import com.sidequest.ui.capture.CurrentAccountProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the Action Board screen (Req 4.1–4.7, 5.1–5.4).
 *
 * The view model observes the current account's live [com.sidequest.domain.board.BoardState]
 * from [BoardRepository.observeBoard] and exposes it as an immutable
 * [StateFlow] of [BoardUiState]. Because the repository's flow is Room-backed,
 * the board — including each item's indicator color (Req 4.7) and the
 * Completion_Counter (Req 5.2, 5.3, 5.4) — recomputes reactively whenever an
 * item's status changes. The composables are stateless and emit intents back
 * through [onStatusChange].
 */
@HiltViewModel
class BoardViewModel @Inject constructor(
    private val boardRepository: BoardRepository,
    private val accountProvider: CurrentAccountProvider,
) : ViewModel() {

    private val accountId: String = accountProvider.currentAccountId()

    /**
     * The live board state. Starts as [BoardUiState.Loading] and switches to
     * [BoardUiState.Ready] once the first [com.sidequest.domain.board.BoardState]
     * is emitted from local storage. Shared while the screen is subscribed so
     * the flow is not re-collected on configuration changes.
     */
    val uiState: StateFlow<BoardUiState> =
        boardRepository.observeBoard(accountId)
            .map<_, BoardUiState> { BoardUiState.Ready(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = BoardUiState.Loading,
            )

    /**
     * Changes the [com.sidequest.domain.model.ActionStatus] of the item
     * identified by [itemId] to [newStatus] (Req 4.6). The change is persisted
     * through the repository; the board's indicator color (Req 4.7) and the
     * Completion_Counter (Req 5.2, 5.3, 5.4) update reactively via [uiState].
     */
    fun onStatusChange(itemId: String, newStatus: ActionStatus) {
        viewModelScope.launch {
            boardRepository.changeStatus(itemId, newStatus)
        }
    }

    private companion object {
        /** Keep the upstream flow alive briefly across config changes. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
