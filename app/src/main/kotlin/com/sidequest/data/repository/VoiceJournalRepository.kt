package com.sidequest.data.repository

import com.sidequest.data.audio.AudioRecorder
import com.sidequest.data.llm.LlmService
import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.dao.VoiceJournalDao
import com.sidequest.data.local.entity.toDomain
import com.sidequest.data.local.entity.toEntity
import com.sidequest.data.transcription.LiveTranscriber
import com.sidequest.data.transcription.TranscriptionService
import com.sidequest.domain.llm.ExtractedAction
import com.sidequest.domain.llm.LlmFailSoft
import com.sidequest.domain.llm.LlmOutcome
import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.model.VoiceJournalEntry
import com.sidequest.domain.transcription.TranscriptionOutcome
import com.sidequest.domain.transcription.TranscriptionResult
import com.sidequest.domain.voice.ConfirmedExtraction
import com.sidequest.domain.voice.ExtractionConfirmationResult
import com.sidequest.domain.voice.HeuristicActionExtraction
import com.sidequest.domain.voice.VoiceActionExtraction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository coordinating voice-journal audio capture and persistence
 * (Req 10.2, 10.4).
 *
 * [startRecording] begins capture via the [AudioRecorder] seam; [stopRecording]
 * finalizes the recording and persists a [VoiceJournalEntry] referencing the
 * recorded audio. At this stage the entry is stored with no transcript and
 * `transcriptionFailed = false`: transcription (Req 10.3) is a later task that
 * updates the entry, and action extraction (Req 10.5–10.7) fills in
 * `extractedActionItemIds`. Writes mark the row dirty so the offline-first sync
 * layer uploads the audio and pushes the entry.
 *
 * Follows the project's primary-ctor(clock/idGenerator) + `@Inject` secondary
 * constructor pattern so production wiring uses the real wall clock and UUID
 * generator while tests inject deterministic ones.
 */
