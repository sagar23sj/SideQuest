package com.sidequest.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the daily reminder notification (Req 6.4, 6.6).
 *
 * Extracting this seam keeps [DailyReminderWorker] free of any direct
 * notification-posting detail and lets the worker be reasoned about as plain
 * suspend logic. The default [NotificationManagerReminderNotifier] backs it with
 * [NotificationManagerCompat] and creates the reminder channel on demand.
 *
 * Posting respects that POST_NOTIFICATIONS may be absent or revoked: when
 * notifications are disabled the notifier is a no-op (it returns `false`), since
 * the OS would drop the notification anyway and the settings screen handles the
 * permission explanation and deep link (Req 6.5).
 */
interface ReminderNotifier {

    /**
     * Posts a daily reminder with the given [title] and [text].
     *
     * @return `true` if the notification was posted; `false` when notifications
     *   are disabled (POST_NOTIFICATIONS not granted), in which case nothing is
     *   posted.
     */
    fun postReminder(title: String, text: String): Boolean

    /** Whether the OS currently permits this app to post notifications. */
    fun areNotificationsEnabled(): Boolean
}

/**
 * [ReminderNotifier] backed by [NotificationManagerCompat].
 *
 * Creates the reminder [NotificationChannel] lazily on first post (a no-op on
 * API levels below O, and idempotent on later ones). Guards every post behind
 * [NotificationManagerCompat.areNotificationsEnabled] so it never attempts to
 * post when the user has not granted (or has revoked) POST_NOTIFICATIONS; in
 * that case the settings screen surfaces the explanation and OS deep link
 * (Req 6.5).
 */
@Singleton
class NotificationManagerReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override fun areNotificationsEnabled(): Boolean =
        notificationManager.areNotificationsEnabled()

    override fun postReminder(title: String, text: String): Boolean {
        // The OS drops notifications when the permission is absent/revoked, so
        // skip posting entirely; the settings screen handles the explanation
        // and deep link to OS settings (Req 6.5).
        if (!notificationManager.areNotificationsEnabled()) return false

        ensureChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        return true
    }

    /**
     * Creates the reminder channel if it does not yet exist. Creating a channel
     * with an existing id is a no-op, so this is safe to call before every post.
     */
    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        val systemManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemManager.createNotificationChannel(channel)
    }

    companion object {
        /** Id of the daily-reminder notification channel. */
        const val CHANNEL_ID = "daily_reminder"

        /** User-visible channel name shown in OS notification settings. */
        const val CHANNEL_NAME = "Daily reminders"

        /** User-visible channel description. */
        const val CHANNEL_DESCRIPTION =
            "Daily reminders summarizing the action items you planned to do."

        /** Fixed notification id so a new daily reminder replaces the previous. */
        const val NOTIFICATION_ID = 1001

        /** Default title for the daily reminder notification. */
        const val DEFAULT_TITLE = "SideQuest"
    }
}
