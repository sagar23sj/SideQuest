package com.sidequest.ui.bucket

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.repository.BoardRepository
import com.sidequest.domain.board.BoardGroup
import com.sidequest.domain.model.ActionStatus
import com.sidequest.ui.capture.CurrentAccountProvider
import com.sidequest.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Screen state for a single bucket's task list.
 *
 * @property loading true until the first board snapshot arrives.
 * @property group the matching [BoardGroup] for the opened bucket, or null when
 *   the bucket has no items yet or could not be found.
 * @property bucketName the bucket's display name (resolved even when empty).
 */
data class BucketDetailUiState(
    val loading: Boolean = true,
    val group: BoardGroup? = null,
    val bucketName: String = "",
)

/**
 * Drives the bucket-detail screen: shows the Action_Items that belong to one
 * bucket (the fix for the earlier bug where tapping a bucket showed the bucket
 * *list* instead of its tasks).
 *
 * Reuses [BoardRepository.observeBoard] and selects the single [BoardGroup]
 * whose bucket id matches the route argument, so the per-bucket view stays in
 * sync with the board and status changes persist through the same path.
 */
@HiltViewModel
class BucketDetailViewModel @Inject constructor(
    private val boardRepository: BoardRepository,
    accountProvider: CurrentAccountProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bucketId: String? = savedStateHandle[Routes.BUCKET_DETAIL_ARG]
    private val accountId: String = accountProvider.currentAccountId()

    val uiState: StateFlow<BucketDetailUiState> =
        boardRepository.observeBoard(accountId)
            .map { board ->
                val group = board.groups.firstOrNull { it.bucket.id == bucketId }
                BucketDetailUiState(
                    loading = false,
                    group = group,
                    bucketName = group?.bucket?.name ?: "",
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = BucketDetailUiState(),
            )

    /** Marks the item completed (the press-and-hold gesture's commit). */
    fun complete(itemId: String) {
        viewModelScope.launch {
            boardRepository.changeStatus(itemId, ActionStatus.COMPLETED)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
