package com.actiontracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.actiontracker.data.local.entity.toActionItems
import com.actiontracker.data.local.entity.toActionPlans
import com.actiontracker.data.local.entity.toBuckets
import com.actiontracker.data.local.entity.toDomain
import com.actiontracker.data.local.entity.toEntity
import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionPlan
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.Bucket
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.LinkPreview
import com.actiontracker.domain.model.SubAction
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe
import com.actiontracker.domain.model.WishlistFields
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

/**
 * Property-based test for the Room persistence layer round trip.
 *
 * Why Robolectric + JUnit4: Property 31 exercises real [androidx.room.Room]
 * persistence, which needs an Android/SQLite runtime. Robolectric provides that
 * runtime as a JVM unit test (no device/emulator). The JUnit4 Robolectric
 * runner runs on the JUnit Platform via the Vintage engine, and
 * [io.kotest.property.checkAll] supplies the property generators and the
 * minimum 100 iterations from inside each [Test] method.
 *
 * To faithfully simulate an app restart, every reload opens a brand-new
 * [ActionTrackerDatabase] connection against the same on-disk database file:
 * the previous connection is closed (as if the process were killed) before the
 * data is read back.
 *
 * _Requirements: 14.2, 14.3, 3.4_
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PersistenceRoundTripPropertyTest {

    private val openDatabases = mutableListOf<ActionTrackerDatabase>()

    @After
    fun tearDown() {
        openDatabases.forEach { runCatching { it.close() } }
        openDatabases.clear()
    }

    /**
     * Opens a fresh file-backed database connection for [dbName]. Each call is
     * a new connection to the same file, so closing one and opening another
     * simulates the app being killed and relaunched.
     */
    private fun openDatabase(dbName: String): ActionTrackerDatabase {
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ActionTrackerDatabase::class.java,
            dbName,
        ).allowMainThreadQueries().build()
        openDatabases.add(db)
        return db
    }

    // Feature: action-tracker-app, Property 31: Persistence round trip survives restart, edits, and deletes
    @Test
    fun property31_persistenceRoundTripSurvivesRestartEditsAndDeletes() = runBlocking {
        checkAll(100, datasetArb) { dataset ->
            // Unique file per iteration so each round trip starts from a clean
            // on-disk database and previous iterations cannot leak state.
            val dbName = "round_trip_${UUID.randomUUID()}.db"

            // 1) WRITE: persist every entity through the DAOs, then "kill" the
            //    process by closing the connection.
            run {
                val db = openDatabase(dbName)
                db.bucketDao().insertAll(dataset.buckets.map { it.toEntity() })
                db.actionItemDao().insertAll(dataset.items.map { it.toEntity() })
                dataset.plans.forEach { db.actionPlanDao().insert(it.toEntity()) }
                db.close()
            }

            // 2) RELOAD AFTER RESTART: reopen the file and assert the reloaded
            //    records are identical to what was written (Req 14.2).
            run {
                val db = openDatabase(dbName)
                db.bucketDao().getAll().toBuckets().toSet() shouldBe dataset.buckets.toSet()
                db.actionItemDao().getAll().toActionItems().toSet() shouldBe dataset.items.toSet()
                db.actionPlanDao().getAll().toActionPlans().toSet() shouldBe dataset.plans.toSet()
                db.close()
            }

            // 3) EDIT: apply edits to one of each entity (when present), persist
            //    them, and restart again to confirm the edits survive (Req 14.3).
            val editedBucket = dataset.buckets.firstOrNull()?.let(::editBucket)
            val editedItem = dataset.items.firstOrNull()?.let(::editItem)
            val editedPlan = dataset.plans.firstOrNull()?.let(::editPlan)
            run {
                val db = openDatabase(dbName)
                editedBucket?.let { db.bucketDao().update(it.toEntity()) }
                editedItem?.let { db.actionItemDao().update(it.toEntity()) }
                editedPlan?.let { db.actionPlanDao().update(it.toEntity()) }
                db.close()
            }
            run {
                val db = openDatabase(dbName)
                editedBucket?.let { db.bucketDao().getById(it.id)?.toDomain() shouldBe it }
                editedItem?.let { db.actionItemDao().getById(it.id)?.toDomain() shouldBe it }
                editedPlan?.let { db.actionPlanDao().getById(it.id)?.toDomain() shouldBe it }
                db.close()
            }

            // 4) DELETE: remove one of each entity (when present), then restart
            //    and confirm the record is absent while the rest remain (Req 14.3).
            val deletedItemId = dataset.items.firstOrNull()?.id
            val deletedBucketId = dataset.buckets.lastOrNull()?.id
            val deletedPlanId = dataset.plans.firstOrNull()?.id
            run {
                val db = openDatabase(dbName)
                deletedItemId?.let { db.actionItemDao().deleteById(it) }
                deletedBucketId?.let { db.bucketDao().deleteById(it) }
                deletedPlanId?.let { db.actionPlanDao().deleteById(it) }
                db.close()
            }
            run {
                val db = openDatabase(dbName)
                deletedItemId?.let {
                    db.actionItemDao().getById(it).shouldBeNull()
                    db.actionItemDao().getAll().any { row -> row.id == it } shouldBe false
                }
                deletedBucketId?.let {
                    db.bucketDao().getById(it).shouldBeNull()
                    db.bucketDao().getAll().any { row -> row.id == it } shouldBe false
                }
                deletedPlanId?.let {
                    db.actionPlanDao().getById(it).shouldBeNull()
                    db.actionPlanDao().getAll().any { row -> row.id == it } shouldBe false
                }
                db.close()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Test data + generators
// ---------------------------------------------------------------------------

/**
 * A coherent set of entities to persist together: [items] reference [buckets]
 * by id and [plans] reference [items] by id, mirroring real app data.
 */
private data class Dataset(
    val buckets: List<Bucket>,
    val items: List<ActionItem>,
    val plans: List<ActionPlan>,
)

private fun nonBlankString(maxLen: Int = 20): Arb<String> =
    Arb.string(1, maxLen).map { it.ifBlank { "x" } }

/** All [Timeframe] variants, including [Timeframe.SpecificDate] (Req 3.4). */
private val timeframeArb: Arb<Timeframe> = Arb.choice(
    Arb.constant(Timeframe.Today),
    Arb.constant(Timeframe.WithinADay),
    Arb.constant(Timeframe.WithinAWeek),
    Arb.long(0L, 60_000L).map { Timeframe.SpecificDate(LocalDate.ofEpochDay(it)) },
)

private val contentTypeArb: Arb<ContentType> = Arb.of(ContentType.entries)

private val statusArb: Arb<ActionStatus> = Arb.of(ActionStatus.entries)

private fun syncMetaArb(): Arb<SyncMeta> = arbitrary {
    SyncMeta(
        updatedAt = Arb.long(0L, Long.MAX_VALUE / 2).bind(),
        version = Arb.long(1L, 100L).bind(),
        deleted = false,
        dirty = Arb.boolean().bind(),
    )
}

private fun bucketArb(): Arb<Bucket> = arbitrary {
    Bucket(
        id = UUID.randomUUID().toString(),
        accountId = "acct-1",
        name = nonBlankString().bind(),
        isShopping = Arb.boolean().bind(),
        notStartedColor = "#9E9E9E",
        inProgressColor = "#1976D2",
        completedColor = "#388E3C",
        sync = syncMetaArb().bind(),
    )
}

private fun subActionArb(order: Int): Arb<SubAction> = arbitrary {
    SubAction(
        id = UUID.randomUUID().toString(),
        text = nonBlankString().bind(),
        order = order,
        completed = Arb.boolean().bind(),
    )
}

/** Builds an [ActionItem] in [bucketId] covering content/wishlist variations. */
private fun actionItemArb(bucketId: String): Arb<ActionItem> = arbitrary {
    val contentType = contentTypeArb.bind()
    val status = statusArb.bind()
    val isWishlist = Arb.boolean().bind()
    val title = nonBlankString().bind()
    ActionItem(
        id = UUID.randomUUID().toString(),
        accountId = "acct-1",
        bucketId = bucketId,
        title = title,
        description = Arb.string(0, 15).orNull().bind()?.ifBlank { null },
        contentType = contentType,
        sourceContent = "src:${UUID.randomUUID()}",
        preview = if (contentType == ContentType.LINK) {
            LinkPreview(
                title = Arb.string(1, 10).orNull().bind(),
                thumbnailUrl = Arb.string(1, 10).map { "https://img/$it" }.orNull().bind(),
                sourceName = Arb.string(1, 10).orNull().bind(),
                rawUrl = "https://example.com/${UUID.randomUUID()}",
                resolved = Arb.boolean().bind(),
            )
        } else {
            null
        },
        timeframe = timeframeArb.bind(),
        status = status,
        createdAt = Arb.long(0L, 4_000_000_000_000L).bind(),
        isWishlistItem = isWishlist,
        wishlist = if (isWishlist) {
            WishlistFields(
                productName = "product:$title",
                sourceLink = Arb.string(1, 10).map { "https://shop/$it" }.orNull().bind(),
                purchased = status == ActionStatus.COMPLETED,
            )
        } else {
            null
        },
        sync = syncMetaArb().bind(),
    )
}

private val datasetArb: Arb<Dataset> = arbitrary {
    // 1..4 buckets with unique ids (UUIDs are already unique).
    val buckets = Arb.list(bucketArb(), 1..4).bind()

    // 0..6 items spread across the generated buckets.
    val itemCount = Arb.int(0..6).bind()
    val items = (0 until itemCount).map {
        val bucket = Arb.of(buckets).bind()
        actionItemArb(bucket.id).bind()
    }

    // A plan for up to the first 3 items, each with 1..4 ordered sub-actions.
    val plans = items.take(3).map { item ->
        val n = Arb.int(1..4).bind()
        val subs = (0 until n).map { subActionArb(it).bind() }
        ActionPlan(
            id = UUID.randomUUID().toString(),
            actionItemId = item.id,
            subActions = subs,
            sync = syncMetaArb().bind(),
        )
    }

    Dataset(buckets = buckets, items = items, plans = plans)
}

// ---------------------------------------------------------------------------
// Edit helpers — produce a modified copy whose fields differ from the original.
// ---------------------------------------------------------------------------

private fun editBucket(bucket: Bucket): Bucket = bucket.copy(
    name = bucket.name + "-edited",
    isShopping = !bucket.isShopping,
    inProgressColor = "#FF5722",
    sync = bucket.sync.copy(version = bucket.sync.version + 1, dirty = true),
)

private fun editItem(item: ActionItem): ActionItem = item.copy(
    title = item.title + "-edited",
    status = when (item.status) {
        ActionStatus.NOT_STARTED -> ActionStatus.IN_PROGRESS
        ActionStatus.IN_PROGRESS -> ActionStatus.COMPLETED
        ActionStatus.COMPLETED -> ActionStatus.NOT_STARTED
    },
    timeframe = Timeframe.SpecificDate(LocalDate.ofEpochDay(12_345L)),
    sync = item.sync.copy(version = item.sync.version + 1, dirty = true),
)

private fun editPlan(plan: ActionPlan): ActionPlan = plan.copy(
    subActions = plan.subActions.map { it.copy(completed = !it.completed) },
    sync = plan.sync.copy(version = plan.sync.version + 1, dirty = true),
)
