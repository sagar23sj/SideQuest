package com.sidequest.data.preview

import com.sidequest.domain.preview.PreviewResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OkHttp-backed [PreviewService] that fetches a URL and parses Open Graph and
 * Twitter Card `<meta>` tags into a [PreviewResult] (Req 1a.1, 1a.2).
 *
 * Behavior maps directly onto the requirements:
 * - Parsed metadata → [PreviewResult.Success] (Req 1a.2).
 * - A fetch that exceeds the configured timeout → [PreviewResult.Timeout]
 *   (Req 1a.5).
 * - Any other error (network failure, non-2xx, empty body, no usable title) →
 *   [PreviewResult.Failure] (Req 1a.4).
 *
 * The timeout is enforced two ways for robustness: a coroutine [withTimeout]
 * around the whole call and a per-request OkHttp call timeout, both derived from
 * the `timeoutMs` argument. Parsing is deliberately lightweight — a regex scan
 * over `<meta>` tags rather than a full HTML parser — which is sufficient for
 * Open Graph/Twitter Card extraction. The pure mapping of this result onto an
 * Action_Item lives in `:domain` ([com.sidequest.domain.preview.PreviewMerge]);
 * this class is the thin network implementation.
 *
 * This service is not yet wired into the capture/WorkManager flow — that is a
 * later task.
 */
@Singleton
class OkHttpPreviewService @Inject constructor(
    private val client: OkHttpClient,
) : PreviewService {

    override suspend fun fetchPreview(url: String, timeoutMs: Long): PreviewResult =
        try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    fetchAndParse(url, timeoutMs)
                }
            }
        } catch (_: TimeoutCancellationException) {
            PreviewResult.Timeout(url)
        } catch (_: IOException) {
            PreviewResult.Failure(url)
        }

    private fun fetchAndParse(url: String, timeoutMs: Long): PreviewResult {
        val call = client.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
            .newCall(Request.Builder().url(url).get().build())

        call.execute().use { response ->
            if (!response.isSuccessful) return PreviewResult.Failure(url)
            val html = response.body?.string()
            if (html.isNullOrBlank()) return PreviewResult.Failure(url)
            return parseHtml(html, url)
        }
    }

    /**
     * Extracts a preview from raw [html], preferring Open Graph then Twitter
     * Card then the HTML `<title>`. Returns [PreviewResult.Failure] when no
     * usable title can be found (Req 1a.4).
     */
    private fun parseHtml(html: String, url: String): PreviewResult {
        val metas = META_TAG.findAll(html)
            .mapNotNull { match -> metaEntry(match.value) }
            .toMap()

        val title = metas["og:title"]
            ?: metas["twitter:title"]
            ?: TITLE_TAG.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return PreviewResult.Failure(url)

        val thumbnailUrl = metas["og:image"] ?: metas["twitter:image"]
        val sourceName = metas["og:site_name"] ?: hostOf(url)

        // Decode HTML entities so numeric/emoji references (e.g. &#x2014; em
        // dash, &#x1f917; emoji) and named entities (&amp; &quot; …) render as
        // real characters instead of their raw codes. URLs are decoded too so a
        // thumbnail link with &amp; resolves correctly.
        return PreviewResult.Success(
            title = decodeHtml(title),
            thumbnailUrl = thumbnailUrl?.let { decodeHtml(it) },
            sourceName = decodeHtml(sourceName),
        )
    }

    /** Decodes HTML character references (named, decimal, hex) into plain text. */
    private fun decodeHtml(text: String): String =
        androidx.core.text.HtmlCompat
            .fromHtml(text, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()

    /**
     * Parses a single `<meta>` tag into a (key, content) pair, where the key is
     * the tag's `property` or `name` attribute (e.g. `og:title`,
     * `twitter:image`). Returns null when either attribute is missing.
     */
    private fun metaEntry(tag: String): Pair<String, String>? {
        val key = (PROPERTY_ATTR.find(tag) ?: NAME_ATTR.find(tag))
            ?.groupValues?.getOrNull(1)?.trim()?.lowercase()
            ?: return null
        val content = CONTENT_ATTR.find(tag)?.groupValues?.getOrNull(1)?.trim()
            ?: return null
        if (content.isEmpty()) return null
        return key to content
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()
            ?.removePrefix("www.")
            ?.takeIf { it.isNotEmpty() }
            ?: url

    private companion object {
        val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        val TITLE_TAG = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val PROPERTY_ATTR = Regex("property\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val NAME_ATTR = Regex("name\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val CONTENT_ATTR = Regex("content\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    }
}
