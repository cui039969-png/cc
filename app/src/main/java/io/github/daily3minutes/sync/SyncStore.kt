// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.sync

import androidx.room.withTransaction
import io.github.daily3minutes.data.*
import java.time.Clock
import kotlinx.coroutines.sync.withLock

data class SyncSnapshot(val date: String, val title: String?, val link: SyncLink?, val operation: SyncOperation?) {
    val value get() = TaskValue(title.orEmpty(), link?.completed ?: false)
}

interface SyncStore {
    suspend fun active(): SyncAccount?
    suspend fun rememberList(accountId: String, listId: String)
    suspend fun dates(account: SyncAccount): List<String>
    suspend fun snapshot(accountId: String, date: String): SyncSnapshot
    suspend fun commit(accountId: String, before: SyncSnapshot, task: CloudTask, value: TaskValue): Boolean
    suspend fun mark(accountId: String, before: SyncSnapshot, state: String, remote: CloudTask? = null)
    suspend fun status(accountId: String, status: String, success: Boolean = false)
}

/** Called inside the record transaction: a committed diary edit and its queue cannot diverge. */
internal suspend fun DailyDatabase.queuePriority(date: String, previous: String?, next: String?) {
    if (previous == next) return
    val account = sync().active() ?: return
    val link = sync().link(account.id, date)
    if (link == null && (date < account.sinceDate || next.isNullOrBlank())) return
    if (next.isNullOrBlank()) {
        if (link != null) sync().link(link.copy(state = "DETACHED"))
        sync().clearOperation(account.id, date)
        return
    }
    if (link?.state in setOf("DETACHED", "REMOTE_DELETED")) return
    val previousOp = sync().operation(account.id, date)
    sync().operation(SyncOperation(account.id, date, (previousOp?.revision ?: 0) + 1))
    if (link != null && link.state != "CONFLICT") sync().link(link.copy(state = "PENDING"))
}

class RoomSyncStore(private val db: DailyDatabase, private val clock: Clock = Clock.systemUTC(),
    private val guard: suspend (suspend () -> Boolean) -> Boolean = { it() },
    private val changed: suspend () -> Unit = {}) : SyncStore {
    override suspend fun active() = db.sync().active()
    override suspend fun rememberList(accountId: String, listId: String): Unit = db.writeMutex.withLock {
        db.withTransaction {
            db.sync().active()?.takeIf { it.id == accountId }?.let { db.sync().account(it.copy(listId = listId)) }
            Unit
        }
    }
    override suspend fun dates(account: SyncAccount): List<String> = db.writeMutex.withLock {
        db.withTransaction {
            (db.records().all().filter { it.date >= account.sinceDate && it.nextPriority.isNotBlank() }.map { it.date } +
                db.sync().links(account.id).filter { it.state !in setOf("DETACHED", "REMOTE_DELETED") }.map { it.date })
                .distinct().sorted()
        }
    }
    override suspend fun snapshot(accountId: String, date: String) = db.writeMutex.withLock {
        db.withTransaction { snapshotLocked(accountId, date) }
    }
    private suspend fun snapshotLocked(accountId: String, date: String) = SyncSnapshot(date,
        db.records().get(date)?.nextPriority, db.sync().link(accountId, date), db.sync().operation(accountId, date))

    override suspend fun commit(accountId: String, before: SyncSnapshot, task: CloudTask, value: TaskValue): Boolean {
        var diaryChanged = false
        val accepted = guard { db.writeMutex.withLock {
            db.withTransaction {
                if (db.sync().active()?.id != accountId || snapshotLocked(accountId, before.date) != before) return@withTransaction false
                val record = db.records().get(before.date) ?: return@withTransaction false
                if (record.nextPriority != value.title) {
                    db.records().upsert(record.copy(nextPriority = value.title,
                        updatedAt = maxOf(record.updatedAt, clock.millis(), 0)))
                    diaryChanged = true
                }
                db.sync().link(SyncLink(accountId, before.date, task.id, value.title, value.completed,
                    value.completed, task.due.orEmpty(), task.etag, "SYNCED"))
                db.sync().clearOperation(accountId, before.date)
                true
            }
        } }
        if (diaryChanged) changed()
        return accepted
    }

    override suspend fun mark(accountId: String, before: SyncSnapshot, state: String, remote: CloudTask?) {
        db.writeMutex.withLock {
            db.withTransaction {
                if (db.sync().active()?.id != accountId || snapshotLocked(accountId, before.date) != before) return@withTransaction
                val old = before.link ?: SyncLink(accountId, before.date, taskId = remote?.id.orEmpty())
                db.sync().link(old.copy(state = state, remoteTitle = remote?.value?.title ?: old.remoteTitle,
                    remoteCompleted = remote?.value?.completed ?: old.remoteCompleted))
                if (state in setOf("DETACHED", "REMOTE_DELETED")) db.sync().clearOperation(accountId, before.date)
            }
        }
    }
    override suspend fun status(accountId: String, status: String, success: Boolean): Unit = db.writeMutex.withLock {
        db.withTransaction {
            db.sync().active()?.takeIf { it.id == accountId }?.let {
                db.sync().account(it.copy(status = status, lastSuccess = if (success) clock.millis() else it.lastSuccess))
            }
            Unit
        }
    }
}
