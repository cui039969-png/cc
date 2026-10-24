// Copyright 2026 Daily 3 Minutes contributors. SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.daily3minutes.backup.BackupEnvelope
import io.github.daily3minutes.backup.BackupManager
import io.github.daily3minutes.backup.ImportPreview
import io.github.daily3minutes.data.DailyRecord
import io.github.daily3minutes.data.DailyRepository
import io.github.daily3minutes.domain.RecordRules
import io.github.daily3minutes.domain.millisUntilNextDay
import io.github.daily3minutes.domain.HistoryRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class DailyScreen { TODAY, HISTORY, EDIT, SETTINGS }
enum class SaveStatus(val label: String) {
    LOADING("正在载入"), SAVED("已保存"), EMPTY("尚未填写"), EDITING("编辑中"), SAVING("保存中"), ERROR("保存失败")
}

data class EditorState(
    val date: String,
    val learned: String = "",
    val unclear: String = "",
    val nextPriority: String = "",
    val exists: Boolean = false,
    val loaded: Boolean = false,
    val dirty: Boolean = false,
    val revision: Long = 0,
    val status: SaveStatus = SaveStatus.LOADING,
    val error: String? = null,
) {
    val fields: List<String> get() = listOf(learned, unclear, nextPriority)
    val empty: Boolean get() = fields.all { it.isBlank() }
    val tooLong: Boolean get() = fields.any { RecordRules.codePointCount(it) > RecordRules.MAX_FIELD_CODE_POINTS }
}

sealed interface BackupState {
    data object Idle : BackupState
    data object Picking : BackupState
    data object Reading : BackupState
    data object Exporting : BackupState
    data object Applying : BackupState
    data class Preview(val envelope: BackupEnvelope, val summary: ImportPreview,
        val archive: io.github.daily3minutes.backup.VoiceArchivePreview? = null) : BackupState
}

data class DailyUiState(
    val today: String,
    val editor: EditorState,
    val screen: DailyScreen = DailyScreen.TODAY,
    val month: YearMonth,
    val monthRecords: List<DailyRecord> = emptyList(),
    val query: String = "",
    val historyRange: HistoryRange = HistoryRange.LAST_7_DAYS,
    val results: List<DailyRecord> = emptyList(),
    val handoff: DailyRecord? = null,
    val busy: Boolean = false,
    val backup: BackupState = BackupState.Idle,
    val notice: String? = null,
    val focusedQuestion: Int? = null,
    val quickActions: Boolean = false,
)

/** Narrow ports keep write-race tests independent of an Android/Room runtime. */
internal interface EditorStore {
    fun observe(date: String): Flow<DailyRecord?>
    fun observeMonth(start: String, end: String): Flow<List<DailyRecord>>
    fun search(query: String): Flow<List<DailyRecord>>
    suspend fun get(date: String): DailyRecord?
    suspend fun all(): List<DailyRecord>
    suspend fun latestPast(today: String): DailyRecord?
    suspend fun save(date: String, learned: String, unclear: String, nextPriority: String)
    suspend fun delete(date: String)
}

internal interface EditorBackups {
    fun parse(input: InputStream): BackupEnvelope
    fun export(records: List<DailyRecord>): String
    suspend fun preview(envelope: BackupEnvelope): ImportPreview
    suspend fun apply(envelope: BackupEnvelope, overwrite: Boolean): ImportPreview
}

private class RoomEditorStore(private val source: DailyRepository) : EditorStore {
    override fun observe(date: String) = source.observe(date)
    override fun observeMonth(start: String, end: String) = source.observeMonth(start, end)
    override fun search(query: String) = source.search(query)
    override suspend fun get(date: String) = source.get(date)
    override suspend fun all() = source.all()
    override suspend fun latestPast(today: String) = source.latestPast(today)
    override suspend fun save(date: String, learned: String, unclear: String, nextPriority: String) = source.save(date, learned, unclear, nextPriority)
    override suspend fun delete(date: String) = source.delete(date)
}

