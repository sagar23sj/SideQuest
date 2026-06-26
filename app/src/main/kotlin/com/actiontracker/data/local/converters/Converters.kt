package com.actiontracker.data.local.converters

import androidx.room.TypeConverter
import com.actiontracker.domain.model.ActionStatus
import com.actiontracker.domain.model.ContentType
import com.actiontracker.domain.model.LinkPreview
import com.actiontracker.domain.model.SubAction
import com.actiontracker.domain.model.Timeframe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate

/**
 * Room [TypeConverter]s for the non-primitive fields of the persisted entities.
 *
 * Enums are stored as their stable names. Structured types ([LinkPreview], the
 * [Timeframe] discriminated union, and the ordered `List<SubAction>`) are
 * serialized to JSON via kotlinx.serialization so the persisted form mirrors
 * the shared OpenAPI schema. Nullable inputs map to nullable JSON columns so
 * absent values stay `NULL` rather than the string `"null"`.
 */
class Converters {

    // --- ActionStatus ----------------------------------------------------

    @TypeConverter
    fun fromActionStatus(value: ActionStatus): String = value.name

    @TypeConverter
    fun toActionStatus(value: String): ActionStatus = ActionStatus.valueOf(value)

    // --- ContentType -----------------------------------------------------

    @TypeConverter
    fun fromContentType(value: ContentType): String = value.name

    @TypeConverter
    fun toContentType(value: String): ContentType = ContentType.valueOf(value)

    // --- Timeframe (discriminated union) ---------------------------------

    @TypeConverter
    fun fromTimeframe(value: Timeframe): String =
        RoomJson.encodeToString(Timeframe.serializer(), value)

    @TypeConverter
    fun toTimeframe(value: String): Timeframe =
        RoomJson.decodeFromString(Timeframe.serializer(), value)

    // --- LinkPreview -----------------------------------------------------

    @TypeConverter
    fun fromLinkPreview(value: LinkPreview?): String? =
        value?.let { RoomJson.encodeToString(LinkPreview.serializer(), it) }

    @TypeConverter
    fun toLinkPreview(value: String?): LinkPreview? =
        value?.let { RoomJson.decodeFromString(LinkPreview.serializer(), it) }

    // --- List<SubAction> (ordered) ---------------------------------------

    @TypeConverter
    fun fromSubActions(value: List<SubAction>): String =
        RoomJson.encodeToString(ListSerializer(SubAction.serializer()), value)

    @TypeConverter
    fun toSubActions(value: String): List<SubAction> =
        RoomJson.decodeFromString(ListSerializer(SubAction.serializer()), value)

    // --- List<String> (e.g. extracted action item ids) -------------------

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        RoomJson.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        RoomJson.decodeFromString(ListSerializer(String.serializer()), value)

    // --- LocalDate -------------------------------------------------------

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
