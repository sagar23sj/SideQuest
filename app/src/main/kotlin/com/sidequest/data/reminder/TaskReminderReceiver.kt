package com.sidequest.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.entity.toDomain
import com.sidequest.domain.model.ActionStatus
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives a per-task reminder alarm (Req 6.5, 6.6), posts the reminder
 * notification for the item, and — for a recurring reminder — re-arms the next
 * occurrence so it keeps firing daily until the until-date or until the item is
 * completed (Req 6.6, 6.7, 6.8).
 *
 * A [BroadcastReceiver] can't use constructor injection, so dependencies are
 * pulled from a Hilt [EntryPoint]. The DB read + reschedule happen on a
 * background scope guarded by `goAsync()` so the work completes after
 * [onReceive] returns.
 */
class TaskReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun actionItemDao(): ActionItemDao
        fun scheduler(): TaskReminderScheduler
        fun notifier(): TaskReminderNotifier
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return

        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java,
        )

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val item = entry.actionItemDao().getById(itemId)?.toDomain()
                // Only notify for a live, not-completed item that still has a reminder.
                if (item != null &&
                    !item.sync.deleted &&
                    item.status != ActionStatus.COMPLETED &&
                    item.reminder != null
                ) {
                    entry.notifier().postTaskReminder(item)
                    // Re-arm the next occurrence (no-op when there are none left).
                    entry.scheduler().schedule(item)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.sidequest.action.TASK_REMINDER_FIRE"
        const val EXTRA_ITEM_ID = "itemId"
        const val EXTRA_TITLE = "title"
    }
}
