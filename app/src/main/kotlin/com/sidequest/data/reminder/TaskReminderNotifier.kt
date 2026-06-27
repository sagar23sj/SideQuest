package com.sidequest.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sidequest.domain.model.ActionItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a per-task reminder notification (Req 6.5). Tapping it opens SideQuest
 * (the launcher activity) so the User lands in the app to act on the task and
 * discover what else is there — completion stays an in-app press-and-hold
 * gesture rather than a notification action (Req 6c).
 *
 * Each item gets a stable notification id derived from its id so a re-fired
 * recurring reminder replaces the previous one rather than stacking.
 */
@Singleton
class TaskReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val notificationManager = NotificationManagerCompat.from(context)

    fun postTaskReminder(item: ActionItem): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        ensureChannel()

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            launch ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(REMINDER_TITLE)
            .setContentText(item.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(item.id.hashCode(), notification)
        return true
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = CHANNEL_DESCRIPTION }
        val systemManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        systemManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val CHANNEL_NAME = "Task reminders"
        const val CHANNEL_DESCRIPTION = "Reminders for specific tasks you scheduled."
        const val REMINDER_TITLE = "Time for your quest"
    }
}
