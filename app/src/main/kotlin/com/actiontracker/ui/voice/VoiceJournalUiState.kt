package com.actiontracker.ui.voice

/**
 * State for the voice-journal recording screen (Req 10.1, 10.2, 10.4).
 *
 * @property isRecording whether a recording is currently in progress (Req 10.2).
 * @property showPermissionRationale whether to show the microphone-permission
 *   explanation with a deep link to app settings, shown after the user has
 *   denied RECORD_AUDIO so recording stays blocked until it is granted
 *   (Req 10.1).
 * @property errorMessage a transient recording error to surface, or null.
 * @property lastSavedEntryId the id of the most recently persisted
 *   Voice_Journal_Entry, set after a successful stop (Req 10.4); used to confirm
 *   the save to the user.
 * @property isTranscribing whether transcription of the last saved entry is in
 *   progress (Req 10.3).
 * @property transcriptionFailed whether transcription of the last saved entry
 *   failed; when true the audio is retained and the UI shows a failure message
 *   (Req 10.8).
 */
data class VoiceJournalUiState(
    val isRecording: Boolean = false,
    val showPermissionRationale: Boolean = false,
    val errorMessage: String? = null,
    val lastSavedEntryId: String? = null,
    val isTranscribing: Boolean = false,
    val transcriptionFailed: Boolean = false,
)
