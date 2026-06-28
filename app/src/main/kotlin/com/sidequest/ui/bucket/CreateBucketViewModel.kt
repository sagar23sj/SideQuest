package com.sidequest.ui.bucket

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidequest.data.local.dao.BucketDao
import com.sidequest.data.repository.BucketRepository
import com.sidequest.domain.bucket.BucketResult
import com.sidequest.ui.capture.CurrentAccountProvider
import com.sidequest.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editable form state for creating or editing a bucket (Req 2.1, 2.3, 2.6).
 *
 * @property name the bucket name being entered.
 * @property notStartedColor / [inProgressColor] / [completedColor] the three
 *   per-status indicator colors as `#RRGGBB` strings (Req 4.3).
 * @property shoppingEnabled whether this bucket behaves as a shopping bucket —
 *   the single switch that turns on the optional product/source/purchased
 *   fields on contained items (the entire "shopping" feature).
 * @property nameError an in-use message shown when the name collides (Req 2.6).
 * @property isEditing true when editing an existing bucket rather than creating.
 * @property saved set true once a save succeeds so the screen can navigate back.
 */
data class CreateBucketUiState(
    val name: String = "",
    val notStartedColor: String = DEFAULT_NOT_STARTED,
    val inProgressColor: String = DEFAULT_IN_PROGRESS,
    val completedColor: String = DEFAULT_COMPLETED,
    val shoppingEnabled: Boolean = false,
    val nameError: String? = null,
    val isEditing: Boolean = false,
    val saved: Boolean = false,
    /** Local file path of the chosen cover image, or null for a themed cover. */
    val imageRef: String? = null,
) {
    /** True when the form can be submitted. */
    val canSave: Boolean get() = name.isNotBlank()

    companion object {
        // Brand-aligned defaults from the SideQuest palette: tertiary (teal),
        // secondary (violet), primary (terracotta) read clearly as three
        // distinct status colors.
        const val DEFAULT_NOT_STARTED = "#89726B"
        const val DEFAULT_IN_PROGRESS = "#6D4EA2"
        const val DEFAULT_COMPLETED = "#006A63"
    }
}

/**
 * Drives the create / edit bucket form (Req 2.1, 2.3, 2.6).
 *
 * Creating validates per-account name uniqueness through [BucketRepository]
 * (which delegates to the pure domain rule) and surfaces the in-use message on
 * a collision (Req 2.6). When opened with an [Routes.EDIT_BUCKET_ARG] argument
 * the form pre-fills from the existing bucket and saving renames it (Req 2.3).
 *
 * Note: persisting the `shoppingEnabled` flag requires a `Bucket` schema field
 * that does not exist yet (the domain `Bucket` has no shopping flag). The
 * toggle state is captured here so the UI is complete; wiring it through the
 * repository is tracked in [persistShoppingFlagTodo].
 */
@HiltViewModel
class CreateBucketViewModel @Inject constructor(
    private val bucketRepository: BucketRepository,
    private val accountProvider: CurrentAccountProvider,
    @ApplicationContext private val appContext: Context,
    private val bucketDao: BucketDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editingBucketId: String? = savedStateHandle[Routes.EDIT_BUCKET_ARG]

    private val _uiState = MutableStateFlow(
        CreateBucketUiState(isEditing = editingBucketId != null),
    )
    val uiState: StateFlow<CreateBucketUiState> = _uiState.asStateFlow()

    init {
        // When editing, pre-fill the form from the existing bucket so the user
        // can adjust the name, colors, and cover image rather than start blank.
        if (editingBucketId != null) {
            viewModelScope.launch {
                bucketDao.getById(editingBucketId)?.let { bucket ->
                    _uiState.update {
                        it.copy(
                            name = bucket.name,
                            notStartedColor = bucket.notStartedColor,
                            inProgressColor = bucket.inProgressColor,
                            completedColor = bucket.completedColor,
                            imageRef = bucket.imageRef,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) =
        _uiState.update { it.copy(name = value, nameError = null) }

    fun onNotStartedColorChange(value: String) =
        _uiState.update { it.copy(notStartedColor = value) }

    fun onInProgressColorChange(value: String) =
        _uiState.update { it.copy(inProgressColor = value) }

    fun onCompletedColorChange(value: String) =
        _uiState.update { it.copy(completedColor = value) }

    fun onShoppingToggle(enabled: Boolean) =
        _uiState.update { it.copy(shoppingEnabled = enabled) }

    /**
     * Copies the picked image [uri] into app-internal storage and records its
     * path as the bucket's cover. Copying (rather than holding the content URI)
     * makes the image durable across restarts and avoids URI-permission issues.
     * A null [uri] clears any chosen image.
     */
    fun onImagePicked(uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(imageRef = null) }
            return
        }
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { copyImageToStorage(uri) }
            if (path != null) _uiState.update { it.copy(imageRef = path) }
        }
    }

    private fun copyImageToStorage(uri: Uri): String? = runCatching {
        val dir = File(appContext.filesDir, "bucket_images").apply { mkdirs() }
        val dest = File(dir, "bucket_${UUID.randomUUID()}.jpg")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        dest.absolutePath
    }.getOrNull()

    /**
     * Saves the form: creates a new bucket, or renames the existing one when
     * editing. A duplicate name is rejected with the in-use message (Req 2.6)
     * and nothing is persisted; on success [CreateBucketUiState.saved] flips so
     * the screen pops back.
     */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            val bucketId = editingBucketId
            if (bucketId != null) {
                // Persist any newly picked cover image + color changes.
                if (state.imageRef != null) {
                    bucketRepository.setBucketImage(bucketId, state.imageRef)
                }
                bucketRepository.updateBucketColors(
                    bucketId = bucketId,
                    notStartedColor = state.notStartedColor,
                    inProgressColor = state.inProgressColor,
                    completedColor = state.completedColor,
                )
                when (val result = bucketRepository.renameBucket(bucketId, state.name)) {
                    is BucketResult.Renamed -> _uiState.update { it.copy(saved = true) }
                    is BucketResult.DuplicateName ->
                        _uiState.update { it.copy(nameError = result.message) }
                    is BucketResult.Created -> _uiState.update { it.copy(saved = true) }
                    null -> _uiState.update { it.copy(saved = true) }
                }
            } else {
                val accountId = accountProvider.currentAccountId()
                val result = bucketRepository.createBucket(
                    accountId = accountId,
                    name = state.name,
                    notStartedColor = state.notStartedColor,
                    inProgressColor = state.inProgressColor,
                    completedColor = state.completedColor,
                    imageRef = state.imageRef,
                )
                when (result) {
                    is BucketResult.Created -> _uiState.update { it.copy(saved = true) }
                    is BucketResult.DuplicateName ->
                        _uiState.update { it.copy(nameError = result.message) }
                    is BucketResult.Renamed -> _uiState.update { it.copy(saved = true) }
                }
            }
        }
    }

    /**
     * TODO(shopping-flag): The `shoppingEnabled` toggle is captured in UI state
     * but not yet persisted. Persisting it needs a boolean column on the
     * `Bucket` domain model + Room entity (e.g. `isShoppingBucket`) and a
     * repository parameter on create/rename. Once that field exists, pass
     * `_uiState.value.shoppingEnabled` through here so contained items surface
     * the product/source/purchased fields.
     */
    private val persistShoppingFlagTodo = Unit
}
