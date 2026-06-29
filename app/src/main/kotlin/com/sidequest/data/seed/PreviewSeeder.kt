package com.sidequest.data.seed

import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.dao.ActionPlanDao
import com.sidequest.data.local.dao.BucketDao
import com.sidequest.data.local.dao.VoiceJournalDao
import com.sidequest.data.local.entity.toEntity
import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionPlan
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.Bucket
import com.sidequest.domain.model.ContentType
import com.sidequest.domain.model.LinkPreview
import com.sidequest.domain.model.SubAction
import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.model.Timeframe
import com.sidequest.domain.model.VoiceJournalEntry
import com.sidequest.ui.capture.CurrentAccountProvider
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Populates the local database with a rich, coherent set of preview data so the
 * whole app can be explored in a working state: several default buckets
 * (Travel, Cooking, Shopping, Daily Rituals, Learning, Vault, Movies & Shows,
 * Appointments, Bills), action items spanning
 * every content type / status / timeframe, action plans with sub-actions, and a
 * transcribed voice-journal entry.
 *
 * The seed is **idempotent**: [seedIfEmpty] does nothing once any bucket exists,
 * so it runs at most once and never clobbers user data. It is invoked from
 * [com.sidequest.SideQuestApp] on startup for debug builds only.
 *
 * All ids are deterministic-free UUIDs and all rows are marked clean (not
 * dirty) so the seed is treated as already-synced local content and isn't
 * pushed to the backend.
 */
