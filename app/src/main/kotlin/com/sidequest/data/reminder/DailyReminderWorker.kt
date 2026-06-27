package com.sidequest.data.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sidequest.data.llm.PrepareReminderTextUseCase
import com.sidequest.data.local.dao.ActionItemDao
import com.sidequest.data.local.dao.BucketDao
import com.sidequest.data.local.entity.toActionItems
import com.sidequest.data.local.entity.toBuckets
import com.sidequest.domain.llm.ActionItemSummary
import com.sidequest.domain.model.ActionItem
import com.sidequest.domain.model.Timeframe
import com.sidequest.domain.reminder.DueSet
import com.sidequest.domain.reminder.ReminderContent
import com.sidequest.ui.capture.CurrentAccountProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Background job that delivers the daily reminder notification (Req 6.4, 6.6,
 * 7.1, 7.4).
 *
 * Scheduled by [ReminderScheduler] to run at the user-selected local time and
 * repeat every 24h (Req 6.3, 6.4). At fire time it:
 *  1. short-circuits to [Result.success] when reminders are disabled (Req 6.2)
 *     or when notifications are not permitted (the settings screen handles the
 *     explanation and OS deep link, Req 6.5);
 *  2. loads the current account's items, computes today's due-set via the pure
 *     [DueSet.dueOn], and summarizes those items (title, bucket name, due hint);
 *  3. delegates the content decision to the pure [ReminderContent.plan]:
 *     - [ReminderContent.ReminderPlan.DueItems] → requests LLM notification text
 *       through [PrepareReminderTextUseCase], which falls back to a non-empty
 *       default on error/timeout (Req 7.1, 7.4), and posts it;
 *     - [ReminderContent.ReminderPlan.ReviewUpcoming] → posts the fixed
 *       "review upcoming" prompt (Req 6.6).
 *
 * The worker is a [HiltWorker] so its dependencies are injected through the
 * app's [androidx.hilt.work.HiltWorkerFactory]; the [Context] and
 * [WorkerParameters] are supplied by WorkManager via [Assisted] injection. The
 * [Clock] is injected (system default in production) so the "today" boundary is
 * deterministic and the delivery logic stays testable.
 */
@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val actionItemDao: ActionItemDao,
    private val bucketDao: BucketDao,
    private val currentAccountProvider: CurrentAccountProvider,
    private val prepareReminderTextUseCase: PrepareReminderTextUseCase,
    private val settingsStore: ReminderSettingsStore,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    /**
     * Gathers the items due today, curates the reminder text, and posts the
     * notification.
     *
     * Returns [Result.success] in every branch: when reminders are disabled,
     * when notifications are not permitted, or after posting — the LLM call
     * already fails soft (Req 7.4) and there is no transient infra failure worth
     * retrying here.
     */
    override suspend fun doWork(): Result {
        // Req 6.2 / 6.5: nothing to deliver when the user disabled reminders or
        // the OS does not permit notifications (the settings screen explains the
        // latter and deep-links to OS settings).
        val settings = settingsStore.settings.first()
        if (!settings.enabled) return Result.success()
        if (!notifier.areNotificationsEnabled()) return Result.success()

        val zone = clock.zone
        val today = LocalDate.now(clock)

        val accountId = currentAccountProvider.currentAccountId()

        // DB reads on IO; the rest of the work is light and stays on the worker
        // coroutine.
        val (dueItems, bucketNames) = withContext(Dispatchers.IO) {
            val items = actionItemDao.observeByAccount(accountId).first().toActionItems()
            val due = DueSet.dueOn(items, today, zone)
            val names = bucketDao.getByAccount(accountId).toBuckets()
                .associate { it.id to it.name }
            due to names
        }

        val summaries = dueItems.map { item ->
            ActionItemSummary(
                title = item.title,
                bucketName = bucketNames[item.bucketId] ?: UNKNOWN_BUCKET_NAME,
                dueDescription = dueDescription(item),
            )
        }

        when (val plan = ReminderContent.plan(summaries)) {
            is ReminderContent.ReminderPlan.DueItems -> {
                // Req 7.1: ask the LLM_Service for the notification text, falling
                // back to a non-empty default summarizing the count on
                // error/timeout (Req 7.4).
                val text = prepareReminderTextUseCase.prepareNotificationText(
                    summaries = plan.summaries,
                    default = ReminderContent.defaultDueText(plan.summaries.size),
                )
                notifier.postReminder(
                    title = NotificationManagerReminderNotifier.DEFAULT_TITLE,
                    text = text,
                )
            }

            ReminderContent.ReminderPlan.ReviewUpcoming -> {
                // Req 6.6: no items due → fixed "review upcoming" prompt, no LLM.
                notifier.postReminder(
                    title = NotificationManagerReminderNotifier.DEFAULT_TITLE,
                    text = ReminderContent.REVIEW_UPCOMING_TEXT,
                )
            }
        }

        return Result.success()
    }

    /**
     * A short, human-readable due hint for [item] derived from its timeframe,
     * used to enrich the LLM request (and as a sensible fallback summary).
     */
    private fun dueDescription(item: ActionItem): String = when (val tf = item.timeframe) {
        Timeframe.Today -> "today"
        Timeframe.WithinADay -> "within a day"
        Timeframe.WithinAWeek -> "within a week"
        is Timeframe.SpecificDate -> "on ${tf.date}"
    }

    companion object {
        /** Unique periodic-work name so the daily reminder is never duplicated. */
        const val WORK_NAME = "daily_reminder"

        /** Fallback bucket label when a due item's bucket can't be resolved. */
        private const val UNKNOWN_BUCKET_NAME = "General"
    }
}
