/* SPDX-License-Identifier: Apache-2.0 */
package io.github.daily3minutes.reminder

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimesTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test fun beforeTimeSchedulesToday() {
        assertEquals(
            Instant.parse("2026-10-24T13:00:00Z"),
            ReminderTimes.next(Instant.parse("2026-10-24T12:59:00Z"), shanghai, 21, 0),
        )
    }

    @Test fun atOrAfterTimeSchedulesTomorrowWithoutCatchup() {
        val expected = Instant.parse("2026-10-25T13:00:00Z")
        assertEquals(expected, ReminderTimes.next(Instant.parse("2026-10-24T13:00:00Z"), shanghai, 21, 0))
        assertEquals(expected, ReminderTimes.next(Instant.parse("2026-10-24T14:00:00Z"), shanghai, 21, 0))
    }

    @Test fun timeZoneUsesLocalDate() {
        assertEquals(
            Instant.parse("2026-10-25T04:00:00Z"),
            ReminderTimes.next(Instant.parse("2026-10-24T14:00:00Z"), ZoneId.of("America/Los_Angeles"), 21, 0),
        )
    }

    @Test fun daylightSavingGapAdvancesToValidLocalTime() {
        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z"),
            ReminderTimes.next(Instant.parse("2026-03-08T05:00:00Z"), ZoneId.of("America/New_York"), 2, 30),
        )
    }

    @Test fun lateSameDayAlarmMayDeliverButNeverOnNextDayOrBeforeScheduledTime() {
        val scheduled = Instant.parse("2026-10-24T13:00:00Z")
        assertTrue(ReminderTimes.isCurrentDay(Instant.parse("2026-10-24T15:00:00Z"), scheduled, shanghai))
        assertFalse(ReminderTimes.isCurrentDay(Instant.parse("2026-10-24T16:00:00Z"), scheduled, shanghai))
        assertFalse(ReminderTimes.isCurrentDay(Instant.parse("2026-10-24T12:59:59Z"), scheduled, shanghai))
    }

    @Test(expected = java.time.DateTimeException::class)
    fun invalidTimeIsRejected() {
        ReminderTimes.next(Instant.parse("2026-10-24T13:00:00Z"), shanghai, 24, 0)
    }
}
