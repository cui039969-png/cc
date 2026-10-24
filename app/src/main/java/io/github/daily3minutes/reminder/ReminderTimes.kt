/* SPDX-License-Identifier: Apache-2.0 */
package io.github.daily3minutes.reminder

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure calendar rules, shared by scheduling and JVM tests. */
object ReminderTimes {
    fun next(now: Instant, zone: ZoneId, hour: Int, minute: Int): Instant {
        val localNow = now.atZone(zone)
        val time = LocalTime.of(hour, minute)
        var candidate = ZonedDateTime.of(localNow.toLocalDate(), time, zone)
        if (!candidate.toInstant().isAfter(now)) {
            candidate = ZonedDateTime.of(localNow.toLocalDate().plusDays(1), time, zone)
        }
        return candidate.toInstant()
    }

    /** Inexact same-day delivery is allowed; an old day's alarm is never caught up. */
    fun isCurrentDay(now: Instant, scheduled: Instant, zone: ZoneId): Boolean =
        !now.isBefore(scheduled) && now.atZone(zone).toLocalDate() == scheduled.atZone(zone).toLocalDate()
}
