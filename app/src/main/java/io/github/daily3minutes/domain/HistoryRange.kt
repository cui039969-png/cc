// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.domain

import io.github.daily3minutes.data.DailyRecord
import java.time.LocalDate

enum class HistoryRange(val days: Int?, val label: String) {
    LAST_3_DAYS(3, "近3天"),
    LAST_7_DAYS(7, "近7天"),
    LAST_30_DAYS(30, "近30天"),
    ALL(null, "全部记录"),
}

/** A day's own next priority is handed to the following date; it is never carried over a gap. */
data class HistoryDay(
    val date: String,
    val record: DailyRecord?,
    // The backup/editor date domain ends at 9999-12-31. Do not invent an invalid next key.
    val targetDate: String?,
) {
    val sourceDate: String get() = date

    /** Null means no record for this date; blank means a saved record with no next priority. */
    val goal: String? get() = record?.nextPriority
}

/**
 * Rolling windows include today and every date in the selected period, even when no record exists.
 * ALL returns only saved records, including future-dated records the calendar already allows editing.
 * Dates are their stored local keys: this calculation does not reinterpret them in another time zone.
 */
fun historyDays(today: String, range: HistoryRange, records: Iterable<DailyRecord>): List<HistoryDay> =
    historyDays(RecordRules.validateDate(today), range, records)

fun historyDays(today: LocalDate, range: HistoryRange, records: Iterable<DailyRecord>): List<HistoryDay> {
    RecordRules.validateDate(today.toString())
    val byDate = mutableMapOf<String, DailyRecord>()
    records.forEach { record ->
        RecordRules.validateDate(record.date)
        require(byDate.put(record.date, record) == null) { "同一天只能有一条历史记录" }
    }
    val dates = range.days?.let { count ->
        (0 until count).mapNotNull { offset ->
            today.minusDays(offset.toLong()).takeIf { it.year >= 1 }?.toString()
        }
    } ?: byDate.keys.sortedDescending()
    return dates.map { date ->
        val next = RecordRules.validateDate(date).plusDays(1)
        HistoryDay(date, byDate[date], next.takeIf { it.year <= 9999 }?.toString())
    }
}
