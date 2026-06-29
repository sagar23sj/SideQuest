package com.sidequest.data.sync

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a stable identifier for this device+app install, used to provision a
 * silent backup account. It prefers `Settings.Secure.ANDROID_ID` (stable across
 * app reinstalls and data-clears for the same signing key on a device), and
 * falls back to a generated UUID persisted in private prefs. The resolved id is
 * cached so it never changes within an install.
 */
@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A stable device id (cached after first resolution). */
    fun deviceId(): String {
        prefs.getString(KEY_ID, null)?.let { return it }
        val resolved = androidId() ?: UUID.randomUUID().toString()
        prefs.edit().putString(KEY_ID, resolved).apply()
        return resolved
    }

    private fun androidId(): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != KNOWN_BAD_ANDROID_ID }
            ?.let { "android-$it" }
    }.getOrNull()

    private companion object {
        const val PREFS = "sidequest_device"
        const val KEY_ID = "device_id"
        // A famous buggy emulator ANDROID_ID shared by many devices; ignore it.
        const val KNOWN_BAD_ANDROID_ID = "9774d56d682e549c"
    }
}
