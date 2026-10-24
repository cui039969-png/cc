/* SPDX-License-Identifier: Apache-2.0 */
package io.github.daily3minutes.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.daily3minutes.reminder.ReminderScheduler
import kotlinx.coroutines.CancellationException

class DailyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DailyWidget.scheduleRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        DailyWidget.cancelRefresh(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        DailyWidget.scheduleRefresh(context)
    }
}

/** WorkManager owns asynchronous refresh lifetime; broadcasts never start an Activity. */
class DailyWidgetWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        DailyWidget.refresh(applicationContext)
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
}

class DailySystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        ReminderScheduler(context).reschedule()
        DailyWidget.scheduleRefresh(context)
        DailyWidget.requestRefresh(context)
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