private class LocalEditorBackups(private val source: BackupManager) : EditorBackups {
    override fun parse(input: InputStream) = source.parse(input)
    override fun export(records: List<DailyRecord>) = source.export(records)
    override suspend fun preview(envelope: BackupEnvelope) = source.preview(envelope)
    override suspend fun apply(envelope: BackupEnvelope, overwrite: Boolean) = source.apply(envelope, overwrite)
}

/**
 * All commits, route changes, and imports share one gate. A pending debounce never owns
 * a database write: cancelling the timer for a new keystroke cannot cancel an in-flight
 * commit. Revision checks prevent a commit or Room emission replacing newer input.
 */
class DailyViewModel internal constructor(
    private val repository: EditorStore,
    private val backupManager: EditorBackups,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    constructor(repository: DailyRepository,
        backupManager: BackupManager = BackupManager(repository),
        clock: Clock = Clock.systemUTC(),
        zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
    ) : this(RoomEditorStore(repository), LocalEditorBackups(backupManager), clock, zoneProvider)

    private fun todayKey() = LocalDate.now(clock.withZone(zoneProvider())).toString()
    private val initialDate = todayKey()
    private val mutable = MutableStateFlow(DailyUiState(
        today = initialDate, editor = EditorState(initialDate), month = YearMonth.parse(initialDate.take(7)),
    ))
    val state: StateFlow<DailyUiState> = mutable.asStateFlow()
    private val writeGate = Mutex()
    private var debounce: Job? = null
    private var editorObserver: Job? = null
    private var monthObserver: Job? = null
    private var searchObserver: Job? = null
    private var generation = 0L
    private var handoffRequest = 0L

    init {
        observeEditor(initialDate)
        observeMonth(mutable.value.month)
        search("")
        viewModelScope.launch { refreshHandoff() }
    }

    fun edit(index: Int, text: String) {
        val current = mutable.value
        if (current.busy || current.backup != BackupState.Idle || !current.editor.loaded) return
        mutable.update { ui ->
            val editor = ui.editor
            val next = when (index) {
                0 -> editor.copy(learned = text)
                1 -> editor.copy(unclear = text)
                2 -> editor.copy(nextPriority = text)
                else -> return
            }
            ui.copy(editor = next.copy(dirty = true, revision = editor.revision + 1,
                status = SaveStatus.EDITING, error = null))
        }
        debounce?.cancel()
        debounce = viewModelScope.launch {
            delay(800)
            // Separate job: subsequent edits only cancel a timer, never this commit.
            save()
        }
    }

    fun save() {
        if (mutable.value.backup != BackupState.Idle) return
        viewModelScope.launch { writeGate.withLock { persistLocked() } }
    }

    /** ON_STOP is best effort; explicit in-app navigation always awaits this gate. */
    fun flushOnBackground() {
        debounce?.cancel()
        save()
    }

    fun refreshToday() {
        val now = todayKey()
        if (now == mutable.value.today) {
            viewModelScope.launch { refreshHandoff() }
            return
        }
        if (mutable.value.busy || mutable.value.backup != BackupState.Idle) return
        transition {
            val currentToday = todayKey()
            val wasToday = mutable.value.screen == DailyScreen.TODAY
            updateToday(currentToday)
            if (wasToday) observeEditor(currentToday)
            refreshHandoff()
        }
    }

    fun nextDayCheckDelayMillis(): Long = millisUntilNextDay(clock, zoneProvider()).coerceAtMost(60_000)

    fun checkDayBoundary() { if (todayKey() != mutable.value.today) refreshToday() }

    private fun updateToday(date: String) {
        val old = mutable.value
        val month = YearMonth.parse(date.take(7))
        val followMonth = old.month == YearMonth.parse(old.today.take(7))
        mutable.update { it.copy(today = date, month = if (followMonth) month else it.month) }
        if (followMonth && old.month != month) observeMonth(month)
    }

    fun navigate(screen: DailyScreen, date: String? = null, question: Int? = null, quick: Boolean = false) {
        transition {
            val currentToday = todayKey()
            updateToday(currentToday)
            mutable.update { it.copy(screen = screen, focusedQuestion = question?.takeIf { it in 0..2 }, quickActions = quick) }
            when (screen) {
                DailyScreen.TODAY -> if (mutable.value.editor.date != currentToday) observeEditor(currentToday)
                DailyScreen.EDIT -> {
                    val selected = runCatching { RecordRules.validateDate(date ?: currentToday).toString() }
                        .getOrDefault(currentToday)
                    if (mutable.value.editor.date != selected) observeEditor(selected)
                }
                else -> Unit
            }
            refreshHandoff()
        }
    }

    fun externalDestination(destination: String?, date: String?, question: Int? = null) {
        if (date != null) navigate(DailyScreen.EDIT, date, question)
        else navigate(if (destination == "history") DailyScreen.HISTORY else DailyScreen.TODAY,
            question = question, quick = destination == "more")
    }

    fun openQuestion(index: Int) {
        require(index in 0..2)
        transition { mutable.update { it.copy(focusedQuestion = index, quickActions = false) } }
    }
    fun closeQuestion() { transition { mutable.update { it.copy(focusedQuestion = null) } } }
    fun closeQuickActions() { mutable.update { it.copy(quickActions = false) } }
    fun selectEditorDate(date: String) {
        RecordRules.validateDate(date)
        transition {
            updateToday(todayKey())
            mutable.update { it.copy(screen = if (date == it.today) DailyScreen.TODAY else DailyScreen.EDIT) }
            if (mutable.value.editor.date != date) observeEditor(date)
        }
    }
    /** Recognition never overwrites typing or a different date/question after returning. */
    fun acceptDictation(date: String, index: Int, text: String) {
        if (date != mutable.value.editor.date || index !in 0..2 || mutable.value.focusedQuestion != index) {
            message("日期或问题已改变，未填入语音文字，请重新输入。")
            return
        }
        val old = mutable.value.editor.fields[index]
        val combined = if (old.isBlank()) text else old + "\n" + text
        if (RecordRules.codePointCount(combined) > RecordRules.MAX_FIELD_CODE_POINTS) {
            message("语音文字超出字数上限，未覆盖原有回答。")
            return
        }
        edit(index, combined)
    }

    fun back(finish: () -> Unit) {
        if (mutable.value.quickActions) { closeQuickActions(); return }
        if (mutable.value.focusedQuestion != null) { closeQuestion(); return }
        when (mutable.value.screen) {
            DailyScreen.EDIT -> navigate(DailyScreen.HISTORY)
            DailyScreen.HISTORY, DailyScreen.SETTINGS -> navigate(DailyScreen.TODAY)
            DailyScreen.TODAY -> transition { finish() }
        }
    }

    private fun transition(action: suspend () -> Unit) {
        if (mutable.value.busy || mutable.value.backup != BackupState.Idle) return
        debounce?.cancel()
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                writeGate.withLock { if (persistLocked()) action() }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun persistLocked(): Boolean {
        while (true) {
            val snapshot = mutable.value.editor
            if (!snapshot.loaded || !snapshot.dirty) return true
            if (snapshot.tooLong) {
                mutable.update { it.copy(editor = it.editor.copy(status = SaveStatus.ERROR,
                    error = "每项最多 20000 字，请缩短超限内容后保存。"), notice = "请先处理超限内容，再离开或备份。") }
                return false
            }
            mutable.update { it.copy(editor = it.editor.copy(status = SaveStatus.SAVING, error = null)) }
            try {
                repository.save(snapshot.date, snapshot.learned, snapshot.unclear, snapshot.nextPriority)
                val stored = repository.get(snapshot.date)
                mutable.update { ui ->
                    if (ui.editor.date != snapshot.date) ui else {
                        val unchanged = ui.editor.revision == snapshot.revision
                        ui.copy(editor = ui.editor.copy(exists = stored != null,
                            dirty = !unchanged,
                            status = if (!unchanged) SaveStatus.EDITING else if (stored == null) SaveStatus.EMPTY else SaveStatus.SAVED,
                            error = null))
                    }
                }
                refreshHandoff()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(editor = it.editor.copy(status = SaveStatus.ERROR,
                    error = "未能保存，内容仍保留在页面中。请点“保存”重试。"), notice = "保存失败，尚未离开当前页面。") }
                return false
            }
        }
    }

    private fun observeEditor(date: String) {
        debounce?.cancel()
        editorObserver?.cancel()
        val epoch = ++generation
        mutable.update { it.copy(editor = EditorState(date)) }
        editorObserver = viewModelScope.launch {
            try {
                // This session owns its snapshot after initial load. Accepting a later queued
                // pre-commit Room read after dirty=false could replace the just-saved answers.
                // Import/delete/date changes explicitly start a fresh generation instead.
                val record = repository.observe(date).first()
                mutable.update { ui ->
                    if (epoch != generation || ui.editor.date != date || ui.editor.dirty) ui
                    else ui.copy(editor = EditorState(
                        date, record?.learned.orEmpty(), record?.unclear.orEmpty(), record?.nextPriority.orEmpty(),
                        exists = record != null, loaded = true,
                        status = if (record == null) SaveStatus.EMPTY else SaveStatus.SAVED,
                    ))
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (epoch == generation) mutable.update { it.copy(editor = it.editor.copy(
                    status = SaveStatus.ERROR, error = "无法载入记录，请重新打开此日期。"), notice = "载入记录失败。") }
            }
        }
    }

    fun deleteCurrent() {
        if (mutable.value.busy || mutable.value.backup != BackupState.Idle) return
        debounce?.cancel()
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                writeGate.withLock {
                    val date = mutable.value.editor.date
                    // Deletion is deliberate; it supersedes pending edits after confirmation.
                    repository.delete(date)
                    observeEditor(date)
                    refreshHandoff()
                    mutable.update { it.copy(notice = "已删除此日记录。") }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(notice = "删除失败，记录仍保留。") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun changeMonth(delta: Long) {
        val month = runCatching { mutable.value.month.plusMonths(delta) }.getOrNull() ?: return
        if (month.year !in 1..9999) return
        mutable.update { it.copy(month = month) }
        observeMonth(month)
    }

    fun currentMonth() {
        val month = YearMonth.from(LocalDate.parse(mutable.value.today))
        mutable.update { it.copy(month = month) }
        observeMonth(month)
    }

    private fun observeMonth(month: YearMonth) {
        monthObserver?.cancel()
        monthObserver = viewModelScope.launch {
            try {
                repository.observeMonth(month.atDay(1).toString(), month.atEndOfMonth().toString()).collectLatest { records ->
                    mutable.update { it.copy(monthRecords = records) }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message("无法载入日历，请重新切换月份。") }
        }
    }

    fun search(query: String) {
        mutable.update { it.copy(query = query) }
        searchObserver?.cancel()
        searchObserver = viewModelScope.launch {
            try {
                repository.search(query).collectLatest { records -> mutable.update { it.copy(results = records) } }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message("搜索失败，请重试。") }
        }
    }

    fun setHistoryRange(range: HistoryRange) {
        if (mutable.value.busy || mutable.value.backup != BackupState.Idle) return
        mutable.update { it.copy(historyRange = range) }
        if (mutable.value.query.isNotEmpty()) search("")
    }

    private suspend fun refreshHandoff() {
        val request = ++handoffRequest
        try {
            val today = mutable.value.today
            val latest = repository.latestPast(today)
            mutable.update { if (it.today == today && request == handoffRequest) it.copy(handoff = latest) else it }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { /* Editor writes must not fail because a summary could not load. */ }
    }

    /** Freeze editing before SAF opens; picker cancellation must call cancelBackup(). */
    fun beginBackup(export: Boolean, launchPicker: () -> Unit) {
        transition {
            mutable.update { it.copy(backup = if (export) BackupState.Exporting else BackupState.Picking) }
            try { launchPicker()
            } catch (_: Exception) {
                mutable.update { it.copy(backup = BackupState.Idle, notice = "无法打开系统文件选择器，请稍后重试。") }
            }
        }
    }

    fun previewImport(archive: io.github.daily3minutes.backup.VoiceArchiveManager? = null, open: () -> InputStream?) {
        if (mutable.value.backup != BackupState.Picking) return
        mutable.update { it.copy(backup = BackupState.Reading) }
        viewModelScope.launch {
            var media: io.github.daily3minutes.backup.VoiceArchivePreview? = null
            try {
                val envelope = withContext(ioDispatcher) {
                    val input = open() ?: error("无法打开文件")
                    input.use { if (archive == null) backupManager.parse(it) else archive.parse(it).also { parsed -> media = parsed }.envelope }
                }
                val preview = if (media == null) backupManager.preview(envelope) else archive!!.preview(media!!)
                mutable.update { it.copy(backup = BackupState.Preview(envelope, preview, media)) }
            } catch (cancelled: CancellationException) { media?.close(); throw cancelled
            } catch (failure: Exception) {
                media?.close()
                mutable.update { it.copy(backup = BackupState.Idle,
                    notice = if (failure is io.github.daily3minutes.backup.BackupValidationException)
                        "导入被拒绝：${failure.message}" else "无法读取备份文件，未更改任何记录。") }
                refreshToday()
            }
        }
    }

    fun applyImport(overwrite: Boolean) {
        val preview = mutable.value.backup as? BackupState.Preview ?: return
        mutable.update { it.copy(backup = BackupState.Applying) }
        debounce?.cancel()
        viewModelScope.launch {
            try {
                writeGate.withLock {
                    val result = if (preview.archive == null) backupManager.apply(preview.envelope, overwrite)
                        else withContext(ioDispatcher) { preview.archive.manager.apply(preview.archive, overwrite) }
                    val date = mutable.value.editor.date
                    // New generation: no pre-import observer or delayed writer may restore old data.
                    observeEditor(date)
                    refreshHandoff()
                    val changed = result.added + if (overwrite) result.conflicts.size else 0
                    mutable.update { it.copy(notice = "导入完成，写入 $changed 条，${if (overwrite) "覆盖" else "跳过"} ${result.conflicts.size} 条冲突记录。") }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message("导入失败，事务已回滚，未更改记录。")
            } finally { preview.archive?.close(); finishBackup() }
        }
    }

    fun exportBackup(onSuccess: (Int) -> Unit = {}, archive: io.github.daily3minutes.backup.VoiceArchiveManager? = null, open: () -> OutputStream?) {
        if (mutable.value.backup != BackupState.Exporting) return
        viewModelScope.launch {
            try {
                writeGate.withLock {
                    val records = repository.all()
                    val count = withContext(ioDispatcher) {
                        val output = open() ?: error("无法写入文件")
                        if (archive == null) { output.bufferedWriter(Charsets.UTF_8).use { it.write(backupManager.export(records)) }; records.size }
                        else output.use { archive.export(it) }
                    }
                    runCatching { onSuccess(count) }
                    message("已导出 $count 条记录。请妥善保存备份。")
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message("导出失败，目标文件可能不完整，请重新导出。")
            } finally { finishBackup() }
        }
    }

    private fun finishBackup() {
        (mutable.value.backup as? BackupState.Preview)?.archive?.close()
        mutable.update { it.copy(backup = BackupState.Idle) }
        refreshToday()
    }
    fun cancelBackup() { finishBackup() }
    fun message(text: String) { mutable.update { it.copy(notice = text) } }
    fun clearNotice() { mutable.update { it.copy(notice = null) } }

    /** A remote write freezes input and flushes it first, then the store rechecks its snapshot. */
    suspend fun withSyncCommit(action: suspend () -> Boolean): Boolean = withContext(Dispatchers.Main.immediate) {
        if (mutable.value.busy || mutable.value.backup != BackupState.Idle) return@withContext false
        debounce?.cancel()
        mutable.update { it.copy(busy = true) }
        try {
            writeGate.withLock {
                if (!persistLocked()) return@withLock false
                val accepted = action()
                if (accepted) { observeEditor(mutable.value.editor.date); refreshHandoff() }
                accepted
            }
        } finally { mutable.update { it.copy(busy = false) } }
    }

    fun afterSaved(action: suspend () -> Unit) { transition {
        action()
        observeEditor(mutable.value.editor.date)
        refreshHandoff()
    } }

    class Factory(private val repository: DailyRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DailyViewModel(repository) as T
    }
}
