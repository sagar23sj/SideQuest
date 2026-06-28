package com.sidequest.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.repository.BoardRepository
import com.sidequest.domain.board.BoardState
import com.sidequest.domain.model.ActionStatus
import com.sidequest.ui.capture.CurrentAccountProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Open/done tallies for a single bucket, shown as a row in the stats screen. */
data class BucketStat(
    val name: String,
    val open: Int,
    val done: Int,
) {
    val total: Int get() = open + done
    /** Completion fraction 0f..1f for the bucket's progress bar. */
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/**
 * Encouraging, non-stressful snapshot of the player's progress: totals plus a
 * per-bucket open/done breakdown. The framing is momentum-first (what you've
 * done, what's left to explore) rather than pressure-first (no overdue counts).
 */
data class StatsUiState(
    val loading: Boolean = true,
    val totalDone: Int = 0,
    val totalOpen: Int = 0,
    val buckets: List<BucketStat> = emptyList(),
) {
    val total: Int get() = totalDone + totalOpen
    val percent: Int get() = if (total == 0) 0 else (totalDone * 100) / total
    /** The bucket with the most completions, to celebrate a bright spot. */
    val topBucket: BucketStat? get() = buckets.filter { it.done > 0 }.maxByOrNull { it.done }

    companion object {
        fun from(board: BoardState): StatsUiState {
            val stats = board.groups.map { group ->
                val done = group.items.count { it.item.status == ActionStatus.COMPLETED }
                val open = group.items.size - done
                BucketStat(name = group.bucket.name, open = open, done = done)
            }
            return StatsUiState(
                loading = false,
                totalDone = stats.sumOf { it.done },
                totalOpen = stats.sumOf { it.open },
                buckets = stats.sortedByDescending { it.total },
            )
        }
    }
}

/**
 * Drives the stats dashboard (opened from the board's progress banner). Observes
 * the same live board as the board screen and derives per-bucket open/done
 * tallies, so the numbers always match what's on the board.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    boardRepository: BoardRepository,
    accountProvider: CurrentAccountProvider,
) : ViewModel() {

    private val accountId: String = accountProvider.currentAccountId()

    val uiState: StateFlow<StatsUiState> =
        boardRepository.observeBoard(accountId)
            .map { StatsUiState.from(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StatsUiState(),
            )
}
