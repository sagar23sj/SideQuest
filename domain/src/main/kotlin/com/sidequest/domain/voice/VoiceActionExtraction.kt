package com.sidequest.domain.voice

import com.sidequest.domain.capture.CaptureDraft
import com.sidequest.domain.capture.CaptureOperations
import com.sidequest.domain.llm.ExtractedAction
import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ContentType
import com.sidequest.domain.model.Timeframe
import com.sidequest.domain.model.VoiceJournalEntry

/**
 * A single extracted action the user has confirmed, together with the bucket and
 * timeframe they chose for it (Req 10.7).
 *
 * The extraction → confirmation → creation pipeline is modeled as pure data so
 * the key invariant (Property 19) is testable without Android, LLM, or Room:
 * the LLM_Service produces a list of [ExtractedAction]s (Req 10.5), the user is
 * shown those items for confirmation (Req 10.6), and only the confirmed subset —
 * each paired here with a chosen [bucketId] and [timeframe] — is turned into
 * Action_Items (Req 10.7).
 *
 * @property action the extracted action the user confirmed.
 * @property bucketId the bucket the user assigned to this action; stored verbatim
 *   on the resulting Action_Item.
 * @property timeframe the timeframe the user assigned; stored verbatim. Callers
 *   are expected to have validated it (Req 3.2, 3.3) before confirming.
 */
data class ConfirmedExtraction(
    val action: ExtractedAction,
    val bucketId: String,
    val timeframe: Timeframe,
)

/**
 * The outcome of confirming a subset of extracted actions: the Action_Items that
 * were created and the originating [VoiceJournalEntry] updated to link them.
 *
 * @property createdItems exactly one [ActionItem] per confirmed extraction, in
 *   the same order, each with status `NOT_STARTED` (Req 10.7) and
 *   `accountId`/source carried from the originating entry.
 * @property updatedEntry the originating entry with the new item ids appended to
 *   [VoiceJournalEntry.extractedActionItemIds], so persistence can link the
 *   created items back to the entry that produced them.
 */
data class ExtractionConfirmationResult(
    val createdItems: List<ActionItem>,
    val updatedEntry: VoiceJournalEntry,
)

/**
 * Pure extract-and-confirm logic for voice journaling — the heart of Property 19.
 *
 * Action extraction is an LLM_Service call that yields candidate
 * [ExtractedAction]s (Req 10.5); those candidates are *presented for
 * confirmation* and create no Action_Items on their own (Req 10.6). Only when the
 * user confirms a subset — each item paired with a chosen bucket and timeframe —
 * are Action_Items created, one per confirmed item, each linked back to the
 * originating [VoiceJournalEntry] (Req 10.7).
 *
 * This object lives in `:domain` with no LLM/network/Room dependency so the
 * confirmation-gated creation invariant is validated directly: given a list of
 * confirmed items, [createConfirmedActionItems] produces exactly that many
 * Action_Items and reports their ids for linking. The `:app` layer keeps the LLM
 * call and persistence thin around it.
 *
 * Every function here is pure and total: it never mutates its inputs, never
 * throws, and injects [idGenerator]/`now` so creation stays deterministic and
 * testable.
 */
object VoiceActionExtraction {

    /**
     * Creates exactly one [ActionItem] per [confirmed] extraction, each linked to
     * the originating [entry] (Req 10.7).
     *
     * Each created item starts with status `NOT_STARTED`, belongs to the entry's
     * `accountId`, stores the chosen bucket and timeframe verbatim, and carries
     * the extracted title plus the entry's transcript as source content. The
     * resulting ids are appended to the entry's `extractedActionItemIds` (without
     * dropping any already linked) and the entry's sync metadata is bumped dirty
     * so the link is pushed by sync.
     *
     * When [confirmed] is empty no items are created and the entry's links are
     * unchanged — the function never invents items for unconfirmed extractions,
     * which is the confirmation gate Property 19 exercises.
     *
     * @param entry the originating Voice_Journal_Entry whose transcript was
     *   extracted from.
     * @param confirmed the user-confirmed subset of extracted actions, each with
     *   a chosen bucket and timeframe.
     * @param idGenerator supplies a fresh client-generated id per created item.
     * @param now epoch milliseconds used for each item's `createdAt`/sync clock
     *   and the entry's bumped `updatedAt`.
     * @return the created items (one per confirmed extraction, in order) and the
     *   entry updated to link them.
     */
    fun createConfirmedActionItems(
        entry: VoiceJournalEntry,
        confirmed: List<ConfirmedExtraction>,
        idGenerator: () -> String,
        now: Long,
    ): ExtractionConfirmationResult {
        val createdItems = confirmed.map { confirmation ->
            CaptureOperations.buildActionItem(
                draft = draftFor(entry, confirmation.action),
                bucketId = confirmation.bucketId,
                timeframe = confirmation.timeframe,
                id = idGenerator(),
                now = now,
            )
        }

        if (createdItems.isEmpty()) {
            return ExtractionConfirmationResult(
                createdItems = emptyList(),
                updatedEntry = entry,
            )
        }

        val updatedEntry = entry.copy(
            extractedActionItemIds = entry.extractedActionItemIds + createdItems.map { it.id },
            sync = entry.sync.copy(
                updatedAt = now,
                version = entry.sync.version + 1,
                dirty = true,
            ),
        )

        return ExtractionConfirmationResult(
            createdItems = createdItems,
            updatedEntry = updatedEntry,
        )
    }

    /**
     * Builds the [CaptureDraft] an extracted action becomes before it is turned
     * into an Action_Item, reusing the shared confirm-capture builder.
     *
     * The draft inherits the entry's `accountId`, uses the extracted title, and
     * is classified as [ContentType.TEXT] with the entry's transcript as source
     * content so the created item traces back to what was spoken.
     */
    private fun draftFor(entry: VoiceJournalEntry, action: ExtractedAction): CaptureDraft =
        CaptureDraft(
            accountId = entry.accountId,
            title = action.title,
            contentType = ContentType.TEXT,
            sourceContent = entry.transcript,
            preview = null,
        )
}
