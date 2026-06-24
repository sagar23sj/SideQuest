package com.actiontracker.ui.board

import com.actiontracker.domain.board.BoardState

/**
 * Screen state for the Action Board, exposed as a single immutable value from
 * [BoardViewModel].
 *
 * The board reads an aggregated [BoardState] from the repository (Req 4.1–4.5,
 * 5.4). Until the first [BoardState] arrives from the Room-backed flow the
 * screen shows [Loading]; thereafter [Ready] carries the live board, which
 * re-emits whenever an item's status changes so the indicator colors (Req 4.7)
 * and the Completion_Counter (Req 5.2, 5.3, 5.4) stay current.
 */
sealed interface BoardUiState {

    /** The board is being assembled from local storage. */
    data object Loading : BoardUiState

    /**
     * The live board is available.
     *
     * @property board the grouped board with resolved status colors and the
     *   derived completion count.
     */
    data class Ready(val board: BoardState) : BoardUiState
}
