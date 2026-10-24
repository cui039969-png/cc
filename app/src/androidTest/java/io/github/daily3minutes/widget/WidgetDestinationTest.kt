/* SPDX-License-Identifier: Apache-2.0 */
package io.github.daily3minutes.widget

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.daily3minutes.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the actual widget Intent factory and MainActivity navigation without editing records.
 * A fresh Activity is not evidence of a force-stopped process or of Launcher-host rendering.
 */
@RunWith(AndroidJUnit4::class)
class WidgetDestinationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun freshActivityTodayIntentShowsTodayEditor() {
        ActivityScenario.launch<MainActivity>(WidgetNavigation.intent(context, "today")).use {
            awaitText("每天3分钟")
            compose.onNodeWithText("今天真正学会了什么？").assertIsDisplayed()
        }
    }

    @Test fun freshActivityHistoryIntentShowsSearchAndCalendar() {
        ActivityScenario.launch<MainActivity>(WidgetNavigation.intent(context, "history")).use {
            awaitText("历史记录")
            compose.onNodeWithText("搜索三个回答").assertIsDisplayed()
        }
    }

    @Test fun warmActivityReceivesBothWidgetDestinationsWithoutCreatingAnotherActivity() {
        ActivityScenario.launch<MainActivity>(WidgetNavigation.intent(context, "today")).use { scenario ->
            awaitText("每天3分钟")
            var original: MainActivity? = null
            scenario.onActivity {
                original = it
                it.startActivity(WidgetNavigation.intent(it, "history"))
            }
            awaitText("历史记录")
            compose.onNodeWithText("搜索三个回答").assertIsDisplayed()
            scenario.onActivity {
                assertSame(original, it)
                it.startActivity(WidgetNavigation.intent(it, "today"))
            }
            awaitText("每天3分钟")
            compose.onNodeWithText("今天真正学会了什么？").assertIsDisplayed()
            scenario.onActivity { assertSame(original, it) }
        }
    }

    @Test fun widgetDestinationsHaveDistinctPendingIntentIdentityAndResolveDateAtClickTime() {
        val today = WidgetNavigation.intent(context, "today")
        val history = WidgetNavigation.intent(context, "history")
        assertFalse(today.filterEquals(history))
        assertEquals(MainActivity::class.java.name, today.component?.className)
        assertEquals(MainActivity::class.java.name, history.component?.className)
        assertFalse("A widget rendered yesterday must still open today's editor", today.hasExtra("date"))
        assertEquals("today", today.getStringExtra("destination"))
        assertEquals("history", history.getStringExtra("destination"))
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }
}