@Singleton
class PreviewSeeder @Inject constructor(
    private val bucketDao: BucketDao,
    private val actionItemDao: ActionItemDao,
    private val actionPlanDao: ActionPlanDao,
    private val voiceJournalDao: VoiceJournalDao,
) {

    private val accountId = CurrentAccountProvider.LOCAL_ACCOUNT_ID
    private val now = System.currentTimeMillis()

    /** Seeds preview data only if the bucket table is empty. */
    suspend fun seedIfEmpty() {
        if (bucketDao.getByAccount(accountId).isNotEmpty()) return
        seed()
    }

    private suspend fun seed() {
        val buckets = defaultBuckets()
        bucketDao.insertAll(buckets.map { it.toEntity() })

        val byName = buckets.associateBy { it.name }
        val items = previewItems(byName)
        actionItemDao.insertAll(items.map { it.toEntity() })

        val plans = previewPlans(items)
        actionPlanDao.insertAll(plans.map { it.toEntity() })

        voiceJournalDao.insertAll(previewVoiceEntries().map { it.toEntity() })
    }

    // --- Buckets -----------------------------------------------------------

    /**
     * The default starter buckets, each with a distinct three-color status
     * palette drawn from the SideQuest tonal scheme so the board reads clearly.
     */
    private fun defaultBuckets(): List<Bucket> = DEFAULT_BUCKETS.map { spec ->
        bucket(spec.name, spec.notStartedColor, spec.inProgressColor, spec.completedColor)
    }

    private fun bucket(
        name: String,
        notStarted: String,
        inProgress: String,
        completed: String,
    ): Bucket = Bucket(
        id = UUID.randomUUID().toString(),
        accountId = accountId,
        name = name,
        notStartedColor = notStarted,
        inProgressColor = inProgress,
        completedColor = completed,
        sync = cleanSync(),
    )

    // --- Action items ------------------------------------------------------

    private fun previewItems(buckets: Map<String, Bucket>): List<ActionItem> {
        val travel = buckets.getValue("Travel").id
        val cooking = buckets.getValue("Cooking").id
        val shopping = buckets.getValue("Shopping").id
        val rituals = buckets.getValue("Daily Rituals").id
        val learning = buckets.getValue("Learning").id
        val vault = buckets.getValue("Vault").id
        val movies = buckets.getValue("Movies & Shows").id
        val appointments = buckets.getValue("Appointments").id
        val bills = buckets.getValue("Bills").id

        var offset = 0L
        fun created(): Long = now - (offset++ * 3_600_000L) // stagger by an hour

        return listOf(
            // Travel — a resolved link with a thumbnail, in progress.
            ActionItem(
                id = ITEM_BOOK_FLIGHT,
                accountId = accountId,
                bucketId = travel,
                title = "Book flight to Japan",
                description = "Compare fares for cherry-blossom season.",
                contentType = ContentType.LINK,
                sourceContent = "https://www.expedia.com/flights-to-japan",
                preview = LinkPreview(
                    title = "Cheap Flights to Japan",
                    thumbnailUrl = "https://images.unsplash.com/photo-1490806843957-31f4c9a91c65",
                    sourceName = "Expedia",
                    rawUrl = "https://www.expedia.com/flights-to-japan",
                    resolved = true,
                ),
                timeframe = Timeframe.WithinAWeek,
                status = ActionStatus.IN_PROGRESS,
                createdAt = created(),
                sync = cleanSync(),
            ),
            // Travel — a text item, not started, with a plan.
            ActionItem(
                id = ITEM_PACKING_LIST,
                accountId = accountId,
                bucketId = travel,
                title = "Make a packing list",
                contentType = ContentType.TEXT,
                sourceContent = "Pack light, layered clothing and a universal adapter.",
                timeframe = Timeframe.WithinADay,
                status = ActionStatus.NOT_STARTED,
                createdAt = created(),
                sync = cleanSync(),
            ),
            // Travel — an unresolved link (raw URL fallback), today.
            ActionItem(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                bucketId = travel,
                title = "Reserve a ryokan",
                contentType = ContentType.LINK,
                sourceContent = "https://www.japan-ryokan.net",
                preview = LinkPreview(
                    title = null,
                    thumbnailUrl = null,
                    sourceName = null,
                    rawUrl = "https://www.japan-ryokan.net",
                    resolved = false,
                ),
                timeframe = Timeframe.Today,
                status = ActionStatus.NOT_STARTED,
                createdAt = created(),
                sync = cleanSync(),
            ),

            // Cooking — a video reference, in progress.
            ActionItem(
                id = ITEM_PASTA,
                accountId = accountId,
                bucketId = cooking,
                title = "Try the fresh pasta reel",
                description = "Saved from Instagram.",
                contentType = ContentType.VIDEO_REF,
                sourceContent = "https://www.instagram.com/reel/fresh-pasta",
                preview = LinkPreview(
                    title = "30-minute fresh pasta",
                    thumbnailUrl = "https://images.unsplash.com/photo-1473093295043-cdd812d0e601",
                    sourceName = "Instagram",
                    rawUrl = "https://www.instagram.com/reel/fresh-pasta",
                    resolved = true,
                ),
                timeframe = Timeframe.WithinAWeek,
                status = ActionStatus.IN_PROGRESS,
                createdAt = created(),
                sync = cleanSync(),
            ),
            // Cooking — completed item.
            ActionItem(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                bucketId = cooking,
                title = "Bake sourdough",
                contentType = ContentType.TEXT,
                sourceContent = "Feed the starter the night before.",
                timeframe = Timeframe.Today,
                status = ActionStatus.COMPLETED,
                createdAt = created(),
                sync = cleanSync(),
            ),

            // Shopping — product link with thumbnail, not started, with a plan.
            ActionItem(
                id = ITEM_HEADPHONES,
                accountId = accountId,
                bucketId = shopping,
                title = "Noise-cancelling headphones",
                description = "For the flight.",
                contentType = ContentType.LINK,
                sourceContent = "https://www.example-store.com/headphones",
                preview = LinkPreview(
                    title = "QuietComfort Headphones",
                    thumbnailUrl = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e",
                    sourceName = "Example Store",
                    rawUrl = "https://www.example-store.com/headphones",
                    resolved = true,
                ),
                timeframe = Timeframe.SpecificDate(LocalDate.now().plusDays(10)),
                status = ActionStatus.NOT_STARTED,
                createdAt = created(),
                sync = cleanSync(),
            ),
            // Shopping — image content, completed.
            ActionItem(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                bucketId = shopping,
                title = "Birthday gift idea (screenshot)",
                contentType = ContentType.IMAGE,
                sourceContent = "content://media/screenshot-gift.png",
                timeframe = Timeframe.WithinAWeek,
                status = ActionStatus.COMPLETED,
                createdAt = created(),
                sync = cleanSync(),
            ),

            // Daily Rituals — recurring-feeling tasks.
            ActionItem(
                id = ITEM_MEDITATE,
                accountId = accountId,
                bucketId = rituals,
                title = "Morning meditation",
                contentType = ContentType.TEXT,
                sourceContent = "10 minutes, guided.",
                timeframe = Timeframe.Today,
                status = ActionStatus.COMPLETED,
                createdAt = created(),
                sync = cleanSync(),
            ),
            ActionItem(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                bucketId = rituals,
                title = "Evening journal",
                contentType = ContentType.TEXT,
                sourceContent = "Three things that went well.",
                timeframe = Timeframe.Today,
                status = ActionStatus.NOT_STARTED,
                createdAt = created(),
                sync = cleanSync(),
            ),

            // Learning — article link with plan, in progress.
            ActionItem(
                id = ITEM_GO_COURSE,
                accountId = accountId,
                bucketId = learning,
                title = "Finish the Go tour",
                description = "Concurrency chapter next.",
                contentType = ContentType.LINK,
                sourceContent = "https://go.dev/tour",
                preview = LinkPreview(
                    title = "A Tour of Go",
                    thumbnailUrl = "https://images.unsplash.com/photo-1517694712202-14dd9538aa97",
                    sourceName = "go.dev",
                    rawUrl = "https://go.dev/tour",
                    resolved = true,
                ),
                timeframe = Timeframe.WithinAWeek,
                status = ActionStatus.IN_PROGRESS,
                createdAt = created(),
                sync = cleanSync(),
            ),

            // Vault — stored reference info (a saved bus ticket), no rush.
            ActionItem(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                bucketId = vault,
                title = "Bus ticket — Pune to Mumbai",
                description = "PNR 8842193 · Seat 14A · Shivneri, 6:30 AM.",
                contentType = ContentType.TEXT,
                sourceContent = null,
                timeframe = Timeframe.WithinAWeek,
                status = ActionStatus.NOT_STARTED,
                createdAt = created(),
                sync = cleanSync(),
            ),

            // Movies & Shows — a watchlist item.
            ActionItem(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                bucketId = movies,
                title = "Watch Dune: Part Two",
                contentType = ContentType.TEXT,
                sourceContent = "Recommended by a friend.",
                timeframe = Timeframe.WithinAWeek,
                status = ActionStatus.NOT_STARTED,
                createdAt = created(),
                sync = cleanSync(),
            ),

            // Appointments — a time-bound event.
            ActionItem(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                bucketId = appointments,
                title = "Dentist checkup",
                description = "Dr. Mehta, 11:00 AM.",
                contentType = ContentType.TEXT,
                sourceContent = null,
                timeframe = Timeframe.SpecificDate(LocalDate.now().plusDays(5)),
                status = ActionStatus.NOT_STARTED,
                createdAt = created(),
                sync = cleanSync(),
            ),

            // Bills — a recurring payment due soon.
            ActionItem(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                bucketId = bills,
                title = "Pay electricity bill",
                description = "Due this week.",
                contentType = ContentType.TEXT,
                sourceContent = null,
                timeframe = Timeframe.WithinAWeek,
                status = ActionStatus.NOT_STARTED,
                createdAt = created(),
                sync = cleanSync(),
            ),
        )
    }

    // --- Action plans ------------------------------------------------------

    private fun previewPlans(items: List<ActionItem>): List<ActionPlan> = listOf(
        // A partially-complete plan (shows the progress bar mid-way).
        plan(
            ITEM_PACKING_LIST,
            "Check the weather forecast" to true,
            "Lay out clothes" to true,
            "Pack toiletries" to false,
            "Charge devices" to false,
        ),
        // A fully-complete plan (triggers the "mark item completed" prompt).
        plan(
            ITEM_MEDITATE,
            "Find a quiet spot" to true,
            "Set a 10-minute timer" to true,
            "Breathe" to true,
        ),
        // A just-started plan.
        plan(
            ITEM_HEADPHONES,
            "Compare two models" to true,
            "Read reviews" to false,
            "Check return policy" to false,
        ),
        plan(
            ITEM_GO_COURSE,
            "Finish basics" to true,
            "Do methods chapter" to true,
            "Do concurrency chapter" to false,
        ),
    )

    private fun plan(itemId: String, vararg steps: Pair<String, Boolean>): ActionPlan =
        ActionPlan(
            id = UUID.randomUUID().toString(),
            actionItemId = itemId,
            subActions = steps.mapIndexed { index, (text, done) ->
                SubAction(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    order = index,
                    completed = done,
                )
            },
            sync = cleanSync(),
        )

    // --- Voice journal -----------------------------------------------------

    private fun previewVoiceEntries(): List<VoiceJournalEntry> = listOf(
        VoiceJournalEntry(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            audioRef = "seed://voice/morning-thoughts.m4a",
            transcript = "I want to plan the Japan trip this week, try that fresh " +
                "pasta recipe I saved, and finally order the headphones before the flight.",
            transcriptionFailed = false,
            createdAt = now - 7_200_000L,
            extractedActionItemIds = emptyList(),
            sync = cleanSync(),
        ),
    )

    // --- Helpers -----------------------------------------------------------

    /** A clean (already-synced) sync envelope so seed rows aren't pushed. */
    private fun cleanSync(): SyncMeta = SyncMeta(
        updatedAt = now,
        version = 1,
        deleted = false,
        dirty = false,
    )

    private companion object {
        // Stable ids for items that own plans, so plans link correctly.
        const val ITEM_BOOK_FLIGHT = "seed-item-book-flight"
        const val ITEM_PACKING_LIST = "seed-item-packing-list"
        const val ITEM_PASTA = "seed-item-pasta"
        const val ITEM_HEADPHONES = "seed-item-headphones"
        const val ITEM_MEDITATE = "seed-item-meditate"
        const val ITEM_GO_COURSE = "seed-item-go-course"
    }
}
