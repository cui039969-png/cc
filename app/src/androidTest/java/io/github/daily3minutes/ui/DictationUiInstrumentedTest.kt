// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daily3minutes.SyntheticTestApplication
import io.github.daily3minutes.audio.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** No device recognizer or microphone is invoked: callbacks are generated text. */
@RunWith(AndroidJUnit4::class)
class DictationUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app: SyntheticTestApplication get() = ApplicationProvider.getApplicationContext()
    private var cancelled = false
    private var finish: ((String) -> Unit)? = null

    @Before fun setup() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        app.syntheticDictation = DictationProvider { language, result, _ ->
            assertEquals("zh-CN", language); finish = result
            object : DictationSession {
                override fun stop() { result("虚构识别文字") }
                override fun cancel() { cancelled = true }
            }
        }
        compose.onNodeWithTag("open-question-0").performClick()
    }
    @After fun cleanup() {
        // Flush and leave the editor before deleting the shared synthetic fixture.
        // Otherwise its pending autosave can recreate a row after teardown.
        compose.onNodeWithContentDescription("返回当天概览").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("open-question-0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("历史", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("daily-history").fetchSemanticsNodes().isNotEmpty() }
        app.syntheticDictation = null
        kotlinx.coroutines.runBlocking { app.repository.delete(java.time.LocalDate.now().toString()) }
        // The outer runner restores the initial permission after instrumentation exits.
    }
    @Test fun serviceResultIsReviewedBeforeAppendingAndNeverReplacesExistingAnswer() {
        compose.onNodeWithTag("answer-0").performTextReplacement("虚构原有回答")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("voice-dictate").performClick()
        compose.onNodeWithText("正在听写").assertIsDisplayed()
        compose.onNodeWithText("结束识别").performClick()
        compose.onNodeWithText("确认识别文字").assertIsDisplayed()
        compose.onNodeWithTag("answer-0").assertTextContains("虚构原有回答")
        compose.onNodeWithText("追加到回答").performClick()
        compose.onNodeWithTag("answer-0").assertTextContains("虚构原有回答\n虚构识别文字")
    }
    @Test fun cancellingRecognitionStopsItsSessionAndKeepsWrittenText() {
        compose.onNodeWithTag("answer-0").performTextReplacement("虚构保留文字")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("voice-dictate").performClick()
        compose.onNodeWithText("取消").performClick()
        assertTrue(cancelled)
        compose.onNodeWithTag("answer-0").assertTextContains("虚构保留文字")
        compose.onNodeWithText("正在听写").assertDoesNotExist()
    }
}
