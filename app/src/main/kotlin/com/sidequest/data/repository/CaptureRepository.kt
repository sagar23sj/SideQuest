package com.sidequest.data.repository

import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.entity.toEntity
import com.sidequest.data.preview.PreviewEnqueuer
import com.sidequest.data.reminder.TaskReminderScheduler
import com.sidequest.domain.capture.CaptureDraft
import com.sidequest.domain.capture.CaptureOperations
import com.sidequest.domain.capture.ClassificationResult
import com.sidequest.domain.capture.SharedIntentData
import com.sidequest.domain.capture.classify
import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ContentType
import com.sidequest.domain.model.Timeframe
import com.sidequest.domain.reminder.TaskReminderDefaults
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of beginning a capture from shared content.
 *
 * Classification runs first (Req 1.2): supported content yields a
 * [CaptureDraft] ready for bucket/timeframe selection, while unsupported
 * content is rejected (Req 1.4) so the caller can show a "content type not
 * supported" message and discard the share.
 */
sealed interface BeginCaptureResult {

    /** The shared content is supported; [draft] feeds the categorization sheet. */
    data class Draft(val draft: CaptureDraft) : BeginCaptureResult

    /** The shared content is in an unsupported format and must be discarded (Req 1.4). */
    data object Unsupported : BeginCaptureResult
}

/**
 * Repository for the offline capture flow (Req 1.5, 3.4).
 *
 * The repository is intentionally thin: the confirm-capture computation that
 * turns a [CaptureDraft] plus the selected bucket and timeframe into a
 * not-started Action_Item lives in the pure `:domain`
 * [CaptureOperations.buildActionItem], so it is portable and validated by
 * Property 2 without Android. Here we classify shared content, build the draft,
 * delegate item construction to the domain, and persist via [ActionItemDao].
 * Writes mark the row dirty so the offline-first sync layer pushes the newly
 * captured item.
 *
 * Link-preview enrichment (Req 1a) and the ShareTargetActivity/categorization
 * sheet are later tasks, so [beginCapture] leaves [CaptureDraft.preview] null
 * and confirm-capture stores the raw source content as-is.
 */
