// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The test Application prefixes preferences and replaces Room; no real entries/settings are read. */
@RunWith(AndroidJUnit4::class)
class LanguageAndGoogleUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun prepare() { context.getSharedPreferences("appearance", 0).edit().putString("language", "zh").commit() }
    @After fun restore() {
        // This screen edits the test Application's shared in-memory repository. Do not leave
        // today's synthetic entry behind for the real RemoteViews host tests that follow.
        kotlinx.coroutines.runBlocking {
            (context.applicationContext as io.github.daily3minutes.DailyApp).repository.delete(java.time.LocalDate.now().toString())
        }
        context.getSharedPreferences("appearance", 0).edit().remove("language").commit()
    }

    @Test fun englishEditorAndHistoryMenuAreTranslatedButSyntheticAnswerIsKept() {
        context.getSharedPreferences("appearance", 0).edit().putString("language", "en").commit()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("answer-0").performTextInput("合成答案 stays unchanged 😀")
        compose.onNodeWithText("What did I truly learn today?").assertIsDisplayed()
        compose.onNodeWithTag("answer-0").assertTextContains("合成答案 stays unchanged 😀")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.onNodeWithText("History", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("history-menu").performClick()
        compose.onNodeWithText("All entries").assertIsDisplayed()
        compose.onNodeWithText("Last 7 days").assertIsDisplayed()
        capture("english-history-menu.png")
    }
    @Test fun unconfiguredGoogleIsClearlyUnavailableAndOfflineBackupRemainsEnabled() {
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("设置", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("daily-settings").performScrollToNode(hasTestTag("google-config-pending"))
        compose.onNodeWithTag("google-config-pending").assertIsDisplayed()
        compose.onNodeWithTag("connect-google").assertIsNotEnabled()
        capture("google-pending.png")
        compose.onNodeWithTag("daily-settings").performScrollToNode(hasText("导出 JSON 备份"))
        compose.onNodeWithText("导出 JSON 备份").assertIsEnabled()
    }

    private fun capture(name: String) {
        val directory = java.io.File(context.cacheDir, "v040-screenshots").apply { mkdirs() }
        java.io.File(directory, name).outputStream().use { output ->
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
