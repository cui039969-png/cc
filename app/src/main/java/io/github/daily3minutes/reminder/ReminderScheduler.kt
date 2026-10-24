/* SPDX-License-Identifier: Apache-2.0 */
package io.github.daily3minutes.reminder

import io.github.daily3minutes.ui.t

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.daily3minutes.ui.MainActivity
import java.time.Instant
import java.time.ZoneId

/** Local opt-in reminder. Uses one inexact alarm and never requires exact-alarm access. */
class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("daily_reminder", Context.MODE_PRIVATE)
    private val alarms = appContext.getSystemService(AlarmManager::class.java)

    fun isEnabled(): Boolean = preferences.getBoolean(ENABLED, false)
    fun hour(): Int = preferences.getInt(HOUR, 21)
    fun minute(): Int = preferences.getInt(MINUTE, 0)

    fun setEnabled(enabled: Boolean) {
        val permitted = enabled && canNotify()
        preferences.edit().putBoolean(ENABLED, permitted).apply()
        if (permitted) ensureChannel() else NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
        reschedule()
    }

    fun setTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59) { "Invalid reminder time" }
        preferences.edit().putInt(HOUR, hour).putInt(MINUTE, minute).apply()
        reschedule()
    }

    fun reschedule() {
        pendingAlarm(0L, PendingIntent.FLAG_NO_CREATE)?.let(alarms::cancel)
        preferences.edit().remove(SCHEDULED).apply()
        if (!isEnabled()) return
        if (!canNotify()) {
            preferences.edit().putBoolean(ENABLED, false).apply()
            return
        }
        ensureChannel()
        val next = ReminderTimes.next(Instant.now(), ZoneId.systemDefault(), hour(), minute()).toEpochMilli()
        preferences.edit().putLong(SCHEDULED, next).apply()
        val alarm = requireNotNull(pendingAlarm(next, PendingIntent.FLAG_UPDATE_CURRENT))
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm)
    }

    /** Validate identity/date before posting; rescheduling invalidates delayed old broadcasts. */
    internal fun deliver(expectedMillis: Long) {
        if (!isEnabled()) return
        val expected = preferences.getLong(SCHEDULED, 0L)
        if (expected == 0L || expected != expectedMillis) return
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val scheduled = Instant.ofEpochMilli(expected)
        val date = scheduled.atZone(zone).toLocalDate().toString()
        val alreadyDelivered = preferences.getString(LAST_DELIVERED_DATE, null) == date
        if (canNotify() && !alreadyDelivered && ReminderTimes.isCurrentDay(now, scheduled, zone)) {
            ensureChannel()
            val openToday = Intent(appContext, MainActivity::class.java)
                .setAction("io.github.daily3minutes.OPEN_TODAY")
                .putExtra("destination", "today")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val contentIntent = PendingIntent.getActivity(
                appContext, NOTIFICATION_ID, openToday,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(appContext, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle(t("每天3分钟"))
                .setContentText(t("用一点时间，记下今天的收获与明天的方向。"))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            try {
                // Permission can be revoked between the check and this platform call.
                NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
                preferences.edit().putString(LAST_DELIVERED_DATE, date).apply()
            } catch (_: SecurityException) {
                preferences.edit().putBoolean(ENABLED, false).apply()
            }
        }
        reschedule()
    }

    private fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, t("每日回顾提醒"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = t("在你选择的时间提醒填写今日三问")
            },
        )
    }

    private fun pendingAlarm(timestamp: Long, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        appContext, ALARM_REQUEST,
        Intent(appContext, ReminderReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXPECTED_TIME, timestamp),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )

    internal companion object {
        const val ACTION_REMINDER = "io.github.daily3minutes.DAILY_REMINDER"
        const val EXPECTED_TIME = "expected_time"
        private const val ENABLED = "enabled"
        private const val HOUR = "hour"
        private const val MINUTE = "minute"
        private const val SCHEDULED = "scheduled_at"
        private const val LAST_DELIVERED_DATE = "last_delivered_date"
        private const val CHANNEL = "daily_review"
        private const val ALARM_REQUEST = 3101
        private const val NOTIFICATION_ID = 3102
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMINDER) return
        ReminderScheduler(context).deliver(intent.getLongExtra(ReminderScheduler.EXPECTED_TIME, 0L))
    }
}
