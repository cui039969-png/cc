/*
 * Calendar month rows/day cells and navigation scaffold adapted from FrictionFreeDiary:
 * ui/screens/calendar/CalendarScreen.kt and ui/adaptive/AdaptiveMainScaffold.kt.
 * Copyright (c) 2026 FrictionFree Diary Contributors. MIT; see NOTICE.md.
 * Daily 3 Minutes changes Copyright 2026, Apache-2.0.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.daily3minutes.ui

import android.Manifest
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.daily3minutes.data.DailyRecord
import io.github.daily3minutes.BuildConfig
import io.github.daily3minutes.domain.RecordRules
import io.github.daily3minutes.domain.historyPreview
import io.github.daily3minutes.domain.HistoryRange
import io.github.daily3minutes.domain.historyDays
import io.github.daily3minutes.domain.HistoryDay
import io.github.daily3minutes.reminder.ReminderScheduler
import io.github.daily3minutes.widget.DailyWidgetReceiver
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

internal val questionLabels = listOf(
    "今天真正学会了什么？",
    "今天还有什么没弄懂？",
    "明天优先解决哪一件事？",
)

@Composable
fun DailyAppContent(model: DailyViewModel, onFinish: () -> Unit) {
    val state by model.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val app = context.applicationContext as io.github.daily3minutes.DailyApp
    val voiceEntries by app.voices.entries.collectAsState(initial = emptyList())
    val voiceCounts = voiceEntries.filter { it.date == state.editor.date }.groupingBy { it.question }.eachCount()
    val voiceOnlyRecords = voiceEntries.groupBy { it.date }.map { (date, notes) ->
        DailyRecord(date, "", "", "", notes.minOf { it.createdAt }, notes.maxOf { it.createdAt })
    }
    val historyState = state.copy(
        results = if (state.query.isBlank()) (state.results + voiceOnlyRecords.filter { audio -> state.results.none { it.date == audio.date } }).sortedByDescending { it.date } else state.results,
        monthRecords = (state.monthRecords + voiceOnlyRecords.filter { it.date.startsWith(state.month.toString()) }).distinctBy { it.date })
    val backupPrefs = remember(context) { context.getSharedPreferences("backup_status", 0) }
    val syncCoordinator = (context.applicationContext as io.github.daily3minutes.DailyApp).sync
    val syncAccount by syncCoordinator.account.collectAsState(initial = null)
    val syncLinks by syncCoordinator.links.collectAsState(initial = emptyList())
    val visibleSyncLinks = syncLinks.filter { it.accountId == syncAccount?.id }.associateBy { it.date }
    val resolver = context.contentResolver
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val navigationScope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) model.cancelBackup()
        else model.previewImport { resolver.openInputStream(uri) }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) model.cancelBackup()
        else model.exportBackup(onSuccess = { count -> backupPrefs.edit().putLong("last_export", System.currentTimeMillis()).putInt("last_count", count).putBoolean("last_audio", false).apply() }) { resolver.openOutputStream(uri, "wt") }
    }
    val archiveImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) model.cancelBackup() else model.previewImport(archive = app.voiceArchives) { resolver.openInputStream(uri) }
    }
    val archiveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) model.cancelBackup() else model.exportBackup(archive = app.voiceArchives,
            onSuccess = { count -> backupPrefs.edit().putLong("last_export", System.currentTimeMillis()).putInt("last_count", count).putBoolean("last_audio", true).apply() }) { resolver.openOutputStream(uri, "wt") }
    }

    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(t(it)); model.clearNotice() }
    }
    LaunchedEffect(state.screen) {
        if (state.screen != DailyScreen.HISTORY) drawer.close()
    }
    BackHandler(enabled = drawer.isClosed) { model.back(onFinish) }
    val enabled = !state.busy && state.backup == BackupState.Idle
    val editorVisible = state.screen == DailyScreen.TODAY || state.screen == DailyScreen.EDIT

    val destinations = listOf(
        Triple(DailyScreen.TODAY, t("今日"), Icons.Default.EditNote),
        Triple(DailyScreen.HISTORY, t("历史"), Icons.Default.CalendarMonth),
        Triple(DailyScreen.SETTINGS, t("设置"), Icons.Default.Settings),
    )
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = enabled && state.screen == DailyScreen.HISTORY,
        drawerContent = {
            ModalDrawerSheet(Modifier.widthIn(max = 320.dp).testTag("history-drawer")) {
              Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(t("历史范围"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { navigationScope.launch { drawer.close() } }) {
                        Icon(Icons.Default.Close, contentDescription = t("关闭历史菜单"))
                    }
                }
                Text(t("选择查看范围"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp, 8.dp))
                listOf(HistoryRange.ALL, HistoryRange.LAST_3_DAYS, HistoryRange.LAST_7_DAYS, HistoryRange.LAST_30_DAYS).forEach { range ->
                    NavigationDrawerItem(label = { Text(t(range.label)) }, selected = state.historyRange == range,
                        onClick = { model.setHistoryRange(range); navigationScope.launch { drawer.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("history-range-${range.name}"))
                }
                Text(t("默认近7天，包含今天。交接按写下目标的日期排列。"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp, 20.dp))
              }
            }
        },
    ) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp && maxWidth > maxHeight
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (state.screen) {
                    DailyScreen.TODAY -> t("每天3分钟")
                    DailyScreen.HISTORY -> t("历史记录")
                    DailyScreen.EDIT -> t("编辑记录")
                    DailyScreen.SETTINGS -> t("设置")
                }) },
                navigationIcon = {
                    if (state.screen == DailyScreen.HISTORY) IconButton(
                        onClick = { navigationScope.launch { drawer.open() } }, enabled = enabled,
                        modifier = Modifier.testTag("history-menu")) {
                        Icon(Icons.Default.Menu, contentDescription = t("历史菜单"))
                    }
                    if (state.focusedQuestion != null || state.screen == DailyScreen.EDIT) IconButton(onClick = { model.back(onFinish) }, enabled = enabled) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t(if (state.focusedQuestion != null) "返回当天概览" else "返回历史记录"))
                    }
                },
                actions = {
                    if (editorVisible) {
                        TextButton(onClick = model::save, enabled = enabled && state.editor.loaded) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("保存"))
                        }
                    }
                },
            )
        },
        bottomBar = {
          if (!useRail && state.focusedQuestion == null) {
            NavigationBar {
                destinations.forEach { (destination, label, icon) ->
                    val selected = state.screen == destination || (destination == DailyScreen.HISTORY && state.screen == DailyScreen.EDIT)
                    NavigationBarItem(selected = selected,
                        onClick = { model.navigate(destination) }, enabled = enabled,
                        icon = { Icon(icon, contentDescription = null) }, label = { Text(label) })
                }
            }
          }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
          if (useRail) NavigationRail(Modifier.testTag("daily-navigation-rail").verticalScroll(rememberScrollState()),
              windowInsets = WindowInsets(0, 0, 0, 0)) {
            destinations.forEach { (destination, label, icon) ->
                val selected = state.screen == destination || (destination == DailyScreen.HISTORY && state.screen == DailyScreen.EDIT)
                NavigationRailItem(selected = selected, enabled = enabled,
                    modifier = Modifier.height(if (LocalDensity.current.fontScale > 1.3f) 80.dp else 56.dp),
                    onClick = { model.navigate(destination) },
                    icon = { Icon(icon, contentDescription = null) }, label = { Text(label) })
            }
          }
        Column(Modifier.weight(1f).fillMaxSize().imePadding()) {
            if (state.busy || state.backup == BackupState.Reading || state.backup == BackupState.Applying)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            // Android's Compose animation clock observes animator_duration_scale, including 0.
            Crossfade(targetState = state.screen, animationSpec = tween(140), label = "page") { screen ->
              val screenEnabled = enabled && screen == state.screen
              when (screen) {
                DailyScreen.TODAY, DailyScreen.EDIT -> if (state.focusedQuestion != null) FocusedEditor(state, model, screenEnabled)
                else EditorScreen(state.copy(screen = screen),
                    onOpen = model::openQuestion, onDate = model::selectEditorDate, voiceCounts = voiceCounts,
                    onDelete = { confirmDelete = true },
                    onHandoff = { state.handoff?.let { model.navigate(DailyScreen.EDIT, it.date) } },
                    enabled = screenEnabled,
                )
                DailyScreen.HISTORY -> HistoryScreen(historyState,
                    onQuery = model::search, onMonth = model::changeMonth,
                    onCurrentMonth = model::currentMonth,
                    onDate = { model.navigate(DailyScreen.EDIT, it) }, enabled = screenEnabled,
                    syncLinks = visibleSyncLinks, voiceCounts = voiceEntries.groupingBy { it.date }.eachCount(),
                    onCompleted = { date, checked -> model.afterSaved { syncCoordinator.setCompleted(date, checked) } })
                DailyScreen.SETTINGS -> SettingsScreen(enabled = screenEnabled,
                    onArchiveExport = { app.voices.finish(); model.beginBackup(export = true) { archiveExport.launch("daily-3-minutes-complete-${java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.zip") } },
                    onArchiveImport = { model.beginBackup(export = false) { archiveImport.launch(arrayOf("application/zip", "application/octet-stream")) } },
                    onImport = { model.beginBackup(export = false) { importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } },
                    onExport = { model.beginBackup(export = true) { exportPicker.launch("daily-3-minutes-${java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.json") } },
                    model = model,
                    onMessage = model::message)
              }
            }
        }
        }
    }
    }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(t("删除此日记录？")) },
        text = { Text(t("将删除 %s 的三个回答和本机录音。此操作无法撤销，请确认已保留需要的备份。", state.editor.date)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; model.deleteCurrent() }) { Text(t("确认删除")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("取消")) } },
    )
    if (state.quickActions) AlertDialog(onDismissRequest = model::closeQuickActions,
        title = { Text(t("今天的快捷操作")) }, text = {
            Column {
                questionLabels.forEachIndexed { index, label ->
                    TextButton(onClick = { model.openQuestion(index) }, modifier = Modifier.fillMaxWidth()) { Text(t(label)) }
                }
                Text(t("进入独立编辑页后，可录音或使用语音转文字。"), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = model::closeQuickActions) { Text(t("关闭")) } })
    val preview = state.backup as? BackupState.Preview
    if (preview != null) ImportPreviewDialog(preview, onCancel = model::cancelBackup, onImport = model::applyImport)
}

@Composable
private fun EditorScreen(state: DailyUiState, onOpen: (Int) -> Unit, onDate: (String) -> Unit, onDelete: () -> Unit,
    onHandoff: () -> Unit, enabled: Boolean, voiceCounts: Map<Int, Int>) {
    val context = LocalContext.current
    val dateFormat = DateTimeFormatter.ofPattern(if (AppLocale.language(context) == "zh") "yyyy年M月d日 EEEE" else "EEEE, d MMMM yyyy", AppLocale.locale(context))
    val editor = state.editor
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("daily-editor"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorDateRail(editor.date, state.today, enabled, onDate)
                if (state.screen == DailyScreen.TODAY) Text(t("记录一点，给明天一个起点。"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (editor.status == SaveStatus.SAVED && editor.exists && editor.empty) t("已保存 · 空记录") else t(editor.status.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (editor.status == SaveStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                editor.error?.let { Text(t(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if (state.screen == DailyScreen.TODAY) item { HandoffCard(state.handoff, onHandoff, enabled) }
        if (!editor.loaded && editor.status == SaveStatus.LOADING) item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
        items(3) { index ->
            Card(onClick = { onOpen(index) }, enabled = enabled && editor.loaded,
                modifier = Modifier.fillMaxWidth().testTag("open-question-$index")) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t(questionLabels[index]), style = MaterialTheme.typography.titleMedium)
                    Text(editor.fields[index].ifBlank { t("点开独立编辑，写下一点就好") },
                        style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(t("独立编辑 · %s 字", RecordRules.codePointCount(editor.fields[index])),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    voiceCounts[index]?.let { count -> Text(t("录音（%s）", count), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        item {
            Text(t("停笔后约 0.8 秒自动保存，也可点右上角“保存”。空白的一天无需补写。"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (editor.exists || voiceCounts.isNotEmpty()) item {
            TextButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(t("删除此日记录"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun HandoffCard(record: DailyRecord?, onSource: () -> Unit, enabled: Boolean) {
    var expanded by rememberSaveable(record?.date, record?.nextPriority) { mutableStateOf(false) }
    var overflowing by remember(record?.date, record?.nextPriority) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("上次交接"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(when {
                record == null -> t("还没有交接目标")
                record.nextPriority.isBlank() -> t("上一条记录未设置明日目标")
                else -> record.nextPriority
            }, style = MaterialTheme.typography.bodyLarge, maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("handoff-goal"),
                onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow })
            if (overflowing || expanded) TextButton(onClick = { expanded = !expanded }, enabled = enabled,
                modifier = Modifier.testTag("handoff-expand")) { Text(if (expanded) t("收起目标") else t("展开目标")) }
            if (record != null) Text(t("来源：%s", record.date), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (record != null) TextButton(onClick = onSource, enabled = enabled,
                modifier = Modifier.testTag("handoff-source")) { Text(t("查看来源记录")) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryScreen(
    state: DailyUiState, onQuery: (String) -> Unit, onMonth: (Long) -> Unit,
    onCurrentMonth: () -> Unit, onDate: (String) -> Unit, enabled: Boolean,
    syncLinks: Map<String, io.github.daily3minutes.data.SyncLink>, voiceCounts: Map<String, Int>, onCompleted: (String, Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val calendarOffTop by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
    val days = remember(state.today, state.historyRange, state.results) {
        historyDays(state.today, state.historyRange, state.results)
    }
    LaunchedEffect(state.historyRange, state.query.isEmpty()) { listState.scrollToItem(0) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("daily-history"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(value = state.query, onValueChange = onQuery, enabled = enabled,
                label = { Text(t("搜索三个回答")) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("daily-search"),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = t("清空搜索"))
                    }
                })
        }
        if (state.query.isEmpty()) item(key = "calendar") {
            Box(Modifier.testTag("history-calendar")) {
                MonthCalendar(state.month, state.today, state.monthRecords.map { it.date }.toSet(),
                    onMonth, onCurrentMonth, onDate, enabled)
            }
        }
        stickyHeader(key = "history-section") {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.query.isEmpty()) TextButton(enabled = enabled,
                        onClick = { scope.launch { listState.animateScrollToItem(if (calendarOffTop) 0 else 2) } },
                        modifier = Modifier.fillMaxWidth().testTag("history-calendar-toggle")) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                            Text(if (calendarOffTop) t("展开日历") else t("收起日历"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(when {
                        state.query.isNotEmpty() -> t("搜索结果（%s）", state.results.size)
                        state.historyRange == HistoryRange.ALL -> t("全部记录")
                        else -> t("%s交接", t(state.historyRange.label))
                    }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("history-range-title"))
                    if (state.query.isEmpty() && state.historyRange != HistoryRange.ALL && days.isNotEmpty()) {
                        Text(t("%s 至 %s · 包含今天", days.last().date, days.first().date),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (state.results.isEmpty() && (state.query.isNotEmpty() || state.historyRange == HistoryRange.ALL)) item {
            Text(if (state.query.isEmpty()) t("还没有记录。点日历中的日期，写下你想留下的一点。") else t("没有找到相关记录，试试其他关键词。"),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
        }
        if (state.query.isNotEmpty() || state.historyRange == HistoryRange.ALL) {
          items(state.results, key = { "record-${it.date}" }) { record ->
            Card(onClick = { onDate(record.date) }, enabled = enabled,
                modifier = Modifier.testTag("handoff-day-${record.date}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(record.date, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    voiceCounts[record.date]?.let { Text(t("录音（%s）", it), style = MaterialTheme.typography.labelMedium) }
                    val preview = historyPreview(record, state.query)
                    if (preview.label.isNotEmpty()) Text(t(preview.label), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(buildAnnotatedString {
                        append(preview.text)
                        if (preview.matchStart >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold,
                            background = MaterialTheme.colorScheme.primaryContainer), preview.matchStart, preview.matchEnd)
                    },
                        style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
          }
        } else {
            items(days, key = { "handoff-${it.date}" }) { day ->
                HistoryHandoffCard(day, onDate, enabled, syncLinks[day.date], voiceCounts[day.date] ?: 0, onCompleted)
            }
        }
    }
}

@Composable
private fun HistoryHandoffCard(day: HistoryDay, onDate: (String) -> Unit, enabled: Boolean,
    link: io.github.daily3minutes.data.SyncLink?, voiceCount: Int, onCompleted: (String, Boolean) -> Unit) {
    Card(onClick = { onDate(day.date) }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("handoff-day-${day.date}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(day.date, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (voiceCount > 0) Text(t("录音（%s）", voiceCount), style = MaterialTheme.typography.labelMedium)
            day.targetDate?.let { Text(t("交接给 %s", it), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(when {
                day.record == null -> t("当天未记录")
                day.goal.isNullOrBlank() -> t("未设置明日目标")
                else -> day.goal.orEmpty()
            }, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (day.record == null) Text(t("点此查看或填写"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (link != null && link.taskId.isNotBlank() && link.state in setOf("SYNCED", "PENDING", "RETRY")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = link.completed, enabled = enabled,
                        modifier = Modifier.testTag("history-task-completed-${day.date}"),
                        onCheckedChange = { onCompleted(day.date, it) })
                    Text(if (link.completed) t("已完成") else t("待完成"), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth, today: String, dates: Set<String>,
    onMonth: (Long) -> Unit, onCurrentMonth: () -> Unit, onDate: (String) -> Unit, enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(-1) }, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("上个月"))
            }
            Text(month.format(DateTimeFormatter.ofPattern(if (AppLocale.language(LocalContext.current) == "zh") "yyyy年M月" else "MMMM yyyy", AppLocale.locale(LocalContext.current))), style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { onMonth(1) }, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = t("下个月"))
            }
            IconButton(onClick = onCurrentMonth, enabled = enabled) {
                Icon(Icons.Default.Today, contentDescription = t("回到本月"))
            }
        }
        val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
        // A horizontally scrollable minimum grid keeps seven columns legible at large fonts.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gridWidth = maxOf(maxWidth, 336.dp * fontScale)
            Box(Modifier.horizontalScroll(rememberScrollState())) {
              Column(Modifier.width(gridWidth)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf(t("一"), t("二"), t("三"), t("四"), t("五"), t("六"), t("日")).forEach { weekday ->
                        Text(weekday, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                    }
                }
                val leading = month.atDay(1).dayOfWeek.value - 1
                val rows = (leading + month.lengthOfMonth() + 6) / 7
                for (week in 0 until rows) Row(Modifier.fillMaxWidth()) {
                    for (column in 0..6) {
                        val day = week * 7 + column - leading + 1
                        if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f))
                        else {
                            val date = month.atDay(day).toString()
                            val isToday = date == today
                            val hasRecord = date in dates
                            val shape = RoundedCornerShape(12.dp)
                            val description = date + (if (isToday) t("，今天") else "") + (if (hasRecord) t("，有记录") else t("，未填写"))
                            Column(
                                modifier = Modifier.weight(1f).heightIn(min = (if (fontScale <= 1.3f) 40.dp else 48.dp) * fontScale)
                                    .clip(shape)
                                    .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .then(if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = t("编辑此日期")) { onDate(date) }
                                    .semantics(mergeDescendants = true) { contentDescription = description }
                                    .padding(horizontal = 2.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text("$day", style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                                Box(Modifier.size(4.dp).clip(CircleShape)
                                    .background(if (hasRecord) MaterialTheme.colorScheme.primary else Color.Transparent))
                            }
                        }
                    }
                }
              }
            }
        }
    }
}

@Composable
private fun SettingsScreen(enabled: Boolean, onImport: () -> Unit, onExport: () -> Unit,
    onArchiveExport: () -> Unit, onArchiveImport: () -> Unit, onMessage: (String) -> Unit, model: DailyViewModel) {
    val context = LocalContext.current
    val scheduler = remember(context) { ReminderScheduler(context.applicationContext) }
    var reminderEnabled by remember { mutableStateOf(scheduler.isEnabled()) }
    var hour by remember { mutableStateOf(scheduler.hour()) }
    var minute by remember { mutableStateOf(scheduler.minute()) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scheduler.setEnabled(granted)
        reminderEnabled = scheduler.isEnabled()
        if (!reminderEnabled) onMessage(t("提醒未开启；记录功能仍可正常使用。"))
    }
    val lifecycle = (context as? ComponentActivity)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    scheduler.setEnabled(false)
                reminderEnabled = scheduler.isEnabled()
                hour = scheduler.hour(); minute = scheduler.minute()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("daily-settings"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("备份与恢复"), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onArchiveExport, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("export-complete")) { Text(t("导出完整备份（文字和录音）")) }
                OutlinedButton(onClick = onArchiveImport, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("import-complete")) { Text(t("恢复完整备份（ZIP）")) }
                Text(t("完整ZIP包含三问文字及本机录音；JSON仅包含文字，不含录音。完整备份上限1000段录音、音频合计512MiB。"), style = MaterialTheme.typography.bodySmall)
                Text(t("JSON 文件包含三个回答的完整正文和日期，不包含 Google 授权、同步关联或任务完成状态。请选择可信位置妥善保管。"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onExport, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(10.dp)); Text(t("导出 JSON 备份"))
                }
                OutlinedButton(onClick = onImport, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(10.dp)); Text(t("导入 JSON 备份"))
                }
                Text(t("导入前会先检查文件并预览。默认跳过已有日期；覆盖冲突需要再次确认。"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val preferences = context.getSharedPreferences("backup_status", 0)
                val last = preferences.getLong("last_export", 0)
                Text(if (last == 0L) t("尚未导出备份。卸载应用或更换手机前，请先导出文件。") else
                    t("上次备份：%s · %s 条记录", java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), preferences.getInt("last_count", 0)),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("backup-last-success"))
                if (last != 0L) Text(t(if (preferences.getBoolean("last_audio", false)) "上次类型：完整ZIP（文字和录音）" else "上次类型：JSON（仅文字）"), style = MaterialTheme.typography.bodySmall)
                Text(t("换手机恢复：在旧手机导出文件，传到新手机后选择“导入 JSON 备份”。恢复后可重新连接 Google 并核对任务关联。"),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item { HorizontalDivider() }
        item { GoogleTasksSection(model, enabled) }
        item { HorizontalDivider() }
        item { LanguageSection(enabled) }
        item { HorizontalDivider() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(t("每日提醒"), style = MaterialTheme.typography.titleMedium)
                        Text(t("默认关闭，仅在主动开启时请求通知权限。"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = reminderEnabled, enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = t("开启每日提醒") },
                        onCheckedChange = { checked ->
                            if (!checked) { scheduler.setEnabled(false); reminderEnabled = false }
                            else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else {
                                scheduler.setEnabled(true); reminderEnabled = scheduler.isEnabled()
                                if (!reminderEnabled) onMessage(t("系统通知已关闭，提醒未开启；记录功能仍可正常使用。"))
                            }
                        })
                }
                OutlinedButton(enabled = enabled, onClick = {
                    TimePickerDialog(context, { _, selectedHour, selectedMinute ->
                        scheduler.setTime(selectedHour, selectedMinute)
                        hour = selectedHour; minute = selectedMinute
                    }, hour, minute, true).show()
                }) { Text(t("提醒时间  %02d:%02d").format(Locale.ROOT, hour, minute)) }
                Text(t("按本地时间提醒。系统省电策略可能使通知延后；通知正文不会展示你的记录。"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { HorizontalDivider() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("桌面小组件"), style = MaterialTheme.typography.titleMedium)
                Text(t("在桌面长按空白处，进入“小组件”并添加“每天3分钟”。支持紧凑和宽版布局，具体格数由桌面决定。"),
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(enabled = enabled, modifier = Modifier.fillMaxWidth(), onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    val requested = try {
                        manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(
                            ComponentName(context, DailyWidgetReceiver::class.java), null, null,
                        )
                    } catch (_: RuntimeException) { false }
                    onMessage(if (requested) t("请按系统提示完成添加；添加后可在桌面调整大小。")
                        else t("当前桌面不支持一键添加，请长按桌面空白处，进入“小组件”并添加“每天3分钟”。"))
                }) {
                    Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, contentDescription = null)
                    Spacer(Modifier.width(10.dp)); Text(t("添加到桌面"))
                }
                Text(t("组件会展示上次记录的目标，桌面可被旁人看到。跨日刷新可能受系统后台策略影响，打开应用后会补充刷新。"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text(t("每天3分钟") + "  ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("app-version"))
            Spacer(Modifier.height(8.dp))
            Text(if (BuildConfig.VERSION_NAME.contains('-')) t("测试版本") else t("正式版本"), style = MaterialTheme.typography.bodySmall)
            Text(t("版本与下载"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cui039969-png/cc/releases"))) }.onFailure { onMessage(t("无法打开浏览器，请稍后重试。")) } }) { Text(t("查看最新安装包")) }
            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cui039969-png/cc"))) }.onFailure { onMessage(t("无法打开浏览器，请稍后重试。")) } }) { Text(t("查看源码")) }
            Text(t("本地功能无需账号，无广告、无遥测。Google Tasks 仅在主动连接后同步。请主动导出需要保留的记录。"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(t("本次更新：可选 Google Tasks、双语界面、备份说明与公开维护。Google 接入状态请查看专门栏目。"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { showSources = !showSources }, enabled = enabled,
                modifier = Modifier.testTag("open-source-toggle")) {
                Text(if (showSources) t("收起开源来源与参考") else t("开源来源与参考"))
            }
        }
        if (showSources) {
            item {
                Text(t("原有代码保留 MIT 许可，新增代码使用 Apache-2.0。以下项目用于实际改造或对比参考；点击后由浏览器打开 GitHub。"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(openSourceProjects, key = { it.url }) { project ->
                Card(onClick = {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(project.url))) }
                    catch (_: ActivityNotFoundException) { onMessage(t("没有可用的浏览器，请安装浏览器后再打开 GitHub。")) }
                    catch (_: SecurityException) { onMessage(t("系统暂时无法打开浏览器，请稍后重试。")) }
                }, enabled = enabled) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(project.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(t(project.detail), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewDialog(preview: BackupState.Preview, onCancel: () -> Unit, onImport: (Boolean) -> Unit) {
    var overwrite by remember(preview) { mutableStateOf(false) }
    var confirmOverwrite by remember(preview) { mutableStateOf(false) }
    if (confirmOverwrite) {
        AlertDialog(onDismissRequest = { confirmOverwrite = false },
            title = { Text(t("确认覆盖冲突记录？")) },
            text = { Text(t("备份中的回答将替换 %s 个已有日期的全部内容。请确认当前记录已经备份。", preview.summary.conflicts.size)) },
            confirmButton = { TextButton(onClick = { confirmOverwrite = false; onImport(true) }) { Text(t("确认覆盖并导入")) } },
            dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text(t("返回预览")) } })
        return
    }
    AlertDialog(onDismissRequest = onCancel,
        title = { Text(t("导入预览")) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("新增 %s 条 · 冲突 %s 条 · 异常 0 条", preview.summary.added, preview.summary.conflicts.size))
                Text(t("文件已通过完整校验，确认后才会写入。"), style = MaterialTheme.typography.bodySmall)
                preview.archive?.let { archive -> Text(t("包含 %s 段录音。跳过冲突日期时也跳过其录音；覆盖时替换文字、按ID合并录音，保留本机已有音频。", archive.manifest.recordings.size), style = MaterialTheme.typography.bodySmall) }
                ImportChoice(t("跳过已有记录"), selected = !overwrite, onSelect = { overwrite = false })
                if (preview.summary.conflicts.isNotEmpty()) {
                    ImportChoice(t("覆盖冲突日期"), selected = overwrite, onSelect = { overwrite = true })
                    Text(t("冲突日期"), style = MaterialTheme.typography.labelLarge)
                    // Show every conflict date, without showing private record bodies.
                    Text(preview.summary.conflicts.joinToString("、"), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            if (overwrite && preview.summary.conflicts.isNotEmpty()) confirmOverwrite = true else onImport(false)
        }) { Text(t("确认导入")) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(t("取消")) } },
    )
}

@Composable
private fun ImportChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
