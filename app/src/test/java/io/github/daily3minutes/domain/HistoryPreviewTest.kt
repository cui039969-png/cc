// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.domain

import io.github.daily3minutes.data.DailyRecord
import org.junit.Assert.*
import org.junit.Test

class HistoryPreviewTest {
    private fun row(learned: String = "", unclear: String = "", goal: String = "") =
        DailyRecord("2026-10-24", learned, unclear, goal, 1, 1)

    @Test fun secondFieldHitIsVisibleDespiteLongFirstAnswer() {
        val preview = historyPreview(row("虚构内容".repeat(2_000), "虚构疑问：参数分类"), "参数")
        assertEquals("还没弄懂", preview.label)
        assertEquals("参数", preview.text.substring(preview.matchStart, preview.matchEnd))
        assertFalse(preview.text.contains("虚构内容"))
    }

    @Test fun distantHitInThirdFieldIncludesShortContextAndLiteralSymbols() {
        val preview = historyPreview(row(goal = "前文".repeat(3_000) + "特殊%_中文" + "后文".repeat(3_000)), "%_")
        assertEquals("明日优先", preview.label)
        assertTrue(preview.text.startsWith("…")); assertTrue(preview.text.endsWith("…"))
        assertEquals("%_", preview.text.substring(preview.matchStart, preview.matchEnd))
        assertTrue(preview.text.length < 150)
    }

    @Test fun englishCaseIsIgnoredButOriginalSpellingIsKept() {
        val preview = historyPreview(row("虚构 Kotlin Study"), "kOtLiN")
        assertEquals("Kotlin", preview.text.substring(preview.matchStart, preview.matchEnd))
    }

    @Test fun emojiAtContextBoundariesRemainsValidUnicode() {
        val preview = historyPreview(row(goal = "😀".repeat(100) + "目标" + "😀".repeat(200)), "目标")
        RecordRules.validateText(preview.text)
        assertEquals("目标", preview.text.substring(preview.matchStart, preview.matchEnd))
    }

    @Test fun newlinesBeforeTheHitDoNotPushItOutOfThePreview() {
        val preview = historyPreview(row(unclear = "\n".repeat(300) + "虚构命中"), "命中")
        assertFalse(preview.text.contains('\n'))
        assertEquals("命中", preview.text.substring(preview.matchStart, preview.matchEnd))
    }

    @Test fun emptyQueryUsesFirstNonblankAnswerAndKeepsEmptyRecordsMarked() {
        assertEquals("还没弄懂", historyPreview(row(unclear = "虚构疑问"), "").label)
        assertEquals("虚构疑问", historyPreview(row(unclear = "虚构疑问"), "").text)
        assertEquals(-1, historyPreview(row("虚构记录"), "").matchStart)
        assertEquals("空记录", historyPreview(row(), "").text)
    }
}
