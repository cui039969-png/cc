// Copyright 2026 Daily 3 Minutes contributors. SPDX-License-Identifier: Apache-2.0
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.daily3minutes.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.daily3minutes.domain.RecordRules
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.Instant

@Composable
internal fun EditorDateRail(date: String, today: String, enabled: Boolean, onDate: (String) -> Unit) {
    val selected = LocalDate.parse(date)
    val month = YearMonth.from(selected)
    val list = rememberLazyListState()
    var picker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(date) { list.animateScrollToItem((selected.dayOfMonth - 3).coerceAtLeast(0)) }
    Column(Modifier.fillMaxWidth().testTag("editor-date-rail")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(enabled = enabled && month > YearMonth.of(1, 1), onClick = {
                val previous = month.minusMonths(1)
                onDate(previous.atDay(minOf(selected.dayOfMonth, previous.lengthOfMonth())).toString())
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("上个月")) }
            Text(if (AppLocale.language() == "zh") "${selected.year}年${selected.monthValue}月" else
                selected.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH)),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (date != today) TextButton(enabled = enabled, onClick = { onDate(today) }) { Text(t("今天")) }
            IconButton(enabled = enabled, onClick = { picker = true }, modifier = Modifier.testTag("editor-date-picker")) {
                Icon(Icons.Default.CalendarMonth, t("选择日期"))
            }
            IconButton(enabled = enabled && month < YearMonth.of(9999, 12), onClick = {
                val next = month.plusMonths(1)
                onDate(next.atDay(minOf(selected.dayOfMonth, next.lengthOfMonth())).toString())
            }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, t("下个月")) }
        }
        LazyRow(state = list, modifier = Modifier.fillMaxWidth().testTag("editor-date-days"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items((1..month.lengthOfMonth()).toList(), key = { it }) { day ->
                val key = month.atDay(day).toString()
                FilterChip(selected = key == date, enabled = enabled, onClick = { onDate(key) },
                    modifier = Modifier.testTag("editor-day-$key"), label = {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Text(day.toString(), style = MaterialTheme.typography.titleMedium)
                            Text(if (key == today) t("今天") else month.atDay(day).format(
                                java.time.format.DateTimeFormatter.ofPattern("EEE", AppLocale.localeFromLanguage())),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    })
            }
        }
    }
    if (picker) {
        val selection = rememberDatePickerState(initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1..9999)
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = {
            TextButton(enabled = selection.selectedDateMillis != null, onClick = {
                selection.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                picker = false
            }) { Text(t("确定")) }
        }, dismissButton = { TextButton(onClick = { picker = false }) { Text(t("取消")) } }) { DatePicker(selection) }
    }
}

private fun AppLocale.localeFromLanguage() = if (language() == "zh") java.util.Locale.SIMPLIFIED_CHINESE else java.util.Locale.ENGLISH

@Composable
internal fun FocusedEditor(state: DailyUiState, model: DailyViewModel, enabled: Boolean) {
    val index = state.focusedQuestion ?: return
    val editor = state.editor
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxHeight < 500.dp * LocalDensity.current.fontScale
    val page = if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(Modifier.fillMaxSize().then(page).padding(horizontal = 16.dp).testTag("focused-editor")) {
        EditorDateRail(editor.date, state.today, enabled, model::selectEditorDate)
        ScrollableTabRow(selectedTabIndex = index, edgePadding = 0.dp) {
            listOf("学会了什么", "还有什么没弄懂", "明日优先").forEachIndexed { question, label ->
                Tab(selected = index == question, enabled = enabled, onClick = { model.openQuestion(question) },
                    modifier = Modifier.testTag("question-tab-$question"), text = { Text(t(label)) })
            }
        }
        Text(t(questionLabels[index]), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 10.dp))
        val count = RecordRules.codePointCount(editor.fields[index])
        Text(t(editor.status.label), style = MaterialTheme.typography.labelMedium,
            color = if (editor.status == SaveStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        editor.error?.let { Text(t(it), color = MaterialTheme.colorScheme.error) }
        key(editor.date, index) {
            OutlinedTextField(value = editor.fields[index], onValueChange = { model.edit(index, it) },
                enabled = enabled && editor.loaded, modifier = Modifier.fillMaxWidth()
                    .then(if (compact) Modifier.height(260.dp) else Modifier.weight(1f))
                    .testTag("answer-$index").semantics { contentDescription = t(questionLabels[index]) },
                textStyle = MaterialTheme.typography.bodyLarge, placeholder = { Text(t("写下一点就好")) },
                isError = count > RecordRules.MAX_FIELD_CODE_POINTS,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                supportingText = { Text(t("%s / 20000 字", count)) })
        }
        VoiceControls(state, model, enabled)
        Spacer(Modifier.height(8.dp))
    }
    }
}
