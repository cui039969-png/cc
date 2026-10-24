// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.domain

import io.github.daily3minutes.data.DailyRecord
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HistoryRangeTest {
    @Test fun rollingWindowIncludesTodayAndOldestDateButNeverTomorrow() {
        val rows = historyDays("2026-10-24", HistoryRange.LAST_7_DAYS, listOf(
            record("2026-10-25", "未来目标"), record("2026-10-24", "今天目标"),
            record("2026-10-18", "最早目标"), record("2026-10-17", "范围外目标"),
        ))
        assertEquals((24 downTo 18).map { "2026-10-$it" }, rows.map { it.date })
        assertEquals("今天目标", rows.first().goal)
        assertEquals("最早目标", rows.last().goal)
        assertEquals("2026-10-25", rows.first().targetDate)
    }

    @Test fun threeDayWindowCrossesLeapMonthUsingCalendarDates() {
        val rows = historyDays(LocalDate.of(2024, 3, 1), HistoryRange.LAST_3_DAYS, emptyList())
        assertEquals(listOf("2024-03-01", "2024-02-29", "2024-02-28"), rows.map { it.date })
        assertEquals(listOf("2024-03-02", "2024-03-01", "2024-02-29"), rows.map { it.targetDate })
    }

    @Test fun rollingWindowCrossesYearAndUsesExactlyThirtyDays() {
        val rows = historyDays("2026-01-02", HistoryRange.LAST_30_DAYS, emptyList())
        assertEquals(30, rows.size)
        assertEquals("2026-01-02", rows.first().date)
        assertEquals("2025-12-04", rows.last().date)
        assertEquals("2026-01-01", rows[1].date)
        assertEquals("2025-12-31", rows[2].date)
    }

    @Test fun missingDayAndSavedEmptyGoalStayDistinctWithoutCarryingOlderGoal() {
        val rows = historyDays("2026-10-24", HistoryRange.LAST_3_DAYS, listOf(
            record("2026-10-22", "不能带到后天的旧目标"), record("2026-10-23", "  \n"),
        ))
        assertNull(rows[0].record)
        assertNull(rows[0].goal)
        assertNotNull(rows[1].record)
        assertEquals("  \n", rows[1].goal)
        assertEquals("2026-10-24", rows[1].targetDate)
        assertEquals("不能带到后天的旧目标", rows[2].goal)
        assertEquals("2026-10-23", rows[2].targetDate)
    }

    @Test fun allModeHasOnlySavedRowsAndRetainsFutureEntries() {
        val rows = historyDays("2026-10-24", HistoryRange.ALL, listOf(
            record("2026-09-01", "旧目标"), record("2026-10-26", "未来已存目标"),
            record("2026-10-24", ""),
        ))
        assertEquals(listOf("2026-10-26", "2026-10-24", "2026-09-01"), rows.map { it.date })
        assertTrue(rows.all { it.record != null })
        assertEquals("未来已存目标", rows.first().goal)
        assertTrue(historyDays("2026-10-24", HistoryRange.ALL, emptyList()).isEmpty())
    }

    @Test fun inputOrderDoesNotAffectAnyRangeOrStoredRecordIdentity() {
        val saved = listOf(record("2026-10-20", "一"), record("2026-10-24", "二"), record("2026-10-23", "三"))
        HistoryRange.entries.forEach { range ->
            val rows = historyDays("2026-10-24", range, saved)
            assertEquals(rows, historyDays("2026-10-24", range, saved.reversed()))
            rows.filter { it.record != null }.forEach { row ->
                assertSame(saved.single { it.date == row.date }, row.record)
                assertEquals(row.date, row.sourceDate)
            }
        }
    }

    @Test fun supportedYearLimitsDoNotProduceInvalidKeysOrCrash() {
        val earliest = historyDays("0001-01-02", HistoryRange.LAST_7_DAYS, emptyList())
        assertEquals(listOf("0001-01-02", "0001-01-01"), earliest.map { it.date })
        val latest = historyDays("9999-12-31", HistoryRange.LAST_3_DAYS, listOf(record("9999-12-31", "年末目标")))
        assertNull(latest.first().targetDate)
        assertEquals("9999-12-31", latest[1].targetDate)
        (earliest + latest).forEach { row ->
            RecordRules.validateDate(row.date)
            row.targetDate?.let(RecordRules::validateDate)
        }
    }

    @Test fun malformedDatesAndDuplicateDaysFailRatherThanSilentlyReorderOrOverwrite() {
        listOf("2026-2-01", "2026-02-29", "0000-01-01", "2026-10-24T00:00:00Z").forEach { date ->
            assertThrows(IllegalArgumentException::class.java) { historyDays(date, HistoryRange.ALL, emptyList()) }
            assertThrows(IllegalArgumentException::class.java) { historyDays("2026-10-24", HistoryRange.ALL, listOf(record(date, "无效日期"))) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            historyDays("2026-10-24", HistoryRange.ALL, listOf(record("2026-10-24", "一"), record("2026-10-24", "二")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            historyDays(LocalDate.of(10000, 1, 1), HistoryRange.LAST_3_DAYS, emptyList())
        }
    }

    private fun record(date: String, goal: String) = DailyRecord(date, "虚构学习", "", goal, 1L, 1L)
}
