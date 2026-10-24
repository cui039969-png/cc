// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.daily3minutes.widget.DailySystemReceiver
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only the reminder preferences and our own generic notification, never record data. */
@RunWith(AndroidJUnit4::class)
class ReminderDeliveryInstrumentedTest {
    private lateinit var fixture: ReminderFixture
    @Before fun prepare() { fixture = ReminderFixture(ApplicationProvider.getApplicationContext()) }
    @After fun restore() { if (::fixture.isInitialized) fixture.close() }

    @Test fun dueReceiverPostsPrivateNotificationAndRejectsDuplicateAndStaleDelivery() {
        val due = System.currentTimeMillis() - 1_000
        fixture.arm(due)
        fixture.receive(due)
        val posted = fixture.awaitPosted()
        assertEquals("每天3分钟", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("用一点时间，记下今天的收获与明天的方向。", posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
        assertEquals(LocalDate.now().toString(), fixture.preferences.getString("last_delivered_date", null))
        assertTrue(fixture.preferences.getLong("scheduled_at", 0) > System.currentTimeMillis())
        assertNotNull(posted.contentIntent)

        fixture.manager.cancel(ReminderFixture.NOTIFICATION_ID)
        fixture.awaitCancelled()
        fixture.preferences.edit().putLong("scheduled_at", due).commit()
        fixture.receive(due)
        fixture.assertStaysAbsent()

        val replacement = System.currentTimeMillis() + 60_000
        fixture.preferences.edit().putLong("scheduled_at", replacement).commit()
        fixture.receive(due)
        fixture.assertStaysAbsent()
        assertEquals(replacement, fixture.preferences.getLong("scheduled_at", 0))
    }

    @Test fun disabledAndNotYetDueRemindersNeverPost() {
        val due = System.currentTimeMillis() - 1_000
        fixture.arm(due)
        fixture.preferences.edit().putBoolean("enabled", false).commit()
        fixture.receive(due)
        fixture.assertStaysAbsent()
        val future = System.currentTimeMillis() + 60_000
        fixture.arm(future)
        fixture.receive(future)
        fixture.assertStaysAbsent()
        assertFalse(fixture.preferences.contains("last_delivered_date"))
    }

    @Test fun controlledSystemEventsRebuildReminderAndEnqueueWidgetRecovery() {
        val receiver = DailySystemReceiver()
        val events = listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)
        for (event in events) {
            fixture.arm(1L)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                receiver.onReceive(fixture.context, Intent(event))
            }
            val next = fixture.preferences.getLong("scheduled_at", 0)
            assertTrue("Reminder recovery failed for $event", next > System.currentTimeMillis())
        }
        val work = WorkManager.getInstance(fixture.context)
            .getWorkInfosForUniqueWork("daily-widget-system-refresh").get(10, TimeUnit.SECONDS)
        assertTrue(work.isNotEmpty())
        assertTrue(work.any { it.state != WorkInfo.State.FAILED && it.state != WorkInfo.State.CANCELLED })
        fixture.preferences.edit().putLong("scheduled_at", 123L).commit()
        receiver.onReceive(fixture.context, Intent("io.github.daily3minutes.SYNTHETIC_UNKNOWN"))
        assertEquals(123L, fixture.preferences.getLong("scheduled_at", 0))
    }
}

/** Permission restoration happens in the caller after instrumentation, since revocation kills the app. */
internal class ReminderFixture(val context: Context) : AutoCloseable {
    val preferences: SharedPreferences = context.getSharedPreferences("daily_reminder", Context.MODE_PRIVATE)
    val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val originalPreferences = preferences.all.toMap()
    private val originalNotification = manager.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID }?.notification
    private val originalChannel = manager.getNotificationChannel("daily_review")

    init {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        manager.cancel(NOTIFICATION_ID)
        awaitCancelled()
    }

    fun arm(expected: Long) {
        preferences.edit().putBoolean("enabled", true).putInt("hour", 21).putInt("minute", 0)
            .putLong("scheduled_at", expected).remove("last_delivered_date").commit()
    }

    fun receive(expected: Long) = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        ReminderReceiver().onReceive(context, Intent(ReminderScheduler.ACTION_REMINDER)
            .putExtra(ReminderScheduler.EXPECTED_TIME, expected))
    }

    fun notification(): Notification? = manager.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID }?.notification

    fun awaitPosted(): Notification {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            notification()?.let { return it }
            SystemClock.sleep(25)
        }
        error("Due reminder was not posted")
    }

    fun awaitCancelled() {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (notification() != null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        check(notification() == null)
    }

    fun assertStaysAbsent() {
        val deadline = SystemClock.elapsedRealtime() + 500
        while (SystemClock.elapsedRealtime() < deadline) {
            check(notification() == null) { "An unexpected reminder was posted" }
            SystemClock.sleep(25)
        }
    }

    override fun close() {
        ReminderScheduler(context).setEnabled(false)
        val editor = preferences.edit().clear()
        for ((key, value) in originalPreferences) when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
        check(editor.commit())
        ReminderScheduler(context).reschedule()
        originalNotification?.let { manager.notify(NOTIFICATION_ID, it) }
        if (originalChannel == null) manager.deleteNotificationChannel("daily_review")
    }

    companion object { const val NOTIFICATION_ID = 3102 }
}
