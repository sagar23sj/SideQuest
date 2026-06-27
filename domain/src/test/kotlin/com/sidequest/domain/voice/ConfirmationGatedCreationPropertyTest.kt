package com.sidequest.domain.voice

import com.sidequest.domain.llm.ExtractedAction
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.model.Timeframe
import com.sidequest.domain.model.VoiceJournalEntry
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.LocalDate

/**
 * Property-based test for confirmation-gated action creation (Property 19).
 *
 * Action extraction produces candidate [ExtractedAction]s, but they create no
 * Action_Items on their own (Req 10.6) — only the user-confirmed subset becomes
 * Action_Items, one per confirmed item, each linked back to the originating
 * [VoiceJournalEntry] (Req 10.7).
 *
 * The "no Action_Items exist before confirmation" half is modeled by confirming
 * the empty subset: zero items are created and the entry's links are unchanged.
 * The "after confirming a subset S" half generates a full extracted set, chooses
 * an arbitrary subset to confirm (each paired with a bucket + timeframe), and
 * checks one not-started item is created per confirmed item, in order, with the
 * chosen bucket/timeframe, unique ids, and the ids appended (not replaced) to the
 * entry's existing links.
 *
 * _Requirements: 10.6, 10.7_
 */
class ConfirmationGatedCreationPropertyTest : StringSpec({

    val fixedNow = 1_700_000_000_000L

    fun syncMeta(version: Long): SyncMeta =
        SyncMeta(updatedAt = 0L, version = version, deleted = false, dirty = false)

    // A deterministic id generator: "item-0", "item-1", ... so created item ids
    // are predictable and verifiably unique.
    fun deterministicIdGenerator(): () -> String {
        var counter = 0
        return { "item-${counter++}" }
    }

    val arbTimeframe: Arb<Timeframe> =
        Arb.element(
            Timeframe.Today,
            Timeframe.WithinADay,
            Timeframe.WithinAWeek,
            Timeframe.SpecificDate(LocalDate.of(2030, 1, 15)),
        )

    // An originating entry with an arbitrary set of pre-existing links, so we can
    // verify confirmed ids are APPENDED rather than replacing existing links.
    val arbEntry: Arb<VoiceJournalEntry> =
        Arb.bind(
            Arb.string(1..12),
            Arb.string(0..40),
            Arb.list(Arb.string(1..8).map { "pre-$it" }, 0..5),
            Arb.long(0L..1L),
        ) { accountId, transcript, preExistingIds, versionSeed ->
            VoiceJournalEntry(
                id = "entry-1",
                accountId = "acct-$accountId",
                audioRef = "audio/entry-1.m4a",
                transcript = transcript,
                transcriptionFailed = false,
                createdAt = 1_699_000_000_000L,
                extractedActionItemIds = preExistingIds,
                sync = syncMeta(version = versionSeed + 1L),
            )
        }

    // The full extracted set paired with, per action, whether the user confirms
    // it plus the bucket/timeframe they would choose. Confirming the empty subset
    // models "before confirmation".
    val arbExtractionScenario: Arb<Triple<VoiceJournalEntry, List<ExtractedAction>, List<ConfirmedExtraction>>> =
        Arb.bind(
            arbEntry,
            Arb.list(
                Arb.bind(
                    Arb.string(0..30),
                    Arb.boolean(),
                    Arb.int(0..1000),
                    arbTimeframe,
                ) { title, confirmed, bucketSeed, timeframe ->
                    ExtractionChoice(
                        action = ExtractedAction(title = title),
                        confirmed = confirmed,
                        bucketId = "bucket-$bucketSeed",
                        timeframe = timeframe,
                    )
                },
                0..12,
            ),
        ) { entry, choices ->
            val confirmed = choices
                .filter { it.confirmed }
                .map { choice ->
                    ConfirmedExtraction(
                        action = choice.action,
                        bucketId = choice.bucketId,
                        timeframe = choice.timeframe,
                    )
                }
            Triple(entry, choices.map { it.action }, confirmed)
        }

    // Feature: action-tracker-app, Property 19: Extracted actions are created only on confirmation, one per confirmed item
    "Property 19: actions are created only on confirmation, one per confirmed item, linked to the entry" {
        checkAll(100, arbExtractionScenario) { (entry, _, confirmed) ->
            // "No Action_Items exist before confirmation": confirming the empty
            // subset creates zero items and leaves the entry's links unchanged.
            val before = VoiceActionExtraction.createConfirmedActionItems(
                entry = entry,
                confirmed = emptyList(),
                idGenerator = deterministicIdGenerator(),
                now = fixedNow,
            )
            before.createdItems shouldBe emptyList()
            before.updatedEntry shouldBe entry

            // After confirming subset S of size n.
            val result = VoiceActionExtraction.createConfirmedActionItems(
                entry = entry,
                confirmed = confirmed,
                idGenerator = deterministicIdGenerator(),
                now = fixedNow,
            )

            // Exactly |S| items are created.
            result.createdItems.size shouldBe confirmed.size

            // Each created item is not-started, belongs to the entry's account,
            // and preserves the chosen bucket/timeframe in order.
            result.createdItems.forEachIndexed { index, item ->
                val source = confirmed[index]
                item.status shouldBe ActionStatus.NOT_STARTED
                item.accountId shouldBe entry.accountId
                item.bucketId shouldBe source.bucketId
                item.timeframe shouldBe source.timeframe
            }

            // Every created item id is unique.
            val createdIds = result.createdItems.map { it.id }
            createdIds.toSet().size shouldBe createdIds.size

            // Created ids are appended to (not replacing) the entry's existing
            // links, keeping them tied to the originating entry.
            result.updatedEntry.extractedActionItemIds shouldContainExactly
                (entry.extractedActionItemIds + createdIds)
        }
    }
}) {
    /**
     * Per-extracted-action generator output: the action, whether the user
     * confirms it, and the bucket/timeframe they would assign on confirmation.
     */
    private data class ExtractionChoice(
        val action: ExtractedAction,
        val confirmed: Boolean,
        val bucketId: String,
        val timeframe: Timeframe,
    )
}
