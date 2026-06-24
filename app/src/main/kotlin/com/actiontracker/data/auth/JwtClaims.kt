package com.actiontracker.data.auth

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads claims from a JWT **without verifying its signature**.
 *
 * The backend is the authority on token validity (it signs and verifies every
 * token); the client only needs the `sub` (subject = account id) claim to label
 * locally created data with the signed-in account (Req 13.3). Because no trust
 * decision is made from this value on the client, an unverified decode is
 * sufficient and avoids shipping the signing secret to the device.
 */
internal object JwtClaims {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns the `sub` claim of [accessToken], or `null` if the token is
     * malformed or carries no subject. Never throws.
     */
    fun subject(accessToken: String): String? {
        val payload = decodePayload(accessToken) ?: return null
        return payload["sub"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
    }

    private fun decodePayload(token: String): JsonObject? {
        val parts = token.split(".")
        if (parts.size != 3) {
            return null
        }
        return try {
            val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            json.decodeFromString(JsonObject.serializer(), String(decoded, Charsets.UTF_8))
        } catch (_: Exception) {
            // Malformed base64 or JSON: treat as "no readable subject".
            null
        }
    }
}
