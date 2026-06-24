package com.actiontracker.domain.capture

/**
 * Platform-neutral representation of the payload delivered to the share target.
 *
 * This is a pure data carrier with no Android dependencies so it can live in the
 * `:domain` module and be exercised by property tests. The Android
 * `ShareTargetActivity` maps an incoming `ACTION_SEND` / `ACTION_SEND_MULTIPLE`
 * intent onto this type before classification.
 *
 * @property mimeType the MIME type reported by the share intent (for example
 *   `text/plain`, `image/png`, `video/mp4`); `null` when the OS supplied none.
 * @property text the textual content shared with the intent, if any. For
 *   `text/plain` shares this may contain a URL.
 * @property uri an opaque reference (string form) to a shared media item such as
 *   an image or video, if any.
 */
data class SharedIntentData(
    val mimeType: String? = null,
    val text: String? = null,
    val uri: String? = null,
)
