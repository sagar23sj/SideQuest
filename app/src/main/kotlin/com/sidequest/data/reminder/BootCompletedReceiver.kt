package com.sidequest.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.entity.toActionItems
import com.sidequest.ui.capture.CurrentAccountProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms all pending per-task reminders after the device restarts (Req 6.10).
 *
 * Android drops scheduled alarms on reboot, so this receiver — registered for
 * `BOOT_COMPLETED` in the manifest — reloads the current account's items and
 * reschedules every active reminder through [TaskReminderScheduler], which
 * recomputes each next-trigger against the (possibly new) current time and zone
 * (Req 6.9).
 */
class BootCompletedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun actionItemDao(): ActionItemDao
        fun scheduler(): TaskReminderScheduler
        fun accountProvider(): CurrentAccountProvider
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootEntryPoint::class.java,
        )

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val accountId = entry.accountProvider().currentAccountId()
                val items = entry.actionItemDao().getByAccount(accountId).toActionItems()
                entry.scheduler().rescheduleAll(items)
            } finally {
                pending.finish()
            }
        }
    }
}
