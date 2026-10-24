// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.domain

import io.github.daily3minutes.data.DailyRecord
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DailyRulesTest {
    @Test fun localMidnightAndZoneChangesOnlyAffectToday() {
        val instant = Instant.parse("2026-10-23T16:00:00Z")
        val clock = Clock.fixed(instant, ZoneId.of("UTC"))
        assertEquals("2026-10-24", DailyDates(clock, ZoneId.of("Asia/Shanghai")).todayKey())
        assertEquals("2026-10-23", DailyDates(clock, ZoneId.of("America/Los_Angeles")).todayKey())
        assertEquals("2026-10-23", DailyDates(Clock.offset(clock, java.time.Duration.ofMillis(-1)), ZoneId.of("Asia/Shanghai")).todayKey())
    }

    @Test fun leapDayIsStrictAndCanonical() {
        assertEquals("2024-02-29", RecordRules.validateDate("2024-02-29").toString())
        listOf("2026-02-29", "2026-02-30", "2026-13-01", "2026-2-01", "26-01-01", "0000-01-01", "2026-10-24T00:00:00Z").forEach {
            assertThrows(IllegalArgumentException::class.java) { RecordRules.validateDate(it) }
        }
    }

    @Test fun emojiCountAsOneCodePointAndUnpairedSurrogatesAreRejected() {
        val emoji = "\uD83D\uDE80"
        val allowed = emoji.repeat(RecordRules.MAX_FIELD_CODE_POINTS)
        assertEquals(RecordRules.MAX_FIELD_CODE_POINTS, RecordRules.codePointCount(allowed))
        RecordRules.validateText(allowed)
        assertThrows(IllegalArgumentException::class.java) { RecordRules.validateText(allowed + emoji) }
        assertThrows(IllegalArgumentException::class.java) { RecordRules.validateText("\uD800") }
        assertThrows(IllegalArgumentException::class.java) { RecordRules.validateText("\uDC00") }
    }

    @Test fun handoffExcludesTodayAndConnectsAcrossMissingDays() {
        val records = listOf(record("2026-10-20", "旧目标"), record("2026-10-24", "今天的明日目标"))
        assertEquals("2026-10-20", latestPastRecord(records, "2026-10-24") { it.date }?.date)
    }

    @Test fun handoffKeepsClosestRecordEvenWhenItsGoalIsEmpty() {
        val records = listOf(record("2026-10-22", "更老目标"), record("2026-10-23", ""))
        assertEquals("", latestPastRecord(records, "2026-10-24") { it.date }?.nextPriority)
        assertNull(latestPastRecord(records, "2026-10-20") { it.date })
        assertNull(latestPastRecord(emptyList<DailyRecord>(), "2026-10-24") { it.date })
    }

    private fun record(date: String, goal: String) = DailyRecord(date, "虚构学习", "", goal, 1L, 1L)
}
