package com.sidequest.ui.capture

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test for the share-target manifest registration (Req 1.1).
 *
 * The app registers [ShareTargetActivity] as an OS share target via
 * `intent-filter`s for `ACTION_SEND` (text/plain, image/&#42;, video/&#42;) and
 * `ACTION_SEND_MULTIPLE` (image/&#42;, video/&#42;). This is one-time
 * configuration in `AndroidManifest.xml`, so it is verified with a smoke test
 * rather than a property test.
 *
 * Why Robolectric: the assertion exercises the merged manifest through the real
 * [PackageManager]. Robolectric stands up an Android runtime (with the merged
 * manifest) as a JVM unit test — no device/emulator — and resolves the
 * registered components exactly as the OS share sheet would when it queries for
 * activities that can handle a given send intent and MIME type.
 *
 * _Requirements: 1.1_
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShareTargetManifestSmokeTest {

    private val packageManager: PackageManager
        get() = ApplicationProvider.getApplicationContext<Context>().packageManager

    /**
     * Resolves an `ACTION_SEND` intent for [mimeType] and reports whether
     * [ShareTargetActivity] is among the activities the OS would offer in the
     * share sheet — the same query the platform performs to populate it.
     */
    private fun shareTargetResolvesFor(mimeType: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).setType(mimeType)
        return packageManager
            .queryIntentActivities(intent, 0)
            .any { it.activityInfo?.name == SHARE_TARGET_CLASS }
    }

    @Test
    fun shareTarget_isRegistered_forPlainTextAndLinks() {
        // text/plain covers both shared links and shared plain text (Req 1.1).
        shareTargetResolvesFor("text/plain") shouldBe true
    }

    @Test
    fun shareTarget_isRegistered_forImages() {
        // The image/* filter matches concrete image MIME types (Req 1.1).
        shareTargetResolvesFor("image/png") shouldBe true
        shareTargetResolvesFor("image/jpeg") shouldBe true
    }

    @Test
    fun shareTarget_isRegistered_forVideoReferences() {
        // The video/* filter matches concrete video MIME types (Req 1.1).
        shareTargetResolvesFor("video/mp4") shouldBe true
    }

    private companion object {
        /** Fully qualified name declared in AndroidManifest.xml (relative `.ui.capture...`). */
        const val SHARE_TARGET_CLASS = "com.sidequest.ui.capture.ShareTargetActivity"
    }
}
