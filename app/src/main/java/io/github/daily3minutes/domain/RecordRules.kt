// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.domain

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Shared input and backup constraints. Text limits count Unicode code points, not UTF-16 units. */
object RecordRules {
    const val MAX_FIELD_CODE_POINTS = 20_000
    private val datePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

    fun validateDate(date: String): LocalDate {
        require(datePattern.matches(date)) { "日期必须使用 yyyy-MM-dd 格式" }
        val parsed = try {
            LocalDate.parse(date)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("日期不是有效的日历日期")
        }
        require(parsed.year in 1..9999) { "日期年份必须在 0001–9999 之间" }
        return parsed
    }

    fun codePointCount(text: String): Int = text.codePointCount(0, text.length)

    fun validateText(text: String) {
        require(codePointCount(text) <= MAX_FIELD_CODE_POINTS) { "每个回答最多 20000 字" }
        // Reject isolated UTF-16 surrogates rather than replacing them during a UTF-8 backup.
        var index = 0
        while (index < text.length) {
            val character = text[index]
            when {
                Character.isHighSurrogate(character) -> {
                    require(index + 1 < text.length && Character.isLowSurrogate(text[index + 1])) {
                        "文本包含无效的 Unicode 字符"
                    }
                    index += 2
                }
                Character.isLowSurrogate(character) -> throw IllegalArgumentException("文本包含无效的 Unicode 字符")
                else -> index++
            }
        }
    }
}

/** Zone changes affect today's key only; stored historical keys are never converted. */
class DailyDates(
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun today(): LocalDate = LocalDate.now(clock.withZone(zoneId))
    fun todayKey(): String = today().toString()
}

/** Pure version of the database handoff ordering, useful for fixtures and tests. */
fun <T> latestPastRecord(records: Iterable<T>, today: String, dateOf: (T) -> String): T? {
    RecordRules.validateDate(today)
    return records.filter { dateOf(it) < today }.maxByOrNull(dateOf)
}
