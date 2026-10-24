// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.daily3minutes.DailyApp
import io.github.daily3minutes.audio.VoiceNote
import kotlinx.coroutines.launch

@Composable internal fun VoiceControls(state: DailyUiState, model: DailyViewModel, enabled: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as DailyApp
    val library = app.voices
    val all by library.entries.collectAsState(initial = emptyList())
    val session by library.session.collectAsState()
    val message by library.message.collectAsState()
    val index = state.focusedQuestion ?: return
    val date = state.editor.date
    val notes = all.filter { it.date == date && it.question == index }
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable(date, index) { mutableStateOf(false) }
    var delete by remember { mutableStateOf<VoiceNote?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf("") }
    var recognitionDate by rememberSaveable { mutableStateOf("") }
    var recognitionQuestion by rememberSaveable { mutableIntStateOf(0) }
    var permissionDate by rememberSaveable { mutableStateOf("") }
    var permissionQuestion by rememberSaveable { mutableIntStateOf(0) }
    var transcript by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionForDictation by rememberSaveable { mutableStateOf(false) }
    var dictating by remember { mutableStateOf(false) }
    var direct by remember { mutableStateOf<io.github.daily3minutes.audio.DictationSession?>(null) }
    fun stopPlaying() { player?.release(); player = null; playing = "" }
    fun cancelDictation() { direct?.cancel(); direct = null; dictating = false }
    fun startDirectDictation() {
        stopPlaying(); dictating = true
        try {
            direct = app.dictation.start(if (AppLocale.language() == "zh") "zh-CN" else "en-US",
                result = { text -> dictating = false; direct = null; transcript = text },
                error = { dictating = false; direct = null; model.message("没有识别到文字，请重试或使用键盘输入。") })
        } catch (_: Exception) { cancelDictation(); model.message("此设备暂不支持语音识别，可使用键盘上的话筒输入。") }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(date, index) { onDispose { library.finish(); stopPlaying(); cancelDictation() } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { library.setForeground(false); stopPlaying(); cancelDictation() }
            if (event == Lifecycle.Event.ON_RESUME) library.setForeground(true)
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) library.setForeground(true)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(message) { message?.let { model.message(it); library.clearMessage() } }
    LaunchedEffect(dictating) {
        if (dictating) { kotlinx.coroutines.delay(30_000); cancelDictation(); model.message("没有识别到文字，请重试或使用键盘输入。") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed && model.state.value.editor.date == permissionDate && model.state.value.focusedQuestion == permissionQuestion) {
            if (permissionForDictation) startDirectDictation() else library.start(permissionDate, permissionQuestion)
        }
        else if (!allowed) model.message("未获得麦克风权限，仍可正常打字。")
    }
    val recognition = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            transcript = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }
            if (transcript == null) model.message("没有识别到文字，请重试或使用键盘输入。")
        }
    }
    Column(Modifier.fillMaxWidth().testTag("voice-controls")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = enabled && !dictating, onClick = {
                stopPlaying()
                if (session != null) library.finish()
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) library.start(date, index)
                else { permissionDate = date; permissionQuestion = index; permissionForDictation = false; permission.launch(Manifest.permission.RECORD_AUDIO) }
            }, modifier = Modifier.testTag("voice-record")) { Text(t(if (session != null) "结束并保存录音" else "开始录音")) }
            TextButton(enabled = enabled && session == null && !dictating, onClick = {
                recognitionDate = date; recognitionQuestion = index
                try {
                    if (app.useDictationService) throw android.content.ActivityNotFoundException()
                    recognition.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (AppLocale.language() == "zh") "zh-CN" else "en-US")
                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, t(questionLabels[index])))
                } catch (_: android.content.ActivityNotFoundException) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startDirectDictation()
                    else { permissionDate = date; permissionQuestion = index; permissionForDictation = true; permission.launch(Manifest.permission.RECORD_AUDIO) }
                } catch (_: Exception) { model.message("此设备暂不支持语音识别，可使用键盘上的话筒输入。") }
            }, modifier = Modifier.testTag("voice-dictate")) { Text(t("语音转文字")) }
            TextButton(enabled = notes.isNotEmpty(), onClick = { expanded = !expanded }, modifier = Modifier.testTag("voice-list-toggle")) {
                Text(t("录音（%s）", notes.size))
            }
        }
        if (session != null) Text(t("正在录音；离开应用会结束并保存。单段最多10分钟。"), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("voice-active"))
        else Text(t("录音仅存本机；语音转文字使用系统服务，可能联网。"), style = MaterialTheme.typography.labelSmall)
        if (expanded) Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
            notes.forEach { note ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(t("录音 · %s 秒", (note.durationMillis + 999) / 1000), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(enabled = session == null, onClick = {
                        if (playing == note.id) stopPlaying()
                        else try {
                            stopPlaying()
                            val next = MediaPlayer()
                            player = next
                            next.setDataSource(library.file(note).absolutePath); next.prepare()
                            next.setOnCompletionListener { if (player === next) stopPlaying() }
                            next.setOnErrorListener { _, _, _ -> if (player === next) { stopPlaying(); model.message("录音无法播放，请核对文件或恢复备份。") }; true }
                            playing = note.id; next.start()
                        } catch (_: Exception) { stopPlaying(); model.message("录音无法播放，请核对文件或恢复备份。") }
                    }) { Text(t(if (playing == note.id) "停止播放" else "播放")) }
                    TextButton(enabled = enabled && session == null, onClick = { delete = note }) { Text(t("删除录音")) }
                }
            }
        }
    }
    if (dictating) AlertDialog(onDismissRequest = { cancelDictation() },
        title = { Text(t("正在听写")) }, text = { Text(t("请说出要追加的文字。系统服务可能联网，最长等待30秒。")) },
        confirmButton = { TextButton(onClick = { direct?.stop() }) { Text(t("结束识别")) } },
        dismissButton = { TextButton(onClick = { cancelDictation() }) { Text(t("取消")) } })
    delete?.let { note -> AlertDialog(onDismissRequest = { delete = null }, title = { Text(t("删除这段录音？")) },
        text = { Text(t("只删除这段本机录音，文字回答保留。")) },
        confirmButton = { TextButton(onClick = { delete = null; stopPlaying(); scope.launch {
            runCatching { library.delete(note) }.onFailure { model.message("删除录音失败，请重试。") }
        } }) { Text(t("确认删除")) } }, dismissButton = { TextButton(onClick = { delete = null }) { Text(t("取消")) } }) }
    transcript?.let { text -> AlertDialog(onDismissRequest = { transcript = null }, title = { Text(t("确认识别文字")) },
        text = { Text(text, modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { transcript = null; model.acceptDictation(recognitionDate, recognitionQuestion, text) }) { Text(t("追加到回答")) } },
        dismissButton = { TextButton(onClick = { transcript = null }) { Text(t("取消")) } }) }
}
