package com.sidequest.data.local.converters

import kotlinx.serialization.json.Json

/**
 * Shared kotlinx.serialization [Json] instance used by the Room [Converters].
 *
 * The default configuration is sufficient: the sealed [com.sidequest.domain.model.Timeframe]
 * type relies on kotlinx.serialization's polymorphic handling (a `type`
 * discriminator plus the variant payload), matching the discriminated-union
 * representation used by the shared OpenAPI schema. [encodeDefaults] is enabled
 * so every field is written explicitly, keeping the persisted form stable.
 */
internal val RoomJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
