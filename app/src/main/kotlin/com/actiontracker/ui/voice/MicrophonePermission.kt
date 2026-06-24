package com.actiontracker.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Helpers for the OS microphone permission flow (Req 10.1).
 *
 * Kept as small, side-effect-light functions so the Compose layer can request
 * the RECORD_AUDIO runtime permission with the Activity Result API and deep-link
 * to app settings when it has been denied, mirroring the notification-permission
 * pattern.
 */
object MicrophonePermission {

    /** The runtime permission required to capture audio (Req 10.1). */
    const val PERMISSION: String = Manifest.permission.RECORD_AUDIO

    /** Whether the app currently holds the RECORD_AUDIO permission. */
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Builds an [Intent] that deep-links to this app's OS details settings so
     * the user can grant the microphone permission after denying it (Req 10.1).
     *
     * Uses [Settings.ACTION_APPLICATION_DETAILS_SETTINGS] with the app package,
     * since RECORD_AUDIO is granted from the app's permission screen rather than
     * a dedicated notification screen.
     */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
}
