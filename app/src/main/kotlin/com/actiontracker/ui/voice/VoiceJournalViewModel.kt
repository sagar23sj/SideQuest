package com.actiontracker.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actiontracker.data.repository.VoiceJournalRepository
import com.actiontracker.ui.capture.CurrentAccountProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the voice-journal recording screen (Req 10.1, 10.2, 10.4).
 *
 * The OS microphone permission is requested by the UI (Activity Result API);
 * this view model owns the recording lifecycle and the resulting UI state. When
 * permission is granted the UI calls [startRecording]/[stopRecording], which
 * delegate to the [VoiceJournalRepository] to capture audio and persist a
 * Voice_Journal_Entry. When permission is denied the UI calls
 * [onPermissionDenied], which raises a rationale so recording stays blocked and
 * the screen can deep-link the user to app settings (Req 10.1).
 *
 * Exposes an immutable [StateFlow] (never the backing [MutableStateFlow]) and
 * performs all suspending work inside [viewModelScope] for structured
 * concurrency.
 */
@HiltViewModel
class VoiceJournalViewModel @Inject constructor(
    private val repository: VoiceJournalRepository,
    private val accountProvider: CurrentAccountProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceJournalUiState())
    val uiState: StateFlow<VoiceJournalUiState> = _uiState.asStateFlow()

    /**
     * Starts capturing audio after the microphone permission has been granted
     * (Req 10.2). Clears any prior permission rationale or error.
     */
    fun startRecording() {
        if (_uiState.value.isRecording) return
        viewModelScope.launch {
            try {
                repository.startRecording()
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        showPermissionRationale = false,
                        errorMessage = null,
                        lastSavedEntryId = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRecording = false, errorMessage = e.message)
                }
            }
        }
    }

    /**
     * Stops the in-progress recording and persists the Voice_Journal_Entry for
     * the current account (Req 10.4), recording the saved entry id in state.
     */
    fun stopRecording() {
        if (!_uiState.value.isRecording) return
        viewModelScope.launch {
            try {
                val entry = repository.stopRecording(accountProvider.currentAccountId())
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        errorMessage = null,
                        lastSavedEntryId = entry.id,
                        transcriptionFailed = false,
                    )
                }
                transcribe(entry.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRecording = false, errorMessage = e.message)
                }
            }
        }
    }

    /**
     * Sends the saved entry's audio for transcription and reflects the outcome
     * in state (Req 10.3, 10.4, 10.8). On failure the audio is retained and
     * [VoiceJournalUiState.transcriptionFailed] is set so the screen shows a
     * failure message; the call never throws because the repository fails soft.
     */
    private fun transcribe(entryId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTranscribing = true, transcriptionFailed = false) }
            val updated = repository.transcribe(entryId)
            _uiState.update {
                it.copy(
                    isTranscribing = false,
                    transcriptionFailed = updated?.transcriptionFailed ?: false,
                )
            }
        }
    }

    /**
     * Records that the microphone permission was denied (Req 10.1). Recording
     * stays blocked and the screen shows an explanation with a deep link to app
     * settings.
     */
    fun onPermissionDenied() {
        _uiState.update { it.copy(showPermissionRationale = true, isRecording = false) }
    }

    /**
     * Clears the permission rationale once the user has (re)granted the
     * microphone permission.
     */
    fun onPermissionGranted() {
        _uiState.update { it.copy(showPermissionRationale = false) }
    }

    /** Dismisses the current transient error message. */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