@Singleton
class CaptureRepository(
    private val actionItemDao: ActionItemDao,
    private val previewEnqueuer: PreviewEnqueuer,
    private val taskReminderScheduler: TaskReminderScheduler,
    private val clock: () -> Long,
    private val idGenerator: () -> String,
    private val copyMedia: (String) -> String? = { null },
) {

    /**
     * Hilt-visible constructor. Hilt supplies the DAOs, the [PreviewEnqueuer],
     * the [TaskReminderScheduler], and the app [android.content.Context] (used to
     * copy shared media into app-internal storage). Tests use the primary
     * constructor to inject deterministic [clock]/[idGenerator] and a fake
     * [copyMedia].
     */
    @Inject
    constructor(
        actionItemDao: ActionItemDao,
        previewEnqueuer: PreviewEnqueuer,
        taskReminderScheduler: TaskReminderScheduler,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
    ) : this(
        actionItemDao = actionItemDao,
        previewEnqueuer = previewEnqueuer,
        taskReminderScheduler = taskReminderScheduler,
        clock = System::currentTimeMillis,
        idGenerator = { UUID.randomUUID().toString() },
        copyMedia = { uriString -> copyMediaToStorage(context, uriString) },
    )

    /**
     * Classifies [intentData] and, when supported, builds a [CaptureDraft] for
     * [accountId] ready for bucket and timeframe selection (Req 1.2).
     *
     * Unsupported content returns [BeginCaptureResult.Unsupported] so the caller
     * discards it with a "not supported" message (Req 1.4). The draft's title
     * and source content are derived from the shared payload; preview enrichment
     * is a later task, so [CaptureDraft.preview] is left null here.
     */
    suspend fun beginCapture(
        accountId: String,
        intentData: SharedIntentData,
    ): BeginCaptureResult =
        when (val classification = classify(intentData)) {
            is ClassificationResult.Supported -> BeginCaptureResult.Draft(
                buildDraft(accountId, intentData, classification.contentType),
            )

            ClassificationResult.Unsupported -> BeginCaptureResult.Unsupported
        }

    /**
     * Confirms a capture: creates a not-started Action_Item that stores the
     * selected [bucketId] and [timeframe], then persists it (Req 1.5, 3.4).
     *
     * The Action_Item is built by the pure [CaptureOperations.buildActionItem]
     * (status [com.sidequest.domain.model.ActionStatus.NOT_STARTED], bucket
     * and timeframe preserved exactly), with the id and timestamp injected from
     * this repository's generators.
     *
     * For LINK items the repository schedules background preview enrichment via
     * [PreviewEnqueuer] *after* the item is persisted. Enqueuing only schedules
     * work and returns immediately, so it runs off the capture critical path and
     * does not block this method from returning the item synchronously
     * (Req 1a.3, 1a.5). The worker later merges the fetched preview and upserts
     * the item, which the Board observes reactively through Room-backed Flows.
     *
     * Returns the persisted domain [ActionItem].
     */
    suspend fun confirmCapture(
        draft: CaptureDraft,
        bucketId: String,
        timeframe: Timeframe,
    ): ActionItem {
        val nowMs = clock()

        // Copy shared media (image/video) out of the transient content:// URI
        // into app-internal storage so it survives the URI grant lapsing, app
        // restarts, and reinstalls — then store that stable path. Falls back to
        // the original reference if the copy fails.
        val isMedia = draft.contentType == ContentType.IMAGE ||
            draft.contentType == ContentType.VIDEO_REF
        val effectiveDraft = if (isMedia && !draft.sourceContent.isNullOrBlank()) {
            draft.copy(sourceContent = copyMedia(draft.sourceContent!!) ?: draft.sourceContent)
        } else {
            draft
        }

        val base = CaptureOperations.buildActionItem(
            draft = effectiveDraft,
            bucketId = bucketId,
            timeframe = timeframe,
            id = idGenerator(),
            now = nowMs,
        )

        // Give every captured quest a concrete default reminder derived from the
        // vague timeframe the user picked, so a reminder is actually configured
        // (the user can edit/remove it later on the detail screen).
        val zonedNow = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
        val item = base.copy(reminder = TaskReminderDefaults.forTimeframe(timeframe, zonedNow))

        actionItemDao.upsert(item.toEntity())

        // Off the critical path: schedule preview enrichment for LINK items. The
        // source may hold several newline-joined links, so enqueue the first URL
        // for preview (Req 1a.5).
        if (item.contentType == ContentType.LINK) {
            com.sidequest.domain.capture.firstUrlOrNull(item.sourceContent)?.let { url ->
                previewEnqueuer.enqueue(actionItemId = item.id, url = url)
            }
        }

        // Arm the default reminder's alarm (no-op when it has no future trigger).
        taskReminderScheduler.schedule(item)

        return item
    }

    /**
     * Derives a [CaptureDraft] from classified shared content. The title is a
     * short, recognizable label (the shared text/link, or a media placeholder)
     * and the raw payload is preserved as the source content for later display
     * and enrichment.
     */
    private fun buildDraft(
        accountId: String,
        intentData: SharedIntentData,
        contentType: ContentType,
    ): CaptureDraft {
        val sourceContent = when (contentType) {
            // Keep the full shared text intact for both links and plain text;
            // the capture flow puts the whole text in the description and pulls
            // out the first URL for the link field.
            ContentType.LINK, ContentType.TEXT -> intentData.text
            ContentType.IMAGE, ContentType.VIDEO_REF -> intentData.uri
        }
        return CaptureDraft(
            accountId = accountId,
            title = deriveTitle(intentData, contentType),
            contentType = contentType,
            sourceContent = sourceContent,
            preview = null,
        )
    }

    /**
     * Builds a recognizable, non-blank title from the shared content. Falls back
     * to a type-based placeholder when no text is available (for media shares).
     */
    private fun deriveTitle(
        intentData: SharedIntentData,
        contentType: ContentType,
    ): String {
        val text = intentData.text?.trim()
        if (!text.isNullOrEmpty()) {
            return text.lineSequence().first().take(MAX_TITLE_LENGTH)
        }
        return when (contentType) {
            ContentType.IMAGE -> "Shared image"
            ContentType.VIDEO_REF -> "Shared video"
            ContentType.LINK -> "Shared link"
            ContentType.TEXT -> "Shared text"
        }
    }

    private companion object {
        /** Cap on a derived title length so a long share does not become the title. */
        const val MAX_TITLE_LENGTH = 100

        /**
         * Copies the content at [uriString] into the app's internal `media`
         * directory and returns the absolute file path, or null on failure (the
         * caller then keeps the original reference). This frees the item from the
         * transient share-time URI grant.
         */
        fun copyMediaToStorage(context: android.content.Context, uriString: String): String? =
            runCatching {
                val uri = android.net.Uri.parse(uriString)
                val dir = java.io.File(context.filesDir, "media").apply { mkdirs() }
                val ext = context.contentResolver.getType(uri)
                    ?.substringAfter('/', "")
                    ?.takeIf { it.isNotBlank() }
                    ?: "bin"
                val dest = java.io.File(dir, "media_${UUID.randomUUID()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                dest.absolutePath
            }.getOrNull()
    }
}
