package com.sidequest.data.reminder

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sidequest.domain.reminder.ReminderSchedule
import com.sidequest.domain.reminder.ReminderSettings
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules (or cancels) the daily reminder via WorkManager from the user's
 * [ReminderSettings] (Req 6.2, 6.3, 6.4).
 *
 * Extracting this seam keeps the reminder ViewModel free of a hard WorkManager
 * dependency: it just applies the latest settings. The default
 * [WorkManagerReminderScheduler] enqueues a periodic [DailyReminderWorker]:
 *  - When reminders are enabled, it schedules a 24h [androidx.work.PeriodicWorkRequest]
 *    with an initial delay computed by the pure [ReminderSchedule.initialDelay]
 *    so the first run lands on the next occurrence of the chosen local time
 *    (Req 6.3), then repeats daily (Req 6.4).
 *  - When reminders are disabled, it cancels the unique work (Req 6.2).
 *
 * The work is enqueued under a single unique name
 * ([DailyReminderWorker.WORK_NAME]) with [ExistingPeriodicWorkPolicy.UPDATE] so
 * changing the time reschedules in place rather than stacking duplicate jobs.
 */
interface ReminderScheduler {

    /**
     * Applies [settings]: schedules the daily reminder when enabled, or cancels
     * it when disabled. Idempotent — calling it repeatedly with the same
     * settings leaves a single scheduled reminder.
     */
    fun apply(settings: ReminderSettings)
}

/**
 * [ReminderScheduler] backed by [WorkManager].
 *
 * The [clock] is injectable so the initial-delay computation is testable; in
 * production it is the system clock in the device's default zone.
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val clock: Clock,
) : ReminderScheduler {

    override fun apply(settings: ReminderSettings) {
        if (!settings.enabled) {
            workManager.cancelUniqueWork(DailyReminderWorker.WORK_NAME)
            return
        }

        val now = ZonedDateTime.now(clock)
        val initialDelay: Duration = ReminderSchedule.initialDelay(now, settings.time)

        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(
            ReminderSchedule.DAY.toMinutes(),
            TimeUnit.MINUTES,
        )
            .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            DailyReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
