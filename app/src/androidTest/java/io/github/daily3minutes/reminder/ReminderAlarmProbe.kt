// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.reminder

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import java.time.LocalTime

/** Opt-in real AlarmManager probe; the timeout is an observation limit, not an exact alarm promise. */
internal object ReminderAlarmProbe {
    fun run(instrumentation: Instrumentation) {
        val result = Bundle()
        try {
            ReminderFixture(instrumentation.targetContext).use { fixture ->
                val target = LocalTime.now().plusMinutes(1)
                val scheduler = ReminderScheduler(fixture.context)
                fixture.preferences.edit().remove("last_delivered_date").commit()
                scheduler.setTime(target.hour, target.minute)
                scheduler.setEnabled(true)
                check(scheduler.isEnabled()) { "Notification permission or channel is blocked" }
                val expected = fixture.preferences.getLong("scheduled_at", 0)
                check(expected > System.currentTimeMillis())
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("alarm_probe", "ARMED: real inexact AlarmManager; waiting for ReminderReceiver")
                })
                val deadline = SystemClock.elapsedRealtime() + 12 * 60_000
                while (fixture.notification() == null && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(250)
                }
                val notification = fixture.notification()
                if (notification != null) {
                    check(fixture.preferences.getString("last_delivered_date", null) != null)
                    result.putString("alarm_probe", "PASS: Android delivered the inexact alarm and the real receiver posted the notification")
                    result.putLong("lateness_ms", System.currentTimeMillis() - expected)
                } else {
                    result.putString("alarm_probe", "NOT_OBSERVED: no notification within the 12-minute observation window")
                }
            }
            instrumentation.finish(Activity.RESULT_OK, result)
        } catch (error: Exception) {
            result.putString("alarm_probe", "FAILED: ${error.javaClass.simpleName}")
            instrumentation.finish(Activity.RESULT_CANCELED, result)
        }
    }
}
