// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.sync

import io.github.daily3minutes.data.*
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SyncEngineTest {
    private val date = "2026-10-24"
    private class MemoryStore : SyncStore {
        var account: SyncAccount? = SyncAccount("synthetic", "test@example.invalid", sinceDate = "2026-10-18")
        var title: String? = "Review one chapter"
        var link: SyncLink? = null
        var operation: SyncOperation? = SyncOperation("synthetic", "2026-10-24")
        var status = "READY"
        override suspend fun active() = account
        override suspend fun rememberList(accountId: String, listId: String) { account = account?.copy(listId = listId) }
        override suspend fun dates(account: SyncAccount) = listOf("2026-10-24")
        override suspend fun snapshot(accountId: String, date: String) = SyncSnapshot(date, title, link, operation)
        override suspend fun commit(accountId: String, before: SyncSnapshot, task: CloudTask, value: TaskValue): Boolean {
            if (account?.id != accountId || snapshot(accountId, before.date) != before) return false
            title = value.title
            link = SyncLink(accountId, before.date, task.id, value.title, value.completed, value.completed, state = "SYNCED")
            operation = null
            return true
        }
        override suspend fun mark(accountId: String, before: SyncSnapshot, state: String, remote: CloudTask?) {
            if (snapshot(accountId, before.date) != before) return
            link = (link ?: SyncLink(accountId, before.date, taskId = remote?.id.orEmpty())).copy(state = state,
                remoteTitle = remote?.value?.title.orEmpty(), remoteCompleted = remote?.value?.completed ?: false)
        }
        override suspend fun status(accountId: String, status: String, success: Boolean) { this.status = status }
    }
    private class MemoryRemote : TasksRemote {
        val tasks = mutableListOf<CloudTask>()
        var creates = 0; var updates = 0; var deletes = 0
        var lostCreate = false
        var failList: Exception? = null
        var onList: (() -> Unit)? = null
        var beforeUpdate: (() -> Unit)? = null
        var conflictUpdate = false
        override suspend fun ensureList(existingId: String?) = "app-list"
        override suspend fun list(listId: String): List<CloudTask> { failList?.let { throw it }; onList?.invoke(); return tasks.toList() }
        override suspend fun create(listId: String, date: String, value: TaskValue): CloudTask {
            creates++
            val task = CloudTask("task-$creates", value, date, TaskSyncRules.due(date).take(10))
            tasks += task
            if (lostCreate) { lostCreate = false; throw IOException("Synthetic lost response") }
            return task
        }
        override suspend fun update(listId: String, task: CloudTask, value: TaskValue): CloudTask {
            beforeUpdate?.invoke()
            if (conflictUpdate) throw TasksHttpException(412)
            updates++
            val changed = task.copy(value = value)
            tasks[tasks.indexOfFirst { it.id == task.id }] = changed
            return changed
        }
        override suspend fun delete(listId: String, taskId: String) { deletes++; tasks.removeAll { it.id == taskId } }
    }
    private fun paired(store: MemoryStore, remote: MemoryRemote) {
        store.link = SyncLink("synthetic", date, "existing", "Review one chapter", state = "SYNCED")
        remote.tasks += CloudTask("existing", TaskValue("Review one chapter"), date)
    }

    @Test fun createsOnlyOneTaskAndSubsequentSyncIsIdempotent() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); val engine = SyncEngine(s, r)
        assertTrue(engine.run()); assertTrue(engine.run())
        assertEquals(1, r.creates); assertEquals(0, r.updates); assertNull(s.operation)
        assertEquals("2026-10-25", r.tasks.single().due)
    }
    @Test fun lostPostResponseRecoversByDateMarkerWithoutDuplicate() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); r.lostCreate = true
        assertFalse(SyncEngine(s, r).run()); assertNotNull(s.operation)
        assertTrue(SyncEngine(s, r).run()); assertEquals(1, r.creates); assertEquals("task-1", s.link?.taskId)
    }
    @Test fun remoteTitleUpdatesOnlyThePriorityValue() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); paired(s, r)
        r.tasks[0] = r.tasks[0].copy(value = TaskValue("Cloud revision"))
        assertTrue(SyncEngine(s, r).run()); assertEquals("Cloud revision", s.title); assertEquals(0, r.updates)
    }
    @Test fun localEditUpdatesTheLinkedTask() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); paired(s, r); s.title = "Local revision"
        assertTrue(SyncEngine(s, r).run()); assertEquals("Local revision", r.tasks.single().value.title)
    }
    @Test fun simultaneousTitleEditsPreserveBothAndDoNotRetryForever() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); paired(s, r); s.title = "Local revision"
        r.tasks[0] = r.tasks[0].copy(value = TaskValue("Cloud revision"))
        assertTrue(SyncEngine(s, r).run()); assertEquals("CONFLICT", s.link?.state)
        assertEquals("Local revision", s.title); assertEquals("Cloud revision", s.link?.remoteTitle); assertEquals(0, r.updates)
    }
    @Test fun independentCompletionAndWordingChangesMerge() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); paired(s, r); s.title = "Local revision"
        r.tasks[0] = r.tasks[0].copy(value = TaskValue("Review one chapter", true))
        assertTrue(SyncEngine(s, r).run()); assertEquals(TaskValue("Local revision", true), r.tasks.single().value)
        assertTrue(s.link!!.completed)
    }
    @Test fun localCompletionAndRemoteWordingMerge() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); paired(s, r); s.link = s.link!!.copy(completed = true)
        r.tasks[0] = r.tasks[0].copy(value = TaskValue("Cloud revision"))
        assertTrue(SyncEngine(s, r).run()); assertEquals(TaskValue("Cloud revision", true), r.tasks.single().value)
    }
    @Test fun cloudDeletionPreservesLocalAnswerAndDoesNotRecreate() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); paired(s, r); r.tasks.clear()
        SyncEngine(s, r).run(); SyncEngine(s, r).run()
        assertEquals("REMOTE_DELETED", s.link?.state); assertEquals("Review one chapter", s.title); assertEquals(0, r.creates)
    }
    @Test fun deletedLocalRecordDetachesWithoutDeletingCloud() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); paired(s, r); s.title = null
        SyncEngine(s, r).run(); assertEquals("DETACHED", s.link?.state); assertEquals(0, r.deletes); assertEquals(1, r.tasks.size)
    }
    @Test fun clearingPriorityDetachesWithoutSendingEmptyTitle() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); paired(s, r); s.title = ""
        SyncEngine(s, r).run(); assertEquals("DETACHED", s.link?.state); assertEquals(0, r.updates)
    }
    @Test fun longUnicodeTitleIsRetainedAndNeverTruncated() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); s.title = "😀".repeat(513)
        SyncEngine(s, r).run(); assertEquals("TOO_LONG", s.link?.state); assertEquals(0, r.creates); assertEquals(1026, s.title!!.length)
    }
    @Test fun duplicateCloudMarkersRequireAttentionRatherThanChoosingOne() = runTest {
        val s = MemoryStore(); val r = MemoryRemote()
        r.tasks += listOf(CloudTask("a", TaskValue("A"), date), CloudTask("b", TaskValue("B"), date))
        SyncEngine(s, r).run(); assertEquals("DUPLICATE", s.link?.state); assertEquals(0, r.updates); assertEquals(0, r.creates)
    }
    @Test fun revokedAuthorizationRequiresForegroundAction() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); r.failList = TasksAuthRequired()
        assertTrue(SyncEngine(s, r).run()); assertEquals("AUTH_REQUIRED", s.status); assertNotNull(s.operation)
    }
    @Test fun networkLossLeavesDurableOperationForRetry() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); r.failList = IOException("Synthetic offline")
        assertFalse(SyncEngine(s, r).run()); assertEquals("OFFLINE", s.status); assertNotNull(s.operation)
    }
    @Test fun disconnectDuringFetchStopsTaskMutations() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); r.onList = { s.account = null }
        SyncEngine(s, r).run(); assertEquals(0, r.creates)
    }
    @Test fun staleResponseCannotReplaceNewerLocalInput() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); paired(s, r); s.title = "First local edit"
        r.beforeUpdate = { s.title = "Newer local edit"; s.operation = s.operation!!.copy(revision = 2) }
        assertFalse(SyncEngine(s, r).run()); assertEquals("Newer local edit", s.title); assertNotNull(s.operation)
    }
    @Test fun conditionalWriteFailureKeepsBothVersionsAndRetries() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); paired(s, r); s.title = "Local revision"; r.conflictUpdate = true
        assertFalse(SyncEngine(s, r).run()); assertEquals("RETRY", s.link?.state); assertEquals("Local revision", s.title)
    }
    @Test fun reconnectWithDifferentCloudTextRequiresExplicitChoice() = runTest {
        val s = MemoryStore(); val r = MemoryRemote(); r.tasks += CloudTask("prior-install", TaskValue("Prior cloud answer"), date)
        SyncEngine(s, r).run(); assertEquals("CONFLICT", s.link?.state); assertEquals("prior-install", s.link?.taskId)
    }
    @Test fun markersRejectInvalidDatesAndDoNotMatchOtherNotes() {
        assertEquals(date, TaskSyncRules.sourceDate(TaskSyncRules.notes(date)))
        assertNull(TaskSyncRules.sourceDate("Personal task $date"))
        assertNull(TaskSyncRules.sourceDate("[daily3minutes:v1:2026-02-30]"))
        assertTrue(TaskSyncRules.eligible("x".repeat(1024))); assertFalse(TaskSyncRules.eligible("x".repeat(1025)))
    }
    @Test fun endOfDateDomainCannotCreateAnInvalidYearTask() {
        assertEquals("9999-12-31T00:00:00.000Z", TaskSyncRules.due("9999-12-30"))
        assertTrue(runCatching { TaskSyncRules.due("9999-12-31") }.isFailure)
    }
}
