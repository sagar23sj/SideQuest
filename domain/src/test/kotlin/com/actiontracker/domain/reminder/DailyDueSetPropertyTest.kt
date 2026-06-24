package com.actiontracker.domain.reminder

import com.actiontracker.domain.model.ActionItem
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.SyncMeta
import com.actiontracker.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Property-based test for the daily due-set (Property 12).
 *
 * For any set of [ActionItem]s and any target date, [DueSet.dueOn] returns
 * exactly those items whose timeframe resolves to that date — today maps to the
 * capture date, within-a-day to capture + 1, within-a-week to capture + 7, and
 * a specific date to the chosen calendar date (Req 6.4).
 *
 * The membership predicate is reimplemented independently here (a plain filter
 * over the same resolution mapping) and the result is asserted equal to
 * [DueSet.dueOn]'s output, then both directions of "exactly" are checked: no
 * not-due item appears in the result and every due item does.
 *
 * Determinism: a fixed [ZoneOffset.UTC] zone is used so the test never depends
 * on the host time zone or DST transitions. Each item's `createdAt` is built
 * from a capture date offset a small number of days from the generated target
 * date plus an intra-day millisecond offset, so capture dates, within-a-day,
 * within-a-week, and specific-date timeframes frequently land on — and off —
 * the target date, exercising membership in both directions. Target dates stay
 * comfortably inside [LocalDate]'s bounds so resolving capture + 7 days never
 * overflows.
 *
 * _Requirements: 6.4_
 */
class DailyDueSetPropertyTest : StringSpec({

    val zone = ZoneOffset.UTC

    // Days a LocalDate can represent; keep target dates within a range whose
    // start-of-day epoch millis (and capture + 7 days) stays comfortably inside
    // Long, since LocalDate spans far more than epoch-millis can hold.
    val arbToday: Arb<LocalDate> =
        Arb.long(-3_000_000L, 3_000_000L).map(LocalDate::ofEpochDay)

    // Milliseconds within a single UTC day, so the capture date recovered from
    // `createdAt` equals the intended capture date regardless of time of day.
    val millisOfDay: Arb<Long> = Arb.long(0L, 86_399_999L)

    // A generated, today-independent description of one item. Offsets are kept
    // small so timeframes resolve near the target date often enough to exercise
    // both "due" and "not due" outcomes.
    data class ItemSpec(
        val idSeed: Int,
        val kind: Timeframe,
        val createdOffsetDays: Int,
        val millisInDay: Long,
        val specificOffsetDays: Int,
        val status: ActionStatus,
    )

    val arbSpec: Arb<ItemSpec> = Arb.bind(
        Arb.int(0..1_000_000),
        Arb.int(0..3),
        Arb.int(-10..10),
        millisOfDay,
        Arb.int(-10..10),
        Arb.enum<ActionStatus>(),
    ) { idSeed, kindIndex, createdOffset, millis, specificOffset, status ->
        // Placeholder for the SpecificDate date; the real date is resolved
        // against `today` inside the test. Other variants ignore it.
        val kind: Timeframe = when (kindIndex) {
            0 -> Timeframe.Today
            1 -> Timeframe.WithinADay
            2 -> Timeframe.WithinAWeek
            else -> Timeframe.SpecificDate(LocalDate.EPOCH)
        }
        ItemSpec(idSeed, kind, createdOffset, millis, specificOffset, status)
    }

    fun syncMeta(): SyncMeta =
        SyncMeta(updatedAt = 0L, version = 1L, deleted = false, dirty = false)

    // Builds a concrete ActionItem from a spec, anchored relative to `today`.
    fun buildItem(spec: ItemSpec, today: LocalDate, index: Int): ActionItem {
        val createdDate = today.plusDays(spec.createdOffsetDays.toLong())
        val createdAt =
            createdDate.atStartOfDay(zone).toInstant().toEpochMilli() + spec.millisInDay
        val timeframe: Timeframe = when (spec.kind) {
            is Timeframe.SpecificDate ->
                Timeframe.SpecificDate(today.plusDays(spec.specificOffsetDays.toLong()))
            else -> spec.kind
        }
        return ActionItem(
            // Index keeps ids unique even when idSeed collides, so the result
            // list can be compared element-for-element without ambiguity.
            id = "item-$index-${spec.idSeed}",
            accountId = "account-1",
            bucketId = "bucket-1",
            title = "Item ${spec.idSeed}",
            contentType = ContentType.TEXT,
            timeframe = timeframe,
            status = spec.status,
            createdAt = createdAt,
            isWishlistItem = false,
            sync = syncMeta(),
        )
    }

    // Feature: action-tracker-app, Property 12: The daily due-set contains exactly the items due that day
    "Property 12: dueOn returns exactly the items whose timeframe resolves to the target date" {
        checkAll(100, arbToday, Arb.list(arbSpec, 0..40)) { today, specs ->
            val items = specs.mapIndexed { index, spec -> buildItem(spec, today, index) }

            val actual = DueSet.dueOn(items, today, zone)

            // Independent reimplementation of the membership predicate: an item
            // is due when its resolved due date equals the target date.
            fun resolvesToToday(item: ActionItem): Boolean {
                val createdDate = DueSet.createdDate(item, zone)
                val resolved = when (val tf = item.timeframe) {
                    Timeframe.Today -> createdDate
                    Timeframe.WithinADay -> createdDate.plusDays(1)
                    Timeframe.WithinAWeek -> createdDate.plusDays(7)
                    is Timeframe.SpecificDate -> tf.date
                }
                return resolved == today
            }

            val expected = items.filter(::resolvesToToday)

            // dueOn equals the independently filtered list (order preserved, no
            // duplication, no loss).
            actual shouldBe expected

            // "Exactly", both directions: every due item is present, and no
            // not-due item leaks into the result.
            items.filter(::resolvesToToday).all { it in actual } shouldBe true
            items.filterNot(::resolvesToToday).none { it in actual } shouldBe true
        }
    }
})
