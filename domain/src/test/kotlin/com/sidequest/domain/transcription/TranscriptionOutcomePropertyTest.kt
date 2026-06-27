package com.sidequest.domain.transcription

import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.model.VoiceJournalEntry
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test for storing transcription outcomes (Property 20).
 *
 * For any recording, a successful transcription stores a [VoiceJournalEntry]
 * preserving the audio reference and transcript with `transcriptionFailed`
 * false (Req 10.4); a failed transcription retains the audio reference with
 * `transcript == null` and `transcriptionFailed == true` (Req 10.8). In both
 * cases every other field of the entry is left unchanged, which is captured by
 * comparing against the corresponding `entry.copy(...)`.
 *
 * _Requirements: 10.4, 10.8_
 */
class TranscriptionOutcomePropertyTest : StringSpec({

    val arbSyncMeta: Arb<SyncMeta> = arbitrary {
        SyncMeta(
            updatedAt = Arb.long(0L, Long.MAX_VALUE).bind(),
            version = Arb.long(0L, Long.MAX_VALUE).bind(),
            deleted = Arb.boolean().bind(),
            dirty = Arb.boolean().bind(),
        )
    }

    // An arbitrary entry: possibly-non-null existing transcript and arbitrary
    // transcriptionFailed so the test exercises overwriting any prior state.
    val arbEntry: Arb<VoiceJournalEntry> = arbitrary {
        VoiceJournalEntry(
            id = Arb.string(1..36).bind(),
            accountId = Arb.string(1..24).bind(),
            audioRef = Arb.string(1..80).bind(),
            transcript = Arb.string(0..120).orNull().bind(),
            transcriptionFailed = Arb.boolean().bind(),
            createdAt = Arb.long(0L, Long.MAX_VALUE).bind(),
            extractedActionItemIds = Arb.list(Arb.string(1..36), 0..5).bind(),
            sync = arbSyncMeta.bind(),
        )
    }

    val arbResult: Arb<TranscriptionResult> = Arb.choice(
        Arb.string(0..200).map { TranscriptionResult.Success(it) },
        Arb.of<TranscriptionResult>(TranscriptionResult.Failure),
    )

    // Feature: action-tracker-app, Property 20: Transcription outcomes are stored correctly
    "Property 20: transcription outcomes are stored correctly" {
        checkAll(100, arbEntry, arbResult) { entry, result ->
            val outcome = TranscriptionOutcome.applyTranscription(entry, result)

            when (result) {
                is TranscriptionResult.Success -> {
                    outcome.transcript shouldBe result.transcript
                    outcome.transcriptionFailed shouldBe false
                    outcome.audioRef shouldBe entry.audioRef
                    // Every other field is preserved.
                    outcome shouldBe entry.copy(
                        transcript = result.transcript,
                        transcriptionFailed = false,
                    )
                }
                TranscriptionResult.Failure -> {
                    outcome.transcript shouldBe null
                    outcome.transcriptionFailed shouldBe true
                    outcome.audioRef shouldBe entry.audioRef
                    // Every other field is preserved.
                    outcome shouldBe entry.copy(
                        transcript = null,
                        transcriptionFailed = true,
                    )
                }
            }
        }
    }
})
