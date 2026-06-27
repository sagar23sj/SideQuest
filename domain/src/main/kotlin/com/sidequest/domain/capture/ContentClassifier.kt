package com.sidequest.domain.capture

import com.sidequest.domain.model.ContentType

/**
 * Pure classification of shared content into a [ContentType] or [Unsupported].
 *
 * The share target registers intent-filters for the MIME types text/plain,
 * image, and video. Classification follows these rules:
 *
 *  - an image MIME type -> [ContentType.IMAGE]
 *  - a video MIME type -> [ContentType.VIDEO_REF]
 *  - a text MIME type -> [ContentType.LINK] when the shared text contains a
 *    URL, otherwise [ContentType.TEXT]
 *  - anything else -> [ClassificationResult.Unsupported] (Req 1.4)
 *
 * The function is total and side-effect free so it can be validated with
 * property-based tests; persistence of the resulting Action_Item happens
 * elsewhere only for [ClassificationResult.Supported] outcomes.
 */
fun classify(intentData: SharedIntentData): ClassificationResult {
    val mimeType = intentData.mimeType?.trim()?.lowercase()
        ?: return ClassificationResult.Unsupported

    val majorType = mimeType.substringBefore('/')
    return when (majorType) {
        "image" -> ClassificationResult.Supported(ContentType.IMAGE)
        "video" -> ClassificationResult.Supported(ContentType.VIDEO_REF)
        "text" -> classifyText(intentData.text)
        else -> ClassificationResult.Unsupported
    }
}

/**
 * Shared text classifies as [ContentType.LINK] when it contains a URL and
 * [ContentType.TEXT] otherwise.
 */
private fun classifyText(text: String?): ClassificationResult {
    val contentType = if (text != null && containsUrl(text)) {
        ContentType.LINK
    } else {
        ContentType.TEXT
    }
    return ClassificationResult.Supported(contentType)
}

/** Matches an `http`/`https` URL token anywhere within the shared text. */
private val URL_REGEX = Regex("""\bhttps?://\S+""", RegexOption.IGNORE_CASE)

private fun containsUrl(text: String): Boolean = URL_REGEX.containsMatchIn(text)
