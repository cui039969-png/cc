// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import androidx.lifecycle.viewModelScope
import io.github.daily3minutes.backup.BackupEnvelope
import io.github.daily3minutes.backup.ImportPreview
import io.github.daily3minutes.data.DailyRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth

/** Real virtual-time concurrency tests. Record content is entirely synthetic. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorConcurrencyTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: MemoryStore
    private lateinit var backups: MemoryBackups
    private val models = mutableListOf<DailyViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = MemoryStore()
        backups = MemoryBackups(store)
    }

    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun model(zone: () -> ZoneId = { ZoneId.of("Asia/Shanghai") }): DailyViewModel =
        DailyViewModel(store, backups,
            Clock.fixed(Instant.parse("2026-10-24T15:59:00Z"), ZoneId.of("UTC")), zone, dispatcher)
            .also { models += it }

    @Test fun failedExportDoesNotRecordASuccessfulBackup() = runTest(dispatcher) {
        val model = model(); runCurrent()
        model.edit(0, "Synthetic learning"); model.beginBackup(true) {}
        advanceUntilIdle()
        var marked = false
        model.exportBackup(onSuccess = { marked = true }) { object : java.io.ByteArrayOutputStream() {
            override fun close() { throw java.io.IOException("Synthetic failed close") }
        } }
        advanceUntilIdle()
        assertFalse(marked)
        assertEquals(BackupState.Idle, model.state.value.backup)
        assertEquals("Synthetic learning", store.rows.value.single().learned)
    }

    @Test fun independentQuestionAndDateChangesFlushTheRightDay() = runTest(dispatcher) {
        val model = model(); runCurrent()
        val first = model.state.value.editor.date
        model.openQuestion(0); advanceUntilIdle()
        model.edit(0, "Synthetic first-day learning")
        model.selectEditorDate("2026-10-25"); advanceUntilIdle()
        assertEquals("Synthetic first-day learning", store.get(first)!!.learned)
        assertEquals(0, model.state.value.focusedQuestion)
        assertEquals("", model.state.value.editor.learned)
        model.openQuestion(2); advanceUntilIdle()
        model.edit(2, "Synthetic second-day priority")
        model.selectEditorDate(first); advanceUntilIdle()
        assertEquals("Synthetic second-day priority", store.get("2026-10-25")!!.nextPriority)
        assertEquals("Synthetic first-day learning", model.state.value.editor.learned)
        assertEquals("", model.state.value.editor.nextPriority)
    }

    @Test fun dictationAppendsAndRejectsAResultForAnotherQuestionOrDate() = runTest(dispatcher) {
        val model = model(); runCurrent()
        val date = model.state.value.editor.date
        model.openQuestion(0); advanceUntilIdle(); model.edit(0, "Typed answer")
        model.acceptDictation(date, 0, "Recognized words")
        assertEquals("Typed answer\nRecognized words", model.state.value.editor.learned)
        model.openQuestion(1); advanceUntilIdle()
        model.acceptDictation(date, 0, "Stale words")
        assertEquals("", model.state.value.editor.unclear)
        model.acceptDictation("2026-10-23", 1, "Wrong date")
        assertEquals("", model.state.value.editor.unclear)
    }

    @Test fun aFailedSaveKeepsTheSameIndependentPageAndDate() = runTest(dispatcher) {
        val model = model(); runCurrent()
        val date = model.state.value.editor.date
        model.openQuestion(2); advanceUntilIdle()
        model.edit(2, "Synthetic unsaved priority"); store.failNextSave = true
        model.selectEditorDate("2026-10-25"); advanceUntilIdle()
        assertEquals(date, model.state.value.editor.date)
        assertEquals(2, model.state.value.focusedQuestion)
        assertEquals("Synthetic unsaved priority", model.state.value.editor.nextPriority)
        assertEquals(SaveStatus.ERROR, model.state.value.editor.status)
        model.selectEditorDate("2026-10-25"); advanceUntilIdle()
        assertEquals("Synthetic unsaved priority", store.get(date)!!.nextPriority)
    }

    @Test fun externalSyncFreezesInputFlushesAndReloadsCommittedChanges() = runTest(dispatcher) {
        val model = model(); runCurrent()
        model.edit(0, "Keep local learning")
        val accepted = model.withSyncCommit {
            assertTrue(model.state.value.busy)
            model.edit(0, "This blocked edit must be ignored")
            val old = store.rows.value.single()
            store.rows.value = listOf(old.copy(nextPriority = "Cloud priority"))
            true
        }
        advanceUntilIdle()
        assertTrue(accepted)
        assertEquals("Keep local learning", model.state.value.editor.learned)
        assertEquals("Cloud priority", model.state.value.editor.nextPriority)
        assertFalse(model.state.value.busy)
    }

    @Test fun navigationAwaitsPendingDebounceAndDoesNotWriteTheNextDate() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.edit(0, "虚构：掌握单调性")
        advanceTimeBy(300)
        model.navigate(DailyScreen.EDIT, "2026-10-20")
        advanceUntilIdle()
        assertEquals("虚构：掌握单调性", store.rows.value.single().learned)
        assertEquals("2026-10-24", store.rows.value.single().date)
        assertEquals("2026-10-20", model.state.value.editor.date)
        assertFalse(model.state.value.editor.exists)
        assertEquals(1, store.commits.size)
    }

    @Test fun newKeystrokeCannotCancelInFlightCommitOrReplaceNewerInput() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        val firstCommit = CompletableDeferred<Unit>()
        store.blockNextSave = firstCommit
        model.edit(0, "虚构：第一版")
        advanceTimeBy(800); runCurrent()
        assertEquals(1, store.activeWrites)
        model.edit(0, "虚构：最终版本")
        advanceTimeBy(800); runCurrent()
        firstCommit.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("虚构：第一版", "虚构：最终版本"), store.commits.map { it.learned })
        assertEquals(1, store.maxConcurrentWrites)
        assertEquals("虚构：最终版本", model.state.value.editor.learned)
        assertEquals("虚构：最终版本", store.rows.value.single().learned)
        assertFalse(model.state.value.editor.dirty)
    }

    @Test fun importFlushesBeforePreviewAndInvalidatesPreImportInput() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.edit(0, "虚构：导入前尚未防抖保存")
        var pickerOpened = false
        model.beginBackup(export = false) { pickerOpened = true }
        advanceUntilIdle()
        assertTrue(pickerOpened)
        assertEquals("虚构：导入前尚未防抖保存", store.rows.value.single().learned)
        backups.envelope = BackupEnvelope(1, "2026-10-24T16:00:00Z", listOf(
            DailyRecord("2026-10-24", "虚构：导入后的内容", "", "", 1, 2),
        ))
        model.previewImport { ByteArrayInputStream(byteArrayOf()) }
        advanceUntilIdle()
        assertTrue(model.state.value.backup is BackupState.Preview)
        model.edit(0, "冻结期间不应写入")
        model.applyImport(overwrite = true)
        advanceUntilIdle()
        assertEquals("虚构：导入后的内容", model.state.value.editor.learned)
        assertEquals("虚构：导入后的内容", store.rows.value.single().learned)
        assertEquals(1, store.commits.size)
        assertEquals(BackupState.Idle, model.state.value.backup)
    }

    @Test fun failedSaveBlocksNavigationAndRetryKeepsInput() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        store.failNextSave = true
        model.edit(1, "虚构：尚未弄懂参数讨论")
        model.navigate(DailyScreen.HISTORY)
        advanceUntilIdle()
        assertEquals(DailyScreen.TODAY, model.state.value.screen)
        assertEquals(SaveStatus.ERROR, model.state.value.editor.status)
        assertTrue(model.state.value.editor.dirty)
        assertTrue(store.rows.value.isEmpty())
        model.navigate(DailyScreen.HISTORY)
        advanceUntilIdle()
        assertEquals(DailyScreen.HISTORY, model.state.value.screen)
        assertEquals("虚构：尚未弄懂参数讨论", store.rows.value.single().unclear)
    }

    @Test fun overLimitCodePointsRetainTextAndBlockPartialSave() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        val tooLong = "学".repeat(20_001)
        model.edit(0, "虚构：可接受的内容")
        model.edit(1, tooLong)
        model.navigate(DailyScreen.SETTINGS)
        advanceUntilIdle()
        assertTrue(store.rows.value.isEmpty())
        assertEquals(tooLong, model.state.value.editor.unclear)
        assertEquals(DailyScreen.TODAY, model.state.value.screen)
        assertEquals(SaveStatus.ERROR, model.state.value.editor.status)
    }

    @Test fun zoneChangeFlushesOldDateBeforeOpeningNewLocalToday() = runTest(dispatcher) {
        var zone = ZoneId.of("Asia/Shanghai")
        val model = model { zone }
        runCurrent()
        model.edit(2, "虚构：明天做两题")
        zone = ZoneId.of("Asia/Tokyo")
        model.refreshToday()
        advanceUntilIdle()
        assertEquals("2026-10-24", store.rows.value.single().date)
        assertEquals("2026-10-25", model.state.value.today)
        assertEquals("2026-10-25", model.state.value.editor.date)
        assertEquals("虚构：明天做两题", model.state.value.handoff?.nextPriority)
    }

    @Test fun backgroundFlushCommitsWithoutWaitingForDebounce() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.edit(0, "虚构：切到后台前的内容")
        advanceTimeBy(50)
        model.flushOnBackground()
        runCurrent()
        assertEquals("虚构：切到后台前的内容", store.rows.value.single().learned)
        assertFalse(model.state.value.editor.dirty)
        advanceUntilIdle()
        assertEquals(1, store.commits.size)
    }

    @Test fun delayedOldDatabaseReadCannotReplaceJustCommittedAnswers() = runTest(dispatcher) {
        val old = DailyRecord("2026-10-24", "虚构：旧版本", "", "", 1, 1)
        store.rows.value = listOf(old)
        val model = model()
        runCurrent()
        model.edit(0, "虚构：已保存的新版本")
        model.save()
        runCurrent()
        assertEquals("虚构：已保存的新版本", model.state.value.editor.learned)
        // A query completed before the commit but its observer notification arrived later.
        // The backing storage remains new; only this read notification contains old text.
        store.delayedReads.emit(old)
        runCurrent()
        assertEquals("虚构：已保存的新版本", model.state.value.editor.learned)
        model.edit(1, "虚构：另一项增补")
        model.save()
        advanceUntilIdle()
        assertEquals("虚构：已保存的新版本", store.rows.value.single().learned)
        assertEquals("虚构：另一项增补", store.rows.value.single().unclear)
    }

    @Test fun foregroundMonthRolloverFlushesOldDayAndFollowsCurrentMonth() = runTest(dispatcher) {
        val clock = MutableClock("2026-10-31T15:59:59Z")
        val model = DailyViewModel(store, backups, clock, { ZoneId.of("Asia/Shanghai") }, dispatcher).also { models += it }
        runCurrent()
        model.edit(2, "虚构：月末交接")
        clock.now = Instant.parse("2026-10-31T16:00:00Z")
        model.checkDayBoundary()
        advanceUntilIdle()
        assertEquals("2026-10-31", store.rows.value.single().date)
        assertEquals("2026-11-01", model.state.value.editor.date)
        assertEquals(YearMonth.of(2026, 11), model.state.value.month)
        assertEquals("虚构：月末交接", model.state.value.handoff?.nextPriority)
    }

    @Test fun rolloverDoesNotMoveAnExplicitHistoricalEditorOrBrowsedMonth() = runTest(dispatcher) {
        var zone = ZoneId.of("Asia/Shanghai")
        val model = model { zone }
        runCurrent()
        model.changeMonth(-1)
        model.navigate(DailyScreen.EDIT, "2026-09-02")
        advanceUntilIdle()
        model.edit(0, "虚构：过去日期")
        zone = ZoneId.of("Asia/Tokyo")
        model.checkDayBoundary()
        advanceUntilIdle()
        assertEquals("2026-10-25", model.state.value.today)
        assertEquals("2026-09-02", model.state.value.editor.date)
        assertEquals(YearMonth.of(2026, 9), model.state.value.month)
        assertEquals("2026-09-02", store.rows.value.single().date)
    }

    @Test fun dayCheckRecoversAfterDateEventWasDeferredByBackupPicker() = runTest(dispatcher) {
        var zone = ZoneId.of("Asia/Shanghai")
        val model = model { zone }
        runCurrent()
        model.beginBackup(export = false) {}
        advanceUntilIdle()
        zone = ZoneId.of("Asia/Tokyo")
        model.checkDayBoundary()
        assertEquals("2026-10-24", model.state.value.today)
        model.cancelBackup()
        model.checkDayBoundary()
        advanceUntilIdle()
        assertEquals("2026-10-25", model.state.value.editor.date)
        assertTrue(store.rows.value.isEmpty())
    }

    private class MutableClock(instant: String) : Clock() {
        var now: Instant = Instant.parse(instant)
        override fun instant() = now
        override fun getZone() = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }

    private class MemoryStore : EditorStore {
        val rows = MutableStateFlow<List<DailyRecord>>(emptyList())
        val delayedReads = MutableSharedFlow<DailyRecord?>(extraBufferCapacity = 1)
        val commits = mutableListOf<DailyRecord>()
        var blockNextSave: CompletableDeferred<Unit>? = null
        var failNextSave = false
        var activeWrites = 0
        var maxConcurrentWrites = 0

        override fun observe(date: String): Flow<DailyRecord?> = merge(
            rows.map { it.firstOrNull { row -> row.date == date } },
            delayedReads.filter { it == null || it.date == date },
        )
        override fun observeMonth(start: String, end: String) = rows.map { it.filter { row -> row.date in start..end } }
        override fun search(query: String) = rows.map { it.filter { row ->
            listOf(row.learned, row.unclear, row.nextPriority).any { text -> text.contains(query, ignoreCase = true) }
        }.sortedByDescending { row -> row.date } }
        override suspend fun get(date: String) = rows.value.firstOrNull { it.date == date }
        override suspend fun all() = rows.value
        override suspend fun latestPast(today: String) = rows.value.filter { it.date < today }.maxByOrNull { it.date }
        override suspend fun save(date: String, learned: String, unclear: String, nextPriority: String) {
            activeWrites++
            maxConcurrentWrites = maxOf(maxConcurrentWrites, activeWrites)
            try {
                val gate = blockNextSave
                blockNextSave = null
                gate?.await()
                if (failNextSave) { failNextSave = false; error("Synthetic write failure") }
                val previous = get(date)
                if (previous == null && listOf(learned, unclear, nextPriority).all { it.isBlank() }) return
                val record = DailyRecord(date, learned, unclear, nextPriority, previous?.createdAt ?: 1, 2)
                commits += record
                rows.value = rows.value.filterNot { it.date == date } + record
            } finally { activeWrites-- }
        }
        override suspend fun delete(date: String) { rows.value = rows.value.filterNot { it.date == date } }
    }

    private class MemoryBackups(private val store: MemoryStore) : EditorBackups {
        var envelope = BackupEnvelope(1, "2026-10-24T16:00:00Z", emptyList())
        override fun parse(input: InputStream) = envelope
        override fun export(records: List<DailyRecord>) = "{}"
        override suspend fun preview(envelope: BackupEnvelope): ImportPreview {
            val dates = store.rows.value.map { it.date }.toSet()
            return ImportPreview(envelope.records.count { it.date !in dates }, envelope.records.map { it.date }.filter { it in dates })
        }
        override suspend fun apply(envelope: BackupEnvelope, overwrite: Boolean): ImportPreview {
            val preview = preview(envelope)
            val merged = store.rows.value.associateBy { it.date }.toMutableMap()
            envelope.records.forEach { if (overwrite || it.date !in merged) merged[it.date] = it }
            store.rows.value = merged.values.toList()
            return preview
        }
    }
}
