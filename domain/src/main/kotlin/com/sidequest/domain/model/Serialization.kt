package com.sidequest.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Serializes [LocalDate] as an ISO-8601 calendar date string (e.g. `2025-06-14`).
 *
 * Using the ISO-8601 wire format keeps the Kotlin models aligned with the shared
 * OpenAPI schema (`format: date`) that the Go backend and a future iOS client
 * generate from, so a [Timeframe.SpecificDate] round-trips identically across
 * platforms.
 */
object LocalDateIso8601Serializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.sidequest.domain.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString(), DateTimeFormatter.ISO_LOCAL_DATE)
}
