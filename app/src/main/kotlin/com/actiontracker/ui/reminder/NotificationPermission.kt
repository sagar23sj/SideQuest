package com.actiontracker.ui.reminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Helpers for the OS notification permission flow (Req 6.1, 6.5).
 *
 * Kept as small, side-effect-light functions so the Compose layer can request
 * the runtime permission with the Activity Result API and deep-link to system
 * settings without spreading API-level checks through the UI.
 */
object NotificationPermission {

    /** The runtime permission requested on Android 13+ (Req 6.1). */
    const val PERMISSION: String = Manifest.permission.POST_NOTIFICATIONS

    /**
     * Whether the app currently has permission to post notifications.
     *
     * On API < 33 ([Build.VERSION_CODES.TIRAMISU]) there is no runtime
     * permission, so this reflects whether the user has notifications enabled
     * for the app. On API 33+ it checks the granted state of
     * [Manifest.permission.POST_NOTIFICATIONS].
     */
    fun isGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, PERMISSION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /**
     * Whether the runtime permission must be requested on this device. Only
     * API 33+ has the [Manifest.permission.POST_NOTIFICATIONS] runtime grant;
     * on older versions notifications are allowed by default and no request is
     * needed (Req 6.1).
     */
    fun requiresRuntimeRequest(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Builds an [Intent] that deep-links to this app's OS notification settings
     * so the user can grant the permission after denying it (Req 6.5).
     *
     * Uses [Settings.ACTION_APP_NOTIFICATION_SETTINGS] with the app package on
     * API 26+ (the app's `minSdk`), which lands directly on the app's
     * notification screen.
     */
    fun appNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
