package com.sidequest.ui.bucket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.repository.BucketDeleteResult
import com.sidequest.data.repository.BucketRepository
import com.sidequest.domain.bucket.BucketDeletionStrategy
import com.sidequest.domain.model.Bucket
import com.sidequest.ui.capture.CurrentAccountProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Screen state for bucket management (Req 2.1–2.6).
 *
 * @property buckets the account's live buckets, ordered by name.
 * @property loading true until the first bucket list arrives from Room.
 */
data class BucketManagementUiState(
    val buckets: List<Bucket> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Drives the bucket-management screen. Observes the current account's live
 * buckets from [BucketRepository] (Req 2.2) and exposes delete actions for both
 * empty buckets (Req 2.4) and non-empty buckets via the reassign-or-delete
 * strategy (Req 2.5).
 *
 * Create and rename are handled on the dedicated create/edit screen
 * ([CreateBucketViewModel]); this view model focuses on listing and deletion so
 * the management list stays thin.
 */
@HiltViewModel
class BucketManagementViewModel @Inject constructor(
    private val bucketRepository: BucketRepository,
    accountProvider: CurrentAccountProvider,
) : ViewModel() {

    private val accountId: String = accountProvider.currentAccountId()

    val uiState: StateFlow<BucketManagementUiState> =
        bucketRepository.observeBuckets(accountId)
            .map { BucketManagementUiState(buckets = it, loading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = BucketManagementUiState(),
            )

    /**
     * Attempts to delete [bucketId]. Empty buckets are tombstoned immediately
     * (Req 2.4); a non-empty bucket reports its item count back through
     * [onResult] so the screen can prompt the reassign-or-delete decision
     * (Req 2.5).
     */
    fun deleteBucket(bucketId: String, onResult: (BucketDeleteResult) -> Unit) {
        viewModelScope.launch {
            onResult(bucketRepository.deleteBucket(bucketId))
        }
    }

    /**
     * Completes deletion of a non-empty bucket by resolving its items with the
     * chosen [strategy] (reassign to another bucket, or delete the items), then
     * tombstoning the bucket (Req 2.5).
     */
    fun deleteNonEmptyBucket(
        bucketId: String,
        strategy: BucketDeletionStrategy,
        onResult: (BucketDeleteResult) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(bucketRepository.deleteNonEmptyBucket(bucketId, strategy))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
