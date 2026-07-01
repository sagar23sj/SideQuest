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

/**
 * Extracts the first `http(s)://` URL from shared [text], reading from the
 * scheme until the next whitespace or the end of the string, and trimming
 * trailing punctuation that commonly clings to a URL in prose (e.g. a closing
 * paren or period). Returns null when [text] is null/contains no URL.
 *
 * This lets a share that arrives as a sentence — e.g.
 * `"See this instagram post by @x https://www.instagram.com/p/abc/"` — still
 * yield a clean link the preview fetcher can resolve, rather than treating the
 * whole sentence as the URL.
 */
fun firstUrlOrNull(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val raw = URL_REGEX.find(text)?.value ?: return null
    return raw.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'', '>')
        .takeIf { it.isNotBlank() }
}

/** Trailing punctuation that commonly clings to a URL in prose. */
private const val URL_TRAILING = ".,;:!?)]}\"'>"

/**
 * Extracts every `http(s)://` URL from [text], in order, trimming trailing
 * punctuation and de-duplicating. Returns an empty list when there are none.
 */
fun allUrls(text: String?): List<String> {
    if (text.isNullOrBlank()) return emptyList()
    return URL_REGEX.findAll(text)
        .map { it.value.trimEnd(*URL_TRAILING.toCharArray()) }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
}

/**
 * Returns [text] with every URL removed and surrounding whitespace collapsed, so
 * the remaining prose can be used as a description. Text that is only links (and
 * whitespace) yields an empty string.
 */
fun stripUrls(text: String?): String {
    if (text.isNullOrBlank()) return ""
    return text.replace(URL_REGEX, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