@Singleton
class VoiceJournalRepository(
    private val audioRecorder: AudioRecorder,
    private val voiceJournalDao: VoiceJournalDao,
    private val transcriptionService: TranscriptionService,
    private val liveTranscriber: LiveTranscriber,
    private val llmService: LlmService,
    private val actionItemDao: ActionItemDao,
    private val clock: () -> Long,
    private val idGenerator: () -> String,
) {

    /**
     * Hilt-visible constructor. Hilt supplies the [AudioRecorder],
     * [VoiceJournalDao], [TranscriptionService], [LiveTranscriber], [LlmService],
     * and [ActionItemDao]; this delegates to the primary constructor with the
     * real wall-clock and UUID generators.
     */
    @Inject
    constructor(
        audioRecorder: AudioRecorder,
        voiceJournalDao: VoiceJournalDao,
        transcriptionService: TranscriptionService,
        liveTranscriber: LiveTranscriber,
        llmService: LlmService,
        actionItemDao: ActionItemDao,
    ) : this(
        audioRecorder = audioRecorder,
        voiceJournalDao = voiceJournalDao,
        transcriptionService = transcriptionService,
        liveTranscriber = liveTranscriber,
        llmService = llmService,
        actionItemDao = actionItemDao,
        clock = System::currentTimeMillis,
        idGenerator = { UUID.randomUUID().toString() },
    )

    /** Whether a recording is currently in progress. */
    val isRecording: Boolean
        get() = audioRecorder.isRecording

    /**
     * Starts capturing audio (Req 10.2) and, when on-device live transcription
     * is available, begins listening to the microphone in parallel so the
     * transcript can be produced offline. The live transcriber is fail-soft and
     * never affects the audio capture path. Returns the audio file path.
     */
    suspend fun startRecording(): String {
        val path = audioRecorder.start()
        runCatching { liveTranscriber.start() }
        return path
    }

    /**
     * Stops the in-progress recording and persists a [VoiceJournalEntry] for
     * [accountId] that references the recorded audio (Req 10.4).
     *
     * The entry starts with `transcript = null` and `transcriptionFailed =
     * false` (transcription is a later task), an empty
     * `extractedActionItemIds`, and a fresh dirty [SyncMeta] so it is uploaded
     * and pushed by sync. Returns the persisted entry.
     */
    suspend fun stopRecording(accountId: String): VoiceJournalEntry {
        val audioRef = audioRecorder.stop()
        // Pull the on-device live transcript, if any. Null means on-device
        // recognition was unavailable/empty, and the caller can fall back to the
        // file-based backend transcription.
        val liveTranscript = runCatching { liveTranscriber.stop() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        val now = clock()
        val entry = VoiceJournalEntry(
            id = idGenerator(),
            accountId = accountId,
            audioRef = audioRef,
            transcript = liveTranscript,
            transcriptionFailed = false,
            createdAt = now,
            extractedActionItemIds = emptyList(),
            sync = SyncMeta(
                updatedAt = now,
                version = 1,
                deleted = false,
                dirty = true,
            ),
        )
        voiceJournalDao.upsert(entry.toEntity())
        return entry
    }

    /**
     * Sends the audio for the persisted entry [entryId] to the
     * Transcription_Service and stores the outcome (Req 10.3, 10.4, 10.8).
     *
     * Loads the entry, calls [TranscriptionService.transcribe] with its
     * `audioRef`, then applies the result with
     * [TranscriptionOutcome.applyTranscription]:
     * - on [TranscriptionResult.Success] the transcript is stored and
     *   `transcriptionFailed` stays false (Req 10.4);
     * - on [TranscriptionResult.Failure] the audio is retained, `transcript`
     *   stays null and `transcriptionFailed` becomes true so the UI can show a
     *   failure message (Req 10.8).
     *
     * The [SyncMeta] is bumped (version incremented, `updatedAt` refreshed,
     * `dirty = true`) so the updated entry is pushed by sync, and the persisted
     * entry is returned so the caller can surface the outcome. Returns null when
     * no entry exists for [entryId].
     */
    suspend fun transcribe(entryId: String): VoiceJournalEntry? {
        val current = voiceJournalDao.getById(entryId)?.toDomain() ?: return null
        val result = transcriptionService.transcribe(current.audioRef)
        val applied = TranscriptionOutcome.applyTranscription(current, result)
        val now = clock()
        val updated = applied.copy(
            sync = current.sync.copy(
                updatedAt = now,
                version = current.sync.version + 1,
                dirty = true,
            ),
        )
        voiceJournalDao.upsert(updated.toEntity())
        return updated
    }

    /**
     * Requests the LLM_Service to extract actionable items from the persisted
     * entry [entryId]'s transcript (Req 10.5) and returns them for the user to
     * confirm (Req 10.6).
     *
     * Loads the entry and, when it has a transcript, calls
     * [LlmService.extractActions] and applies [LlmFailSoft.listOrUnavailable] so
     * the flow always completes: on success the carried [ExtractedAction]s are
     * returned with `unavailable == false`; when the LLM_Service is unavailable
     * or times out an empty list is returned with `unavailable == true`. No
     * Action_Items are created here — extracted items are only candidates the UI
     * presents for confirmation (Req 10.6); creation happens in
     * [confirmExtractedActions] (Req 10.7).
     *
     * Returns null when no entry exists for [entryId] or the entry has no
     * transcript yet (nothing to extract from).
     */
    suspend fun requestExtraction(entryId: String): LlmOutcome<ExtractedAction>? {
        val entry = voiceJournalDao.getById(entryId)?.toDomain() ?: return null
        val transcript = entry.transcript ?: return null
        val outcome = LlmFailSoft.listOrUnavailable(llmService.extractActions(transcript))
        if (!outcome.unavailable && outcome.values.isNotEmpty()) {
            return outcome
        }
        // LLM unavailable (or produced nothing): fall back to the pure on-device
        // heuristic so extraction still works fully offline. We surface its
        // candidates as available, since they were produced successfully.
        val heuristic = HeuristicActionExtraction.extract(transcript)
        return if (heuristic.isNotEmpty()) {
            LlmOutcome(values = heuristic, unavailable = false)
        } else {
            outcome
        }
    }

    /**
     * Creates one Action_Item per user-confirmed extracted action for the entry
     * [entryId], links them back to that originating entry, and persists both
     * (Req 10.7).
     *
     * Delegates the confirmation-gated creation to the pure
     * [VoiceActionExtraction.createConfirmedActionItems]: exactly one
     * [com.sidequest.domain.model.ActionItem] (status `NOT_STARTED`) is built
     * per [confirmed] item with the user-chosen bucket and timeframe, and the
     * entry's `extractedActionItemIds` are extended with the new ids (its sync
     * metadata bumped dirty). The created items are persisted via
     * [ActionItemDao] and the updated entry via [VoiceJournalDao] so the link is
     * pushed by sync. When [confirmed] is empty nothing is created and the entry
     * is left unchanged.
     *
     * Returns null when no entry exists for [entryId].
     */
    suspend fun confirmExtractedActions(
        entryId: String,
        confirmed: List<ConfirmedExtraction>,
    ): ExtractionConfirmationResult? {
        val entry = voiceJournalDao.getById(entryId)?.toDomain() ?: return null
        val result = VoiceActionExtraction.createConfirmedActionItems(
            entry = entry,
            confirmed = confirmed,
            idGenerator = idGenerator,
            now = clock(),
        )
        if (result.createdItems.isEmpty()) {
            return result
        }
        actionItemDao.upsertAll(result.createdItems.map { it.toEntity() })
        voiceJournalDao.upsert(result.updatedEntry.toEntity())
        return result
    }

    /**
     * Cancels an in-progress recording without persisting an entry, releasing
     * the microphone and discarding the partial audio file.
     */
    suspend fun cancelRecording() {
        audioRecorder.cancel()
        runCatching { liveTranscriber.cancel() }
    }

    /**
     * Observes the [accountId]'s voice-journal entries, newest first, as a
     * reactive [Flow] backed by Room so the UI updates as entries are added or
     * updated (Req 10.4).
     */
    fun observeEntries(accountId: String): Flow<List<VoiceJournalEntry>> =
        voiceJournalDao.observeByAccount(accountId)
            .map { entities -> entities.map { it.toDomain() } }
}
