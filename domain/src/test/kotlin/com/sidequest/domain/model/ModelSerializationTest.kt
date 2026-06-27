package com.sidequest.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Round-trip serialization tests for the domain models.
 *
 * These are example/unit tests (not property-based) that assert every
 * [ContentType] and [Timeframe] variant serializes and deserializes back to an
 * equal value via kotlinx.serialization. A full [ActionItem] round trip
 * exercises the nested models and the discriminated [Timeframe] union together.
 *
 * _Requirements: 14.2_
 */
class ModelSerializationTest : StringSpec({

    val json = Json

    // --- ContentType variants --------------------------------------------

    ContentType.entries.forEach { variant ->
        "ContentType.$variant round-trips through JSON" {
            val encoded = json.encodeToString(ContentType.serializer(), variant)
            val decoded = json.decodeFromString(ContentType.serializer(), encoded)
            decoded shouldBe variant
        }
    }

    "all ContentType variants are covered" {
        ContentType.entries.toSet() shouldBe
            setOf(ContentType.LINK, ContentType.TEXT, ContentType.IMAGE, ContentType.VIDEO_REF)
    }

    // --- Timeframe variants ----------------------------------------------

    val timeframes: List<Pair<String, Timeframe>> = listOf(
        "Today" to Timeframe.Today,
        "WithinADay" to Timeframe.WithinADay,
        "WithinAWeek" to Timeframe.WithinAWeek,
        "SpecificDate" to Timeframe.SpecificDate(LocalDate.of(2025, 6, 14)),
    )

    timeframes.forEach { (label, value) ->
        "Timeframe.$label round-trips through JSON" {
            val encoded = json.encodeToString(Timeframe.serializer(), value)
            val decoded = json.decodeFromString(Timeframe.serializer(), encoded)
            decoded shouldBe value
        }
    }

    "Timeframe.SpecificDate serializes the date as an ISO-8601 string" {
        val value = Timeframe.SpecificDate(LocalDate.of(2025, 1, 5))
        val encoded = json.encodeToString(Timeframe.serializer(), value)
        // Discriminated union: type discriminator + ISO-8601 date payload.
        encoded.contains("\"specific_date\"") shouldBe true
        encoded.contains("\"2025-01-05\"") shouldBe true
    }

    // --- Full ActionItem round trip (exercises nested models) -------------

    "ActionItem with a link preview and SpecificDate round-trips through JSON" {
        val item = ActionItem(
            id = "item-1",
            accountId = "acct-1",
            bucketId = "bucket-1",
            title = "Read this article",
            description = "An interesting read",
            contentType = ContentType.LINK,
            sourceContent = "https://example.com/article",
            preview = LinkPreview(
                title = "Example Article",
                thumbnailUrl = "https://example.com/thumb.png",
                sourceName = "example.com",
                rawUrl = "https://example.com/article",
                resolved = true,
            ),
            timeframe = Timeframe.SpecificDate(LocalDate.of(2025, 12, 31)),
            status = ActionStatus.NOT_STARTED,
            createdAt = 1_700_000_000_000L,
            sync = SyncMeta(updatedAt = 1_700_000_000_000L, version = 1, deleted = false, dirty = true),
        )

        val encoded = json.encodeToString(ActionItem.serializer(), item)
        val decoded = json.decodeFromString(ActionItem.serializer(), encoded)
        decoded shouldBe item
    }

    "ActionItem TEXT variant with each Timeframe round-trips through JSON" {
        timeframes.forEach { (_, timeframe) ->
            val item = ActionItem(
                id = "item-2",
                accountId = "acct-1",
                bucketId = "shop-1",
                title = "New headphones",
                contentType = ContentType.TEXT,
                sourceContent = "Wireless headphones",
                preview = null,
                timeframe = timeframe,
                status = ActionStatus.IN_PROGRESS,
                createdAt = 1_700_000_001_000L,
                sync = SyncMeta(updatedAt = 1_700_000_001_000L, version = 2, deleted = false, dirty = false),
            )

            val encoded = json.encodeToString(ActionItem.serializer(), item)
            val decoded = json.decodeFromString(ActionItem.serializer(), encoded)
            decoded shouldBe item
        }
    }

    // --- VoiceJournalEntry round trip ------------------------------------

    "VoiceJournalEntry freshly captured (no transcript) round-trips through JSON" {
        val entry = VoiceJournalEntry(
            id = "vj-1",
            accountId = "acct-1",
            audioRef = "/data/user/0/com.sidequest/files/voice_journal/vj-1.m4a",
            transcript = null,
            transcriptionFailed = false,
            createdAt = 1_700_000_002_000L,
            extractedActionItemIds = emptyList(),
            sync = SyncMeta(updatedAt = 1_700_000_002_000L, version = 1, deleted = false, dirty = true),
        )

        val encoded = json.encodeToString(VoiceJournalEntry.serializer(), entry)
        val decoded = json.decodeFromString(VoiceJournalEntry.serializer(), encoded)
        decoded shouldBe entry
    }

    "VoiceJournalEntry with transcript and extracted ids round-trips through JSON" {
        val entry = VoiceJournalEntry(
            id = "vj-2",
            accountId = "acct-1",
            audioRef = "audio/acct-1/vj-2.m4a",
            transcript = "Remember to book flights and renew passport.",
            transcriptionFailed = false,
            createdAt = 1_700_000_003_000L,
            extractedActionItemIds = listOf("item-a", "item-b"),
            sync = SyncMeta(updatedAt = 1_700_000_003_000L, version = 3, deleted = false, dirty = false),
        )

        val encoded = json.encodeToString(VoiceJournalEntry.serializer(), entry)
        val decoded = json.decodeFromString(VoiceJournalEntry.serializer(), encoded)
        decoded shouldBe entry
    }

    "VoiceJournalEntry with failed transcription round-trips through JSON" {
        val entry = VoiceJournalEntry(
            id = "vj-3",
            accountId = "acct-1",
            audioRef = "audio/acct-1/vj-3.m4a",
            transcript = null,
            transcriptionFailed = true,
            createdAt = 1_700_000_004_000L,
            extractedActionItemIds = emptyList(),
            sync = SyncMeta(updatedAt = 1_700_000_004_000L, version = 2, deleted = false, dirty = true),
        )

        val encoded = json.encodeToString(VoiceJournalEntry.serializer(), entry)
        val decoded = json.decodeFromString(VoiceJournalEntry.serializer(), encoded)
        decoded shouldBe entry
    }
})
