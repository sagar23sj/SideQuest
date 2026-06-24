package com.actiontracker.domain.capture

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test for the rejection of unsupported shared content.
 *
 * Classification is the gate that decides whether a Shared_Item proceeds into
 * the capture flow that can create (and therefore persist) an Action_Item.
 * Only a [ClassificationResult.Supported] outcome lets capture proceed, so
 * proving that unsupported content always classifies as
 * [ClassificationResult.Unsupported] is the correctness check for "never
 * persisted" (Req 1.4).
 *
 * _Requirements: 1.4_
 */
class UnsupportedContentPropertyTest : StringSpec({

    // The only major MIME types the app supports.
    val supportedMajorTypes = setOf("image", "video", "text")

    /**
     * Generates MIME types whose major type is NOT one of the supported set,
     * plus the `null` case. Covers structured `major/sub` types (e.g.
     * application/pdf, audio/mpeg), bare major types, and arbitrary random
     * strings, while excluding anything that would resolve to image/video/text.
     */
    val unsupportedMimeType: Arb<String?> = arbitrary { rs ->
        val knownUnsupportedMajors = listOf(
            "application", "audio", "font", "model", "multipart", "message", "chemical",
        )
        val subtypes = listOf("octet-stream", "pdf", "json", "mpeg", "zip", "xml", "", "x-custom")

        when (Arb.element("known", "random", "null").bind()) {
            "known" -> {
                val major = Arb.element(knownUnsupportedMajors).bind()
                val sub = Arb.element(subtypes).bind()
                if (Arb.boolean().bind()) "$major/$sub" else major
            }
            "random" -> {
                // Arbitrary string; reject any value whose major type is supported.
                val raw = Arb.string(0..20).bind()
                val major = raw.trim().lowercase().substringBefore('/')
                if (major in supportedMajorTypes) "application/$raw" else raw
            }
            else -> null
        }
    }

    // Feature: action-tracker-app, Property 1: Unsupported content is rejected and never persisted
    "Property 1: unsupported content classifies as Unsupported and is never persisted" {
        checkAll(
            100,
            unsupportedMimeType,
            Arb.of(null, "", "some shared text", "https://example.com/post", "no url here"),
            Arb.of(null, "", "content://media/external/1", "file:///tmp/x"),
        ) { mimeType, text, uri ->
            // Guard: the generated mime type must genuinely be unsupported.
            val major = mimeType?.trim()?.lowercase()?.substringBefore('/')
            if (major == null || major !in supportedMajorTypes) {
                val result = classify(SharedIntentData(mimeType = mimeType, text = text, uri = uri))
                result shouldBe ClassificationResult.Unsupported
            }
        }
    }
})
