// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import android.content.Context
import android.os.Bundle
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daily3minutes.data.DailyDatabase
import io.github.daily3minutes.data.DailyRepository
import io.github.daily3minutes.domain.historyPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.io.File

/**
 * UI and Room end-to-end coverage uses its own in-memory database and synthetic text.
 * The production Application.repository and its on-disk database are never accessed.
 * These are instrumentation tests: source availability does not mean a device run passed.
 */
@RunWith(AndroidJUnit4::class)
class EditorUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: DailyDatabase
    private lateinit var repository: DailyRepository
    private lateinit var model: DailyViewModel

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC)
        database = Room.inMemoryDatabaseBuilder(context, DailyDatabase::class.java).build()
        repository = DailyRepository(database, clock)
        model = DailyViewModel(repository, clock = clock, zoneProvider = { ZoneOffset.UTC })
    }

    @After fun tearDown() {
        compose.runOnIdle { model.viewModelScope.cancel() }
        database.close()
    }

    @Test fun fillAllAnswersSaveSearchAndReopenTheSameDate() {
        compose.setContent { Daily3Theme { DailyAppContent(model, onFinish = {}) } }
        compose.waitUntil(5_000) { model.state.value.editor.loaded }
        val answers = listOf(
            "虚构 UI 学习：函数单调性",
            "虚构 UI 疑问：特殊%_中文",
            "虚构 UI 目标：完成两题",
        )
        answers.forEachIndexed { index, answer ->
            scrollToAnswer(index)
            compose.onNodeWithTag("answer-$index").performTextInput(answer)
        }
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) { model.state.value.editor.status == SaveStatus.SAVED && !model.state.value.editor.dirty }
        val stored = runBlocking { repository.get("2026-10-24")!! }
        assertEquals(answers, listOf(stored.learned, stored.unclear, stored.nextPriority))

        showOverview()
        compose.onNodeWithText("历史").performClick()
        compose.waitUntil(5_000) { model.state.value.screen == DailyScreen.HISTORY && !model.state.value.busy }
        compose.onNodeWithTag("daily-search").performTextInput("特殊%_中文")
        compose.waitUntil(5_000) { model.state.value.query == "特殊%_中文" && model.state.value.results.size == 1 }
        compose.onNodeWithText("2026-10-24").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { model.state.value.screen == DailyScreen.EDIT && model.state.value.editor.loaded }
        answers.forEachIndexed { index, answer ->
            scrollToAnswer(index)
            compose.onNodeWithTag("answer-$index").assertTextContains(answer)
        }
    }

    @Test fun doubleFontScaleKeepsQuestionsAndMonthNavigationUsable() {
        compose.setContent {
            val current = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(current.density, fontScale = 2f)) {
                Daily3Theme { DailyAppContent(model, onFinish = {}) }
            }
        }
        compose.waitUntil(5_000) { model.state.value.editor.loaded }
        for (index in 0..2) {
            scrollToAnswer(index)
            compose.onNodeWithTag("answer-$index").assertIsDisplayed()
        }
        showOverview()
        compose.onNodeWithText("历史").performClick()
        compose.waitUntil(5_000) { model.state.value.screen == DailyScreen.HISTORY }
        compose.onNodeWithText("2026年10月").assertExists()
        compose.onNodeWithContentDescription("上个月").performClick()
        compose.onNodeWithText("2026年9月").assertExists()
        compose.onNodeWithContentDescription("回到本月").performClick()
        compose.onNodeWithText("2026年10月").assertExists()
    }

    @Test fun nativeAccessibilityTreeNamesEachEditableQuestionAndCanSetItsText() {
        compose.setContent { Daily3Theme { DailyAppContent(model, onFinish = {}) } }
        compose.waitUntil(5_000) { model.state.value.editor.loaded }
        val labels = listOf("今天真正学会了什么？", "今天还有什么没弄懂？", "明天优先解决哪一件事？")
        fun hasLabel(node: AccessibilityNodeInfo, label: String): Boolean =
            node.contentDescription?.toString() == label ||
                (0 until node.childCount).any { node.getChild(it)?.let { child -> hasLabel(child, label) } == true }
        fun editable(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
            if (node.isEditable && hasLabel(node, label)) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { editable(it, label)?.let { found -> return found } }
            }
            return null
        }
        for ((index, label) in labels.withIndex()) {
            scrollToAnswer(index)
            compose.waitForIdle()
            val root = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
            val field = requireNotNull(editable(root, label)) { "Editable question has no native accessible name: $index" }
            val answer = "虚构无障碍输入 $index"
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, answer)
            }
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
            compose.waitUntil(5_000) { model.state.value.editor.fields[index] == answer }
        }
    }

    @Test fun longHandoffExpandsAndSourceNavigationSavesTodaysPendingAnswer() {
        val goal = (1..16).joinToString("\n") { "虚构交接第${it}行：完成一道分类讨论题。" }
        runBlocking { repository.save("2026-10-23", "虚构昨日学习", "", goal) }
        compose.setContent { Daily3Theme { DailyAppContent(model, onFinish = {}) } }
        compose.runOnIdle { model.refreshToday() }
        compose.waitUntil(5_000) { model.state.value.handoff != null && model.state.value.editor.loaded }
        compose.onNodeWithTag("handoff-expand").performScrollTo().performClick()
        compose.onNodeWithText("收起目标").assertExists()
        compose.onNodeWithTag("handoff-goal").assertTextContains(goal)
        capture("v020-handoff-expanded.png")
        compose.onNodeWithTag("handoff-expand").performScrollTo().performClick()
        compose.runOnIdle { model.edit(0, "虚构：查看来源前尚未防抖保存") }
        compose.onNodeWithTag("handoff-source").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.state.value.screen == DailyScreen.EDIT && model.state.value.editor.date == "2026-10-23" && model.state.value.editor.loaded }
        assertEquals(goal, model.state.value.editor.nextPriority)
        assertEquals("虚构：查看来源前尚未防抖保存", runBlocking { repository.get("2026-10-24") }?.learned)
    }

    @Test fun searchShowsTheMatchingSecondAnswerAfterThousandsOfCharacters() {
        val unclear = "虚构疑问" + "\n".repeat(300) + "特殊%_中文分类讨论"
        runBlocking { repository.save("2026-10-22", "虚构长学习".repeat(2_000), unclear, "虚构明日目标") }
        compose.setContent { Daily3Theme { DailyAppContent(model, onFinish = {}) } }
        compose.waitUntil(5_000) { model.state.value.editor.loaded }
        showOverview()
        compose.onNodeWithText("历史").performClick()
        compose.waitUntil(5_000) { model.state.value.screen == DailyScreen.HISTORY && !model.state.value.busy }
        compose.onNodeWithTag("daily-search").performTextInput("特殊%_中文")
        compose.waitUntil(5_000) { model.state.value.results.size == 1 && model.state.value.query == "特殊%_中文" }
        val preview = historyPreview(model.state.value.results.single(), "特殊%_中文")
        compose.onNodeWithText("还没弄懂").assertIsDisplayed()
        compose.onNodeWithText(preview.text).assertIsDisplayed()
        capture("v020-search-match.png")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun landscapeNavigationKeepsSavingAndAllDestinationsUsable() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(820.dp, 360.dp))) {
                Daily3Theme { DailyAppContent(model, onFinish = {}) }
            }
        }
        compose.waitUntil(5_000) { model.state.value.editor.loaded }
        compose.onNodeWithTag("daily-navigation-rail").assertIsDisplayed()
        listOf("今日", "历史", "设置").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        scrollToAnswer(0)
        compose.onNodeWithTag("answer-0").performTextInput("虚构横屏保存")
        showOverview()
        compose.onNodeWithText("历史").performClick()
        compose.waitUntil(5_000) { model.state.value.screen == DailyScreen.HISTORY && !model.state.value.busy }
        assertEquals("虚构横屏保存", runBlocking { repository.get("2026-10-24") }?.learned)
        showOverview()
        compose.onNodeWithText("设置").performClick()
        compose.waitUntil(5_000) { model.state.value.screen == DailyScreen.SETTINGS && !model.state.value.busy }
        compose.onNodeWithText("今日").performClick()
        compose.waitUntil(5_000) { model.state.value.screen == DailyScreen.TODAY && !model.state.value.busy }
        scrollToAnswer(0)
        compose.onNodeWithTag("answer-0").assertTextContains("虚构横屏保存")
        listOf("今日", "历史", "设置").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        capture("v020-wide-navigation.png")
    }

    @Test fun settingsShowsInstalledVersionAndReviewedSourcesWithoutOpeningADialog() {
        compose.setContent { Daily3Theme { DailyAppContent(model, onFinish = {}) } }
        compose.waitUntil(5_000) { model.state.value.editor.loaded }
        showOverview()
        compose.onNodeWithText("设置").performClick()
        compose.waitUntil(5_000) { model.state.value.screen == DailyScreen.SETTINGS && !model.state.value.busy }
        compose.onNodeWithTag("daily-settings").performScrollToNode(hasTestTag("app-version"))
        compose.onNodeWithTag("app-version").assertTextContains("每天3分钟  ${io.github.daily3minutes.BuildConfig.VERSION_NAME}")
        compose.onNodeWithTag("open-source-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("daily-settings").performScrollToNode(androidx.compose.ui.test.hasText("Now in Android"))
        compose.onNodeWithText("Now in Android").assertIsDisplayed()
        capture("v020-open-source.png")
        compose.onNodeWithTag("daily-settings").performScrollToNode(androidx.compose.ui.test.hasText("Quillpad"))
        compose.onNodeWithText("Quillpad").assertIsDisplayed()
    }

    @Test fun fullPageTabsAndDateRailPreserveLongAnswersOnTheirOwnDates() {
        compose.setContent { Daily3Theme { DailyAppContent(model, onFinish = {}) } }
        compose.waitUntil(5_000) { model.state.value.editor.loaded }
        val longText = (1..30).joinToString("\n") { "虚构第${it}行：整理今天学到的知识。" }
        scrollToAnswer(0)
        compose.onNodeWithTag("answer-0").performTextInput(longText)
        scrollToAnswer(1)
        compose.onNodeWithTag("answer-1").performTextInput("虚构今日疑问")
        compose.onNodeWithTag("editor-date-days").performScrollToNode(hasTestTag("editor-day-2026-10-23"))
        compose.onNodeWithTag("editor-day-2026-10-23").performClick()
        compose.waitUntil(5_000) { model.state.value.editor.date == "2026-10-23" && model.state.value.editor.loaded && !model.state.value.busy }
        assertEquals(1, model.state.value.focusedQuestion)
        compose.onNodeWithTag("answer-1").performTextInput("虚构前一天疑问")
        compose.onNodeWithTag("editor-day-2026-10-24").performClick()
        compose.waitUntil(5_000) { model.state.value.editor.date == "2026-10-24" && model.state.value.editor.loaded && !model.state.value.busy }
        compose.onNodeWithTag("answer-1").assertTextContains("虚构今日疑问")
        scrollToAnswer(0)
        compose.onNodeWithTag("answer-0").assertTextContains(longText)
        compose.onNodeWithTag("voice-record").assertIsDisplayed()
        compose.onNodeWithTag("voice-dictate").assertIsDisplayed()
        compose.onNodeWithTag("answer-0").performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        capture("v050-focused-editor.png", "focused-editor")
        showOverview()
        assertEquals(longText, runBlocking { repository.get("2026-10-24") }!!.learned)
        assertEquals("虚构前一天疑问", runBlocking { repository.get("2026-10-23") }!!.unclear)
    }

    private fun capture(name: String, tag: String? = null) {
        val directory = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "v020-screenshots").apply { mkdirs() }
        File(directory, name).outputStream().use { output ->
            val node = if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)
            assertTrue(node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun showOverview() {
        if (model.state.value.focusedQuestion != null) {
            compose.onNodeWithContentDescription("返回当天概览").performClick()
            compose.waitUntil(5_000) { !model.state.value.busy && model.state.value.focusedQuestion == null }
        }
    }

    private fun scrollToAnswer(index: Int) {
        if (model.state.value.focusedQuestion == null) {
            compose.onNodeWithTag("daily-editor").performScrollToNode(hasTestTag("open-question-$index"))
            compose.onNodeWithTag("open-question-$index").performClick()
        } else if (model.state.value.focusedQuestion != index) {
            compose.onNodeWithTag("question-tab-$index").performClick()
        }
        compose.waitUntil(5_000) { !model.state.value.busy && model.state.value.focusedQuestion == index }
        if (!compose.onNodeWithTag("answer-$index").isDisplayed()) compose.onNodeWithTag("answer-$index").performScrollTo()
        compose.onNodeWithTag("answer-$index").assertIsDisplayed()
    }
}
