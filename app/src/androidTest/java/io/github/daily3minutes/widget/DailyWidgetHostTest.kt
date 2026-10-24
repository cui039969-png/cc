/* SPDX-License-Identifier: Apache-2.0 */
package io.github.daily3minutes.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.daily3minutes.ui.MainActivity
import io.github.daily3minutes.DailyApp
import io.github.daily3minutes.data.DailyRecord
import io.github.daily3minutes.backup.BackupEnvelope
import io.github.daily3minutes.backup.BackupManager
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.Instant
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A real AppWidgetHost runs inside a temporary native overlay on the target Activity.
 * No production screen, test manifest component, Launcher access, or record fixture is added.
 * Requires temporary shell grantbind for io.github.daily3minutes; the caller revokes it afterward.
 * SyntheticTestRunner supplies an in-memory repository; user records are never opened.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class DailyWidgetHostTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun compactHostRendersAndBothWidgetButtonsOpenTheirPages() {
        host(widthDp = 140, fileName = "compact.png", verifyNavigation = true)
    }

    @Test fun wideHostRendersAndBothWidgetButtonsOpenTheirPages() {
        host(widthDp = 280, fileName = "wide.png", verifyNavigation = true, verifyMutations = true)
    }

    @Test fun compactHostHandlesThousandsOfChineseCharactersWithoutBreakingActions() {
        host(widthDp = 140, fileName = "long-goal.png", verifyNavigation = true,
            goal = "虚构长文本目标：" + "完成练习并整理疑问。".repeat(300))
    }

    @Test fun compactHostShowsActualSourceDateWhenYesterdayWasMissed() {
        host(widthDp = 140, fileName = "missed-day.png", verifyNavigation = false,
            goal = "虚构：三天前的交接目标", sourceDaysAgo = 3)
    }

    @Test fun wideHostKeepsNearestEmptyGoalInsteadOfSkippingToOlderRecord() {
        host(widthDp = 280, fileName = "nearest-empty.png", verifyNavigation = false,
            goal = "", olderGoal = "虚构：更早的目标不能替代最近的空目标")
    }

    private fun host(widthDp: Int, fileName: String, verifyNavigation: Boolean, goal: String? = null,
        verifyMutations: Boolean = false, sourceDaysAgo: Long = 1, olderGoal: String? = null) {
        val repository = (context.applicationContext as DailyApp).repository
        val today = LocalDate.now()
        val todayDate = today.toString()
        val todayLabel = "今天 · ${today.monthValue}月${today.dayOfMonth}日"
        val sourceDate = today.minusDays(sourceDaysAgo).toString()
        val olderDate = today.minusDays(sourceDaysAgo + 1).toString()
        if (olderGoal != null) runBlocking { repository.save(olderDate, "虚构较早样例", "", olderGoal) }
        if (goal != null) runBlocking { repository.save(sourceDate, "虚构组件样例", "", goal) }
        val expectedGoal = when {
            goal == null -> "还没有交接目标"
            goal.isBlank() -> "上一条记录未设置明日目标"
            else -> goal
        }
        val expectedSource = when {
            goal == null -> "昨日交接"
            sourceDaysAgo == 1L -> "昨天交接 · $sourceDate"
            else -> "上次交接 · $sourceDate"
        }
        val host = AppWidgetHost(context, HOST_ID)
        var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        var overlay: FrameLayout? = null
        var widgetView: AppWidgetHostView? = null
        var ready: WidgetReadyIdler? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(WidgetNavigation.intent(context, "history")) as MainActivity
        fun onActivity(action: (MainActivity) -> Unit) = instrumentation.runOnMainSync { action(activity) }
        try {
            run {
                onActivity { activity ->
                    val manager = AppWidgetManager.getInstance(activity)
                    val provider = ComponentName(activity, DailyWidgetReceiver::class.java)
                    widgetId = host.allocateAppWidgetId()
                    val options = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, HEIGHT_DP)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, HEIGHT_DP)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(widthDp.toFloat(), HEIGHT_DP.toFloat())))
                    }
                    assertTrue(
                        "Requires temporary appwidget grantbind for io.github.daily3minutes",
                        manager.bindAppWidgetIdIfAllowed(widgetId, provider, options),
                    )
                    val info = requireNotNull(manager.getAppWidgetInfo(widgetId))
                    val view = host.createView(activity, widgetId, info).apply {
                        setPadding(0, 0, 0, 0)
                        manager.updateAppWidgetOptions(widgetId, options)
                    }
                    widgetView = view
                    val density = activity.resources.displayMetrics.density
                    val panel = FrameLayout(activity).apply {
                        setBackgroundColor(Color.WHITE)
                        addView(view, FrameLayout.LayoutParams(
                            (widthDp * density).roundToInt(),
                            (HEIGHT_DP * density).roundToInt(),
                            Gravity.CENTER,
                        ))
                    }
                    overlay = panel
                    activity.findViewById<ViewGroup>(android.R.id.content).addView(
                        panel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                    )
                    ready = WidgetReadyIdler(view, expectedGoal).also { IdlingRegistry.getInstance().register(it) }
                    host.startListening()
                    // Explicitly request first render; the Glance receiver owns its asynchronous work.
                    activity.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId)))
                }

                // The idling resource awaits actual host RemoteViews text and has Espresso's bounded timeout.
                onView(withText(expectedGoal)).check(matches(isDisplayed()))
                onView(withText(todayLabel)).check(matches(isDisplayed()))
                onView(withText(expectedSource)).check(matches(isDisplayed()))
                onView(withText("今天还没填写")).check(matches(isDisplayed()))
                onView(withText("填写今天")).check(matches(isDisplayed()))
                onView(withText("历史记录")).check(matches(isDisplayed()))
                onActivity {
                    val view = requireNotNull(widgetView)
                    val upper = requireNotNull(findText(view, expectedGoal))
                    val lower = requireNotNull(findText(view, todayLabel))
                    val upperBounds = Rect()
                    val lowerBounds = Rect()
                    assertTrue(upper.getGlobalVisibleRect(upperBounds))
                    assertTrue(lower.getGlobalVisibleRect(lowerBounds))
                    assertTrue("Past handoff must be above today's separate panel", upperBounds.bottom <= lowerBounds.top)
                    assertFullyVisible(requireNotNull(findText(view, expectedSource)), view)
                    assertFullyVisible(lower, view)
                    assertFullyVisible(requireNotNull(findText(view, "填写今天")), view)
                    assertFullyVisible(requireNotNull(findText(view, "历史记录")), view)
                }
                onActivity { activity ->
                    val view = requireNotNull(widgetView)
                    assertTrue(view.width > 0 && view.height > 0)
                    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(Canvas(image))
                        val folder = requireNotNull(activity.getExternalFilesDir("widget-test"))
                        File(folder, fileName).outputStream().use { stream ->
                            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        }
                    } finally {
                        image.recycle()
                    }
                }

                if (verifyMutations) {
                    fun awaitWidgetText(expected: String) {
                        compose.waitUntil(10_000) {
                            var found = false
                            onActivity { found = findText(requireNotNull(widgetView), expected) != null }
                            found
                        }
                        onView(withText(expected)).check(matches(isDisplayed()))
                    }
                    runBlocking { repository.save(sourceDate, "虚构组件样例", "", "虚构：新增目标") }
                    awaitWidgetText("虚构：新增目标")
                    runBlocking { repository.save(sourceDate, "虚构组件样例", "", "虚构：修改目标") }
                    awaitWidgetText("虚构：修改目标")
                    runBlocking { repository.save(todayDate, "虚构：今日所学", "", "") }
                    awaitWidgetText("1/3 项已填写 · 虚构：今日所学")
                    runBlocking { repository.save(todayDate, "", "虚构：今日疑问", "") }
                    awaitWidgetText("1/3 项已填写 · 虚构：今日疑问")
                    runBlocking { repository.save(todayDate, "虚构：今日所学", "虚构：今日疑问", "虚构：明天目标") }
                    awaitWidgetText("3/3 项已填写 · 虚构：今日所学")
                    awaitWidgetText("虚构：修改目标")
                    runBlocking { repository.save(todayDate, "", "", "") }
                    awaitWidgetText("已保存空记录")
                    runBlocking { repository.delete(todayDate) }
                    awaitWidgetText("今天还没填写")
                    runBlocking { repository.delete(sourceDate) }
                    awaitWidgetText("还没有交接目标")
                    runBlocking {
                        BackupManager(repository).apply(BackupEnvelope(1, Instant.now().toString(), listOf(
                            DailyRecord(sourceDate, "虚构导入样例", "", "虚构：导入目标", 100L, 200L),
                            DailyRecord(todayDate, "", "", "虚构：导入今日目标", 100L, 200L),
                        )))
                    }
                    awaitWidgetText("虚构：导入目标")
                    awaitWidgetText("1/3 项已填写 · 虚构：导入今日目标")
                    onActivity {
                        val view = requireNotNull(widgetView)
                        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                        try {
                            view.draw(Canvas(image))
                            File(requireNotNull(activity.getExternalFilesDir("widget-test")), "wide-today-saved.png")
                                .outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        } finally { image.recycle() }
                    }
                    runBlocking {
                        BackupManager(repository).apply(BackupEnvelope(1, Instant.now().toString(), listOf(
                            DailyRecord(todayDate, "虚构：覆盖导入今日", "", "", 100L, 300L),
                        )), overwrite = true)
                    }
                    awaitWidgetText("1/3 项已填写 · 虚构：覆盖导入今日")
                    awaitWidgetText("虚构：导入目标")
                    runBlocking { repository.delete(sourceDate); repository.delete(todayDate) }
                    awaitWidgetText("还没有交接目标")
                    awaitWidgetText("今天还没填写")
                }

                if (verifyNavigation) {
                    // Clicking the lower panel heading uses its parent action, not just the CTA.
                    onView(withText(todayLabel)).perform(click())
                    waitForComposeText("每天3分钟")
                    onActivity { assertEquals("today", it.intent.getStringExtra("destination")) }
                    onView(withText("填写今天")).perform(click())
                    waitForComposeText("每天3分钟")
                    onActivity { activity ->
                        assertEquals("today", activity.intent.getStringExtra("destination"))
                        activity.findViewById<ViewGroup>(android.R.id.content).removeView(overlay)
                    }
                    awaitComposeText("每天3分钟")
                    compose.onNodeWithText("今天真正学会了什么？").assertIsDisplayed()
                    onActivity { activity ->
                        activity.findViewById<ViewGroup>(android.R.id.content).addView(overlay)
                    }
                    onView(withText("历史记录")).perform(click())
                    waitForComposeText("搜索三个回答")
                    onActivity { activity ->
                        assertEquals("history", activity.intent.getStringExtra("destination"))
                        activity.findViewById<ViewGroup>(android.R.id.content).removeView(overlay)
                    }
                    awaitComposeText("搜索三个回答")
                    compose.onNodeWithText("搜索三个回答").assertIsDisplayed()
                }
            }
        } finally {
            ready?.let {
                IdlingRegistry.getInstance().unregister(it)
                InstrumentationRegistry.getInstrumentation().runOnMainSync { it.detach() }
            }
            host.stopListening()
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(widgetId)
            // This dedicated host ID belongs exclusively to this test fixture.
            host.deleteHost()
            // ActivityScenario's lifecycle observer becomes stale after real RemoteViews
            // PendingIntent launches on MagicOS. Check the actual Activity teardown instead.
            onActivity { it.finishAndRemoveTask() }
            compose.waitUntil(10_000) { activity.isDestroyed }
            runBlocking {
                if (goal != null || verifyMutations) repository.delete(sourceDate)
                if (olderGoal != null) repository.delete(olderDate)
                if (verifyMutations) repository.delete(todayDate)
            }
        }
    }

    private fun findText(node: View, expected: String): TextView? {
        if (node is TextView && node.text.toString() == expected) return node
        if (node is ViewGroup) for (index in 0 until node.childCount) {
            findText(node.getChildAt(index), expected)?.let { return it }
        }
        return null
    }

    private fun assertFullyVisible(text: TextView, host: AppWidgetHostView) {
        val bounds = Rect()
        val hostBounds = Rect()
        assertTrue(text.getGlobalVisibleRect(bounds))
        assertTrue(host.getGlobalVisibleRect(hostBounds))
        assertTrue("Widget action must remain inside the host", hostBounds.contains(bounds))
        assertEquals("Widget action height must not be clipped", text.height, bounds.height())
        assertEquals("Widget action width must not be clipped", text.width, bounds.width())
        assertEquals("Widget action text must fit on one line", 1, text.lineCount)
        assertEquals("Widget action text must not be ellipsized", 0, text.layout.getEllipsisCount(0))
    }

    private fun awaitComposeText(text: String) {
        waitForComposeText(text)
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun waitForComposeText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private class WidgetReadyIdler(private val view: AppWidgetHostView, private val expectedGoal: String) : IdlingResource,
        ViewTreeObserver.OnGlobalLayoutListener {
        @Volatile private var ready = false
        @Volatile private var callback: IdlingResource.ResourceCallback? = null

        init { view.viewTreeObserver.addOnGlobalLayoutListener(this) }
        override fun getName() = "DailyWidgetRemoteViewsReady"
        override fun isIdleNow() = ready
        override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) { this.callback = callback }

        override fun onGlobalLayout() {
            if (ready) return
            val texts = mutableSetOf<String>()
            fun collect(node: View) {
                if (node is TextView) texts += node.text.toString()
                if (node is ViewGroup) for (index in 0 until node.childCount) collect(node.getChildAt(index))
            }
            collect(view)
            ready = view.width > 0 && view.height > 0 &&
                texts.containsAll(listOf(expectedGoal, "今天还没填写", "填写今天", "历史记录"))
            if (ready) callback?.onTransitionToIdle()
        }

        fun detach() {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(this)
        }
    }

    private companion object {
        const val HOST_ID = 310331
        const val HEIGHT_DP = 150
    }
}
