/*
 * Copyright 2023 The Android Open Source Project
 * Copyright 2026 Daily 3 Minutes contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified from android/platform-samples ToolBarAppWidget.kt, commit
 * 177c3b92c387894648d0a2d42d238e265b05bbc3: responsive journal content,
 * Room observation, local date refresh, explicit application destinations, and
 * separate past-handoff and today-writing panels.
 */
package io.github.daily3minutes.widget

import io.github.daily3minutes.ui.t

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.daily3minutes.DailyApp
import io.github.daily3minutes.data.DailyRecord
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Two supported layouts; the host chooses the one fitting its actual dp size. */
class DailyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(140.dp, 150.dp), DpSize(280.dp, 150.dp), DpSize(140.dp, 260.dp), DpSize(280.dp, 260.dp)))
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as DailyApp
        val repository = app.repository
        val initialDate = LocalDate.now().toString()
        val initial = try {
            WidgetSnapshot(repository.latestPast(initialDate), repository.get(initialDate), voiceCount = app.voices.db.voices().all().count { it.date == initialDate })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            WidgetSnapshot(failed = true)
        }
        provideContent {
            // The persisted token also invalidates an already-running Glance composition at midnight.
            val refreshToken = currentState<Preferences>()[REFRESH_TOKEN]
            val today = remember(refreshToken) { LocalDate.now() }
            val records = remember(today) {
                combine(
                    repository.observeLatestPast(today.toString()),
                    repository.observe(today.toString()),
                    app.voices.entries,
                ) { past, current, voices -> WidgetSnapshot(past, current, voiceCount = voices.count { it.date == today.toString() }) }
                    .catch { emit(WidgetSnapshot(failed = true)) }
            }
            val snapshot by records.collectAsState(initial = initial)
            GlanceTheme { Content(context, today, snapshot) }
        }
    }

    @Composable
    private fun Content(context: Context, today: LocalDate, snapshot: WidgetSnapshot) {
        val wide = LocalSize.current.width >= 280.dp
        val source = snapshot.past
        val sourceLabel = when {
            source == null -> t("昨日交接")
            source.date == today.minusDays(1).toString() -> t("昨天交接 · %s", source.date)
            else -> t("上次交接 · %s", source.date)
        }
        val goal = when {
            snapshot.failed -> t("暂时无法读取，请打开应用重试")
            source == null -> t("还没有交接目标")
            source.nextPriority.isBlank() -> t("上一条记录未设置明日目标")
            else -> source.nextPriority
        }
        val current = snapshot.today
        val summary = when {
            snapshot.failed -> t("请打开应用查看今天")
            (current == null || current.isEmpty) && snapshot.voiceCount > 0 -> t("录音（%s）", snapshot.voiceCount)
            current == null -> t("今天还没填写")
            current.isEmpty -> t("已保存空记录")
            else -> {
                val answers = listOf(current.learned, current.unclear, current.nextPriority)
                t("%s/3 项已填写 · %s", answers.count { it.isNotBlank() }, answers.first { it.isNotBlank() })
            }
        }
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .appWidgetBackground()
                .background(WIDGET_BACKGROUND)
                .cornerRadius(20.dp)
                .padding(8.dp),
        ) {
            // The upper panel always uses the nearest earlier record, even when its goal is empty.
            // Today's answers never replace yesterday's handoff.
            Column(
                modifier = GlanceModifier.fillMaxWidth().height(if (LocalSize.current.height >= 260.dp) 74.dp else 44.dp)
                    .cornerRadius(12.dp).background(WIDGET_PAST_PANEL).padding(5.dp),
            ) {
                Text(
                    text = sourceLabel,
                    style = TextStyle(color = WIDGET_SECONDARY, fontSize = 10.sp),
                    maxLines = 1,
                )
                Text(
                    text = goal,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    style = TextStyle(color = WIDGET_TEXT, fontSize = if (wide) 13.sp else 12.sp),
                    maxLines = if (wide) 2 else 1,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            // Collection rows scroll independently; each answer opens its own full editor.
            Column(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    .cornerRadius(12.dp).background(WIDGET_TODAY_PANEL).padding(5.dp),
            ) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Text(text = t("今天 · %s月%s日", today.monthValue, today.dayOfMonth),
                        modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(open(context, "today"))),
                        style = TextStyle(color = WIDGET_TEXT, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(text = t("更多"), modifier = GlanceModifier.clickable(actionStartActivity(open(context, "more"))).padding(horizontal = 2.dp),
                        style = TextStyle(color = WIDGET_BUTTON_TEXT, fontSize = 10.sp), maxLines = 1)
                }
                LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    if (current == null || current.isEmpty || snapshot.failed) item {
                        Text(summary, style = TextStyle(color = WIDGET_SECONDARY, fontSize = 11.sp))
                    } else {
                        val labels = listOf("学会了什么", "还有什么没弄懂", "明日优先")
                        listOf(current.learned, current.unclear, current.nextPriority).forEachIndexed { index, answer ->
                            if (answer.isNotBlank()) {
                                item {
                                    Text(t(labels[index]), style = TextStyle(color = WIDGET_SECONDARY, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                }
                                val points = answer.codePointCount(0, answer.length)
                                val visible = answer.substring(0, answer.offsetByCodePoints(0, minOf(points, 4000)))
                                // Bound each collection row without breaking a surrogate pair; no stored text is truncated.
                                var start = 0
                                while (start < visible.length) {
                                    val end = visible.offsetByCodePoints(start, minOf(400, visible.codePointCount(start, visible.length)))
                                    val part = visible.substring(start, end)
                                    item { Text(part, modifier = GlanceModifier.fillMaxWidth().clickable(
                                        actionStartActivity(WidgetNavigation.intent(context, "today", index))),
                                        style = TextStyle(color = WIDGET_TEXT, fontSize = 12.sp)) }
                                    start = end
                                }
                                if (points > 4000) item { Text(t("点开应用查看全文"), modifier = GlanceModifier.clickable(
                                    actionStartActivity(WidgetNavigation.intent(context, "today", index))),
                                    style = TextStyle(color = WIDGET_BUTTON_TEXT, fontSize = 11.sp)) }
                            }
                        }
                    }
                }
                Row(GlanceModifier.fillMaxWidth()) {
                    Destination(context, t("填写今天"), "today", GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(4.dp))
                    Destination(context, t("历史记录"), "history", GlanceModifier.defaultWeight())
                }
            }
        }
    }

    @Composable
    private fun Destination(context: Context, label: String, destination: String, modifier: GlanceModifier) {
        Text(
            text = label,
            modifier = modifier.cornerRadius(7.dp).background(WIDGET_BUTTON)
                .clickable(actionStartActivity(open(context, destination)))
                .padding(horizontal = 2.dp, vertical = 3.dp),
            style = TextStyle(color = WIDGET_BUTTON_TEXT, fontSize = 11.sp),
            maxLines = 1,
        )
    }

    private data class WidgetSnapshot(
        val past: DailyRecord? = null,
        val today: DailyRecord? = null,
        val failed: Boolean = false,
        val voiceCount: Int = 0,
    )

    companion object {
        private val WIDGET_BACKGROUND = ColorProvider(day = Color.White, night = Color(0xFF121820))
        private val WIDGET_PAST_PANEL = ColorProvider(day = Color(0xFFF1F4F8), night = Color(0xFF1C2530))
        private val WIDGET_TODAY_PANEL = ColorProvider(day = Color(0xFFEAF2FF), night = Color(0xFF172C49))
        private val WIDGET_TEXT = ColorProvider(day = Color(0xFF17202A), night = Color(0xFFF3F6FA))
        private val WIDGET_SECONDARY = ColorProvider(day = Color(0xFF526174), night = Color(0xFFB6C2D1))
        private val WIDGET_BUTTON = ColorProvider(day = Color(0xFFDCE8FF), night = Color(0xFF17345F))
        private val WIDGET_BUTTON_TEXT = ColorProvider(day = Color(0xFF164F9F), night = Color(0xFFD6E5FF))
        private val REFRESH_TOKEN = longPreferencesKey("refresh_token")
        private val refreshMutex = Mutex()
        private const val PERIODIC_WORK = "daily-widget-hourly"
        private const val IMMEDIATE_WORK = "daily-widget-system-refresh"

        /** Called after committed mutations; one update covers all widget instances. */
        suspend fun refresh(context: Context) = refreshMutex.withLock {
            val manager = GlanceAppWidgetManager(context)
            val token = System.nanoTime()
            manager.getGlanceIds(DailyWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { it[REFRESH_TOKEN] = token }
            }
            DailyWidget().updateAll(context)
        }

        fun scheduleRefresh(context: Context) {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, DailyWidgetReceiver::class.java))
            if (ids.isEmpty()) return
            val request = PeriodicWorkRequestBuilder<DailyWidgetWorker>(1, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun requestRefresh(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DailyWidgetWorker>().build(),
            )
        }

        fun cancelRefresh(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }

        /** The date is deliberately resolved by MainActivity when the action is clicked. */
        private fun open(context: Context, destination: String) = WidgetNavigation.intent(context, destination)
    }
}
