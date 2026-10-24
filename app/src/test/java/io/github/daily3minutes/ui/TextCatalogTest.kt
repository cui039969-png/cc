// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import org.junit.Assert.*
import org.junit.Test

class TextCatalogTest {
    @Test fun catalogContainsUnambiguousNavigationAndBackupLabels() {
        assertEquals("History menu", TextCatalog.render("历史菜单", "en"))
        assertEquals("Google Tasks (optional)", TextCatalog.render("Google Tasks（可选）", "en"))
        assertEquals("Backup and restore", TextCatalog.render("备份与恢复", "en"))
        assertEquals("Open-source notices and references", TextCatalog.render("开源来源与参考", "en"))
    }
    @Test fun formattedUiTextLeavesPersonalAnswerCharactersUnchanged() {
        val answer = "合成回答 100% / %s / \\ / 😀"
        assertEquals("Local: $answer", TextCatalog.render("本地：%s", "en", answer))
        assertEquals("本地：$answer", TextCatalog.render("本地：%s", "zh", answer))
    }
    @Test fun interpolatedEditorNoticesAndValidationErrorsHaveEnglishTranslations() {
        assertEquals("Exported 3 entries. Keep your backup safe.", TextCatalog.render("已导出 3 条记录。请妥善保存备份。", "en"))
        assertEquals("Import rejected: The backup is not valid JSON", TextCatalog.render("导入被拒绝：备份文件不是有效的 JSON", "en"))
        assertEquals("Import complete: 4 entries written; 2 conflicting entries skipped.", TextCatalog.render("导入完成，写入 4 条，跳过 2 条冲突记录。", "en"))
    }
    @Test fun sourceQuestionsRemainExactAndEnglishIsSpecific() {
        assertEquals("What is the one thing to tackle first tomorrow?", TextCatalog.render("明天优先解决哪一件事？", "en"))
        assertEquals("今天真正学会了什么？", TextCatalog.render("今天真正学会了什么？", "zh"))
    }
    @Test fun everyCatalogEntryHasSameFormattingArgumentsInBothLanguages() {
        val pattern = Regex("%[1-9]["+'$'+"]s|%02d")
        assertTrue(TextCatalog.english.size > 150)
        TextCatalog.english.forEach { (zh, en) ->
            assertTrue(en.isNotBlank())
            assertEquals(zh, pattern.findAll(zh).map { it.value }.sorted().toList(), pattern.findAll(en).map { it.value }.sorted().toList())
        }
    }
}
