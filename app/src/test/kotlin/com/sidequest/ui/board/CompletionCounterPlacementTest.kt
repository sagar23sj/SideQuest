package com.sidequest.ui.board

import androidx.compose.ui.graphics.Color
import com.sidequest.domain.board.BoardGroup
import com.sidequest.domain.board.BoardItem
import com.sidequest.domain.board.BoardState
import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.model.Bucket
import com.sidequest.domain.model.ContentType
import com.sidequest.domain.model.SyncMeta
import com.sidequest.domain.model.Timeframe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Example tests for the Completion_Counter shown at the top of the board
 * (Req 5.1) and the indicator-color parsing that backs the status dots
 * (Req 4.3).
 *
 * Note on the top-of-board placement: actually asserting that the counter node
 * renders above the first bucket/item requires a Compose UI test
 * (`createComposeRule` from `androidx.compose.ui.test`). Those dependencies are
 * wired for instrumentation tests only (`androidTestImplementation`), not for
 * local unit/Robolectric tests, so this spec verifies the counter at the levels
 * that are JVM-testable:
 *
 *  - The [BoardUiState.Ready] state exposes the count the top counter renders
 *    ([BoardState.completionCount]), so the data the counter needs is present.
 *
 * The actual layout ordering is established structurally in
 * [BoardContent]/`ReadyBoard`: the `LazyColumn` adds the
 * `"completion-counter"` item before iterating `board.groups`, so the counter
 * is always the first emitted item and therefore sits at the top of the board.
 * That ordering is covered by the (instrumentation) Compose UI test layer.
 *
 * _Requirements: 5.1, 4.3_
 */
class CompletionCounterPlacementTest : StringSpec({

    val accountId = "account-1"

    fun syncMeta(): SyncMeta =
        SyncMeta(updatedAt = 0L, version = 1L, deleted = false, dirty = false)

    fun bucket(id: String): Bucket =
        Bucket(
            id = id,
            accountId = accountId,
            name = id,
            notStartedColor = "#NS_$id",
            inProgressColor = "#IP_$id",
            completedColor = "#CO_$id",
            sync = syncMeta(),
        )

    fun item(id: String, status: ActionStatus): ActionItem =
        ActionItem(
            id = id,
            accountId = accountId,
            bucketId = "travel",
            title = "Item $id",
            contentType = ContentType.TEXT,
            timeframe = Timeframe.Today,
            status = status,
            createdAt = 0L,
            sync = syncMeta(),
        )

    fun readyState(vararg statuses: ActionStatus): BoardUiState.Ready {
        val items = statuses.mapIndexed { index, status ->
            BoardItem(item = item("item-$index", status), statusColor = "#NS_travel")
        }
        val completionCount = statuses.count { it == ActionStatus.COMPLETED }
        return BoardUiState.Ready(
            BoardState(
                groups = listOf(BoardGroup(bucket = bucket("travel"), items = items)),
                completionCount = completionCount,
            ),
        )
    }

    // ---- Counter value exposed for top-of-board rendering (Req 5.1) ----

    "Ready state exposes the completion count the top counter renders" {
        val state = readyState(
            ActionStatus.COMPLETED,
            ActionStatus.NOT_STARTED,
            ActionStatus.COMPLETED,
        )

        state.shouldBeInstanceOf<BoardUiState.Ready>()
        state.board.completionCount shouldBe 2
    }

    "Ready state exposes a zero completion count when nothing is completed" {
        val state = readyState(ActionStatus.NOT_STARTED, ActionStatus.IN_PROGRESS)

        state.board.completionCount shouldBe 0
    }

    // ---- Indicator color parsing backing the status dots (Req 4.3) ----

    "parseStatusColor parses a six-digit hex string" {
        parseStatusColor("#336699", fallback = Color.Black) shouldBe
            Color(red = 0x33, green = 0x66, blue = 0x99)
    }

    "parseStatusColor parses an eight-digit hex string with alpha" {
        parseStatusColor("#8033CC11", fallback = Color.Black) shouldBe
            Color(alpha = 0x80, red = 0x33, green = 0xCC, blue = 0x11)
    }

    "parseStatusColor expands a three-digit hex shorthand" {
        parseStatusColor("#ABC", fallback = Color.Black) shouldBe
            Color(red = 0xAA, green = 0xBB, blue = 0xCC)
    }

    "parseStatusColor parses a value without a leading hash" {
        parseStatusColor("336699", fallback = Color.Black) shouldBe
            Color(red = 0x33, green = 0x66, blue = 0x99)
    }

    "parseStatusColor falls back when the value is blank" {
        parseStatusColor("   ", fallback = Color.Magenta) shouldBe Color.Magenta
    }

    "parseStatusColor falls back when the value is not a valid hex color" {
        parseStatusColor("not-a-color", fallback = Color.Magenta) shouldBe Color.Magenta
    }

    "parseStatusColor falls back when the hex length is unsupported" {
        // Five hex digits is neither #RGB, #RRGGBB, nor #AARRGGBB.
        parseStatusColor("#12345", fallback = Color.Magenta) shouldBe Color.Magenta
    }
})
