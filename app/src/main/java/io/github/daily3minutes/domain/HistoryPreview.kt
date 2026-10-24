// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.domain

import io.github.daily3minutes.data.DailyRecord

data class HistoryPreview(val label: String, val text: String, val matchStart: Int = -1, val matchEnd: Int = -1)

/** Keep a literal search hit visible even when it follows thousands of characters. */
fun historyPreview(record: DailyRecord, query: String): HistoryPreview {
    val fields = listOf("学会了" to record.learned, "还没弄懂" to record.unclear, "明日优先" to record.nextPriority)
    val selected = if (query.isEmpty()) fields.firstOrNull { it.second.isNotBlank() }
        else fields.firstOrNull { it.second.contains(query, ignoreCase = true) }
    if (selected == null) return HistoryPreview("", if (record.isEmpty) "空记录" else "打开查看完整记录")
    val (label, text) = selected
    val hit = if (query.isEmpty()) -1 else text.indexOf(query, ignoreCase = true)
    // Offset/count use Unicode code points so an emoji cannot be cut into an invalid surrogate.
    val before = if (hit < 0) 0 else minOf(24, text.codePointCount(0, hit))
    val start = if (hit < 0) 0 else text.offsetByCodePoints(hit, -before)
    val matchEnd = if (hit < 0) start else hit + query.length
    val end = text.offsetByCodePoints(matchEnd, minOf(96, text.codePointCount(matchEnd, text.length)))
    val prefix = if (start > 0) "…" else ""
    val beforeHit = text.substring(start, if (hit < 0) start else hit).replace(Regex("\\s+"), " ")
    val match = if (hit < 0) "" else text.substring(hit, matchEnd)
    val afterHit = text.substring(matchEnd, end).replace(Regex("\\s+"), " ")
    val snippet = prefix + beforeHit + match + afterHit + if (end < text.length) "…" else ""
    val relativeHit = if (hit < 0) -1 else prefix.length + beforeHit.length
    return HistoryPreview(label, snippet, relativeHit, if (hit < 0) -1 else relativeHit + query.length)
}
