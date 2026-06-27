package com.sidequest.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.ActionStatus
import com.sidequest.domain.reminder.TaskReminderSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules and cancels per-task reminders via [AlarmManager] (Req 6.5–6.11).
 *
 * Each scheduled reminder is an exact alarm whose [PendingIntent] targets
 * [TaskReminderReceiver]; on fire the receiver posts the notification and, for
 * recurring reminders, re-arms the next occurrence. Using a stable request code
 * derived from the item id means re-scheduling replaces the previous alarm
 * rather than stacking duplicates, and cancellation can target it precisely.
 *
 * Alarms are anchored to the device's local time via [TaskReminderSchedule]
 * (Req 6.9). On API 31+ exact alarms require [AlarmManager.canScheduleExactAlarms];
 * when that is not granted we fall back to an inexact alarm so a reminder is
 * still delivered (slightly later) rather than dropped.
 */
@Singleton
class TaskReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules (or reschedules) the reminder for [item], or cancels it when the
     * item has no reminder or is already completed (Req 6.7). When the reminder
     * has no remaining occurrences (past its until-date) it is cancelled.
     */
    fun schedule(item: ActionItem) {
        val reminder = item.reminder
        if (reminder == null || item.status == ActionStatus.COMPLETED) {
            cancel(item.id)
            return
        }
        val triggerAt = TaskReminderSchedule.nextTrigger(reminder, ZonedDateTime.now(clock))
        if (triggerAt == null) {
            cancel(item.id)
            return
        }

        val pending = pendingIntent(item.id, item.title, create = true) ?: return
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            // Fall back to an inexact alarm so the reminder still fires (Req 6.11).
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /** Cancels any pending reminder alarm for [itemId] (Req 6.7, 6.8). */
    fun cancel(itemId: String) {
        val pending = pendingIntent(itemId, title = "", create = false) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    /** Reschedules all reminders for [items] (used after reboot, Req 6.10). */
    fun rescheduleAll(items: List<ActionItem>) {
        items.forEach { schedule(it) }
    }

    private fun pendingIntent(itemId: String, title: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_FIRE
            putExtra(TaskReminderReceiver.EXTRA_ITEM_ID, itemId)
            putExtra(TaskReminderReceiver.EXTRA_TITLE, title)
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, requestCode(itemId), intent, flags)
    }

    /** Stable per-item request code so reschedules replace and cancels target precisely. */
    private fun requestCode(itemId: String): Int = itemId.hashCode()
}
