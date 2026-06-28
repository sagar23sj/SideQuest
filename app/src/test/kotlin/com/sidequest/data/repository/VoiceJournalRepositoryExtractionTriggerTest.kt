package com.sidequest.data.repository

import com.sidequest.data.audio.AudioRecorder
import com.sidequest.data.llm.LlmService
import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.dao.VoiceJournalDao
import com.sidequest.data.local.entity.VoiceJournalEntryEntity
import com.sidequest.data.transcription.LiveTranscriber
import com.sidequest.data.transcription.TranscriptionService
import com.sidequest.domain.llm.ExtractedAction
import com.sidequest.domain.llm.LlmResult
import com.sidequest.domain.model.SyncMeta
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

/**
 * Example test proving that a generated transcript triggers LLM action
 * extraction (Req 10.5).
 *
 * [VoiceJournalRepository.requestExtraction] is the trigger: when a persisted
 * [com.sidequest.domain.model.VoiceJournalEntry] has a transcript, the
 * repository must hand that transcript to [LlmService.extractActions] and
 * surface the result through the pure fail-soft mapping
 * ([com.sidequest.domain.llm.LlmFailSoft.listOrUnavailable]). The
 * transcript is what triggers extraction: an entry with no transcript yet must
 * never reach the LLM_Service. The DAO and [LlmService] are mocked so the test
 * verifies the trigger without any database or network; the other constructor
 * seams are mocked because they are not exercised here, and deterministic
 * clock/idGenerator are injected via the primary constructor.
 *
 * _Requirements: 10.5_
 */
class VoiceJournalRepositoryExtractionTriggerTest : StringSpec({

    val fixedNow = 1_000L

    fun storedEntry(
        id: String = "entry-1",
        transcript: String?,
    ): VoiceJournalEntryEntity =
        VoiceJournalEntryEntity(
            id = id,
            accountId = "account-1",
            audioRef = "audio/$id.m4a",
            transcript = transcript,
            transcriptionFailed = false,
            createdAt = fixedNow,
            extractedActionItemIds = emptyList(),
            sync = SyncMeta(updatedAt = fixedNow, version = 1, deleted = false, dirty = false),
        )

    fun repository(
        voiceJournalDao: VoiceJournalDao,
        llmService: LlmService,
    ): VoiceJournalRepository =
        VoiceJournalRepository(
            audioRecorder = mockk<AudioRecorder>(),
            voiceJournalDao = voiceJournalDao,
            transcriptionService = mockk<TranscriptionService>(),
            liveTranscriber = mockk<LiveTranscriber>(),
            llmService = llmService,
            actionItemDao = mockk<ActionItemDao>(),
            clock = { fixedNow },
            idGenerator = { "generated-id" },
        )

    "a generated transcript triggers LLM extraction with that transcript" {
        runTest {
            val transcript = "buy milk and call mom"
            val extracted = listOf(
                ExtractedAction(title = "Buy milk"),
                ExtractedAction(title = "Call mom", suggestedBucketName = "Family"),
            )
            val voiceJournalDao: VoiceJournalDao = mockk {
                coEvery { getById("entry-1") } returns storedEntry(transcript = transcript)
            }
            val llmService: LlmService = mockk {
                coEvery { extractActions(transcript) } returns LlmResult.Ok(extracted)
            }

            val outcome = repository(voiceJournalDao, llmService).requestExtraction("entry-1")

            // The transcript is the trigger: extraction must be requested with
            // exactly the entry's transcript (Req 10.5).
            coVerify(exactly = 1) { llmService.extractActions(transcript) }
            // ...and the outcome surfaces the LLM result via fail-soft mapping.
            outcome?.unavailable shouldBe false
            outcome?.values shouldBe extracted
        }
    }

    "an entry with no transcript never triggers LLM extraction" {
        runTest {
            val voiceJournalDao: VoiceJournalDao = mockk {
                coEvery { getById("entry-1") } returns storedEntry(transcript = null)
            }
            val llmService: LlmService = mockk()

            val outcome = repository(voiceJournalDao, llmService).requestExtraction("entry-1")

            // With no transcript there is nothing to extract from, so the
            // LLM_Service is never reached and there is no outcome.
            outcome shouldBe null
            coVerify(exactly = 0) { llmService.extractActions(any()) }
        }
    }
})
