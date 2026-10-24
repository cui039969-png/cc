// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.daily3minutes.data.DailyDatabase
import io.github.daily3minutes.data.DailyRepository
import io.github.daily3minutes.domain.HistoryRange
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** All records and screenshots belong to this isolated synthetic Room fixture. */
@RunWith(AndroidJUnit4::class)
class HistoryUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: DailyDatabase
    private lateinit var repository: DailyRepository
    private lateinit var model: DailyViewModel

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC)
        database = Room.inMemoryDatabaseBuilder(context, DailyDatabase::class.java).build()
        repository = DailyRepository(database, clock)
        runBlocking {
            repository.save("2026-10-25", "虚构未来学习", "", "虚构未来目标")
            repository.save("2026-10-24", "虚构今天学习", "", "虚构今天目标")
            repository.save("2026-10-23", "虚构昨日学习，尚未设置目标", "", "")
            repository.save("2026-10-20", "虚构二十日学习", "虚构二十日疑问", "虚构二十日目标")
            repository.save("2026-10-17", "虚构范围外学习", "", "虚构范围外目标")
            repository.save("2026-09-01", "虚构旧学习", "", "虚构旧目标")
        }
        model = DailyViewModel(repository, clock = clock, zoneProvider = { ZoneOffset.UTC })
    }

    @After fun tearDown() {
        compose.runOnIdle { model.viewModelScope.cancel() }
        database.close()
    }

    @Test fun defaultSevenDaysShowCalendarAndEachDatesOwnHandoffIncludingMissingDays() {
        openHistory()
        assertEquals(HistoryRange.LAST_7_DAYS, model.state.value.historyRange)
        compose.onNodeWithTag("history-calendar").assertIsDisplayed()
        compose.onNodeWithContentDescription("历史菜单").assertIsDisplayed()
        compose.onNodeWithText("近7天交接").assertIsDisplayed()
        dayText("2026-10-24", "虚构今天目标").assertIsDisplayed()
        capture("default-history.png")

        showDay("2026-10-24")
        dayText("2026-10-24", "虚构今天目标").assertIsDisplayed()
        dayText("2026-10-24", "交接给 2026-10-25").assertIsDisplayed()
        showDay("2026-10-23")
        dayText("2026-10-23", "未设置明日目标").assertIsDisplayed()
        showDay("2026-10-22")
        dayText("2026-10-22", "当天未记录").assertIsDisplayed()
        for (day in 21 downTo 18) showDay("2026-10-$day")
        compose.onNodeWithTag("handoff-day-2026-10-17").assertDoesNotExist()
        showDay("2026-10-24")
        compose.onNodeWithTag("handoff-day-2026-10-25").assertDoesNotExist()
    }

    @Test fun visibleDrawerSwitchesThreeThirtyAndAllRecordsWithoutLosingFutureSavedRows() {
        openHistory()
        compose.onNodeWithTag("history-menu").performClick()
        listOf(HistoryRange.ALL, HistoryRange.LAST_3_DAYS, HistoryRange.LAST_7_DAYS, HistoryRange.LAST_30_DAYS).forEach {
            compose.onNodeWithTag("history-range-${it.name}").assertIsDisplayed()
        }
        capture("history-menu.png")
        chooseRange(HistoryRange.LAST_3_DAYS, menuAlreadyOpen = true)
        showDay("2026-10-22")
        compose.onNodeWithTag("handoff-day-2026-10-21").assertDoesNotExist()

        chooseRange(HistoryRange.LAST_30_DAYS)
        showDay("2026-09-25")
        dayText("2026-09-25", "当天未记录").assertIsDisplayed()

        chooseRange(HistoryRange.ALL)
        showDay("2026-10-25")
        dayText("2026-10-25", "虚构未来学习", substring = true).assertIsDisplayed()
        showDay("2026-09-01")
        dayText("2026-09-01", "虚构旧学习", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("handoff-day-2026-09-25").assertDoesNotExist()
    }

    @Test fun swipingHistoryMovesCalendarAwayAndStickyHandleRestoresIt() {
        openHistory()
        compose.onNodeWithTag("history-calendar").assertIsDisplayed()
        // Actual touch gestures exercise the unified scroll, rather than a model-only toggle.
        repeat(2) {
            compose.onNodeWithTag("daily-history").performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        compose.onNodeWithTag("history-calendar").assertIsNotDisplayed()
        compose.onNodeWithTag("history-calendar-toggle").assertIsDisplayed()
        compose.onNodeWithText("展开日历").assertIsDisplayed()
        capture("history-collapsed.png")
        compose.onNodeWithTag("history-calendar-toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("history-calendar").assertIsDisplayed()
        compose.onNodeWithText("收起日历").assertIsDisplayed()
    }

    @Test fun handoffCardOpensItsOwnSourceDateWithAllThreeStoredAnswers() {
        openHistory()
        showDay("2026-10-20")
        compose.onNodeWithTag("handoff-day-2026-10-20").performClick()
        compose.waitUntil(5_000) {
            model.state.value.screen == DailyScreen.EDIT &&
                model.state.value.editor.date == "2026-10-20" && model.state.value.editor.loaded
        }
        assertEquals(listOf("虚构二十日学习", "虚构二十日疑问", "虚构二十日目标"), model.state.value.editor.fields)
        for ((index, answer) in model.state.value.editor.fields.withIndex()) {
            if (index == 0) {
                compose.onNodeWithTag("daily-editor").performScrollToNode(hasTestTag("open-question-0"))
                compose.onNodeWithTag("open-question-0").performClick()
            } else compose.onNodeWithTag("question-tab-$index").performClick()
            compose.waitUntil(5_000) { model.state.value.focusedQuestion == index && !model.state.value.busy }
            compose.onNodeWithTag("answer-$index").assertTextContains(answer)
        }
    }

    @Test fun doubleFontScaleKeepsMenuAndRangeCardsReachable() {
        openHistory(fontScale = 2f)
        compose.onNodeWithTag("history-menu").assertIsDisplayed().performClick()
        compose.onNodeWithTag("history-range-LAST_3_DAYS").assertIsDisplayed()
        chooseRange(HistoryRange.LAST_3_DAYS, menuAlreadyOpen = true)
        showDay("2026-10-22")
        dayText("2026-10-22", "当天未记录").assertIsDisplayed()
        chooseRange(HistoryRange.ALL)
        showDay("2026-09-01")
        dayText("2026-09-01", "虚构旧学习", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("history-menu").assertIsDisplayed()
    }

    @Test fun savedGoalChangesRefreshVisibleTimelineWithoutReopeningHistory() {
        openHistory()
        showDay("2026-10-20")
        dayText("2026-10-20", "虚构二十日目标").assertIsDisplayed()
        runBlocking { repository.save("2026-10-20", "虚构二十日学习", "虚构二十日疑问", "虚构实时更新目标") }
        compose.waitUntil(5_000) {
            model.state.value.results.any { it.date == "2026-10-20" && it.nextPriority == "虚构实时更新目标" }
        }
        dayText("2026-10-20", "虚构实时更新目标").assertIsDisplayed()
        dayText("2026-10-20", "交接给 2026-10-21").assertIsDisplayed()
    }

    @Test fun externalTodayDestinationClosesAnOpenHistoryMenuAndRevealsTheEditor() {
        openHistory()
        compose.onNodeWithTag("history-menu").performClick()
        compose.onNodeWithTag("history-range-ALL").assertIsDisplayed()
        // MainActivity.onNewIntent uses this same route for the desktop today's button.
        compose.runOnIdle { model.externalDestination("today", null) }
        compose.waitUntil(5_000) {
            model.state.value.screen == DailyScreen.TODAY && !model.state.value.busy &&
                model.state.value.editor.loaded
        }
        compose.waitForIdle()
        HistoryRange.entries.forEach { range ->
            compose.onNodeWithTag("history-range-${range.name}").assertIsNotDisplayed()
        }
        compose.onNodeWithTag("daily-editor").assertIsDisplayed()
        compose.onNodeWithTag("daily-editor").performScrollToNode(hasTestTag("open-question-0"))
        compose.onNodeWithTag("open-question-0").performClick()
        compose.waitUntil(5_000) { model.state.value.focusedQuestion == 0 && !model.state.value.busy }
        compose.onNodeWithTag("answer-0").assertIsDisplayed().assertTextContains("虚构今天学习")
        assertEquals("2026-10-24", model.state.value.editor.date)
    }

    private fun openHistory(fontScale: Float = 1f) {
        compose.setContent {
            val current = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(current.density, fontScale)) {
                Daily3Theme { DailyAppContent(model, onFinish = {}) }
            }
        }
        compose.waitUntil(5_000) { model.state.value.editor.loaded && model.state.value.results.size == 6 }
        compose.onNodeWithText("历史").performClick()
        compose.waitUntil(5_000) { model.state.value.screen == DailyScreen.HISTORY && !model.state.value.busy }
        compose.waitForIdle()
    }

    private fun showDay(date: String) {
        compose.onNodeWithTag("daily-history").performScrollToNode(hasTestTag("handoff-day-$date"))
        compose.onNodeWithTag("handoff-day-$date").assertIsDisplayed()
    }

    private fun dayText(date: String, text: String, substring: Boolean = false) = compose.onNode(
        hasText(text, substring = substring) and hasAnyAncestor(hasTestTag("handoff-day-$date")),
        useUnmergedTree = true,
    )

    private fun chooseRange(range: HistoryRange, menuAlreadyOpen: Boolean = false) {
        if (!menuAlreadyOpen) compose.onNodeWithTag("history-menu").performClick()
        compose.onNodeWithTag("history-range-${range.name}").performClick()
        compose.waitUntil(5_000) { model.state.value.historyRange == range }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val directory = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "history-v030").apply { mkdirs() }
        File(directory, name).outputStream().use { output ->
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
