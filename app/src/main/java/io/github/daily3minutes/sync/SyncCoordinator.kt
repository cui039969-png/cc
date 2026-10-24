// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import io.github.daily3minutes.DailyApp
import io.github.daily3minutes.data.*
import io.github.daily3minutes.widget.DailyWidget
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncCoordinator(private val context: Context, private val db: DailyDatabase,
    private val remoteFactory: (String) -> TasksRemote = { email -> GoogleTasksRemote(
        { GoogleTaskAuth.token(context, email) }, { GoogleTaskAuth.invalidate(context, it) }) }) {
    val account = db.sync().observeAccount()
    val links = db.sync().observeLinks()
    private val mutex = Mutex()
    var editorBarrier: (suspend (suspend () -> Boolean) -> Boolean)? = null
    private val store = RoomSyncStore(db, guard = { action -> editorBarrier?.invoke(action) ?: action() }) { DailyWidget.refresh(context) }

    suspend fun connect(id: String, email: String, sinceDate: String) = mutex.withLock {
        require(id.isNotBlank() && email.isNotBlank())
        LocalDate.parse(sinceDate)
        db.writeMutex.withLock {
            db.withTransaction {
                val previous = db.sync().account(id)
                db.sync().disableAll()
                db.sync().account((previous ?: SyncAccount(id, email, sinceDate = sinceDate))
                    .copy(email = email, enabled = true, sinceDate = sinceDate, status = "READY",
                        listId = if (previous?.status == "LIST_MISSING") "" else previous?.listId.orEmpty()))
                for (record in db.records().all().filter { it.date >= sinceDate && it.nextPriority.isNotBlank() }) {
                    val link = db.sync().link(id, record.date)
                    // User explicitly approved a reconnect preview; preserved cloud tasks can be rebound.
                    if (link != null && (link.state in setOf("DETACHED", "REMOTE_DELETED") || previous?.status == "LIST_MISSING"))
                        db.sync().link(link.copy(taskId = "", state = "PENDING"))
                    val op = db.sync().operation(id, record.date)
                    db.sync().operation(SyncOperation(id, record.date, (op?.revision ?: 0) + 1))
                }
            }
        }
        schedule(context)
    }

    suspend fun disconnect() {
        // Disable before waiting on a network call so no further task requests are started.
        db.writeMutex.withLock { db.withTransaction { db.sync().disableAll() } }
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NOW)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PERIODIC)
    }

    suspend fun run(): Boolean = mutex.withLock {
        val active = db.sync().active() ?: return@withLock true
        SyncEngine(store, remoteFactory(active.email)).run()
    }

    suspend fun setCompleted(date: String, completed: Boolean) {
        db.writeMutex.withLock {
            db.withTransaction {
                val active = db.sync().active() ?: return@withTransaction
                val link = db.sync().link(active.id, date) ?: return@withTransaction
                if (link.state in setOf("DETACHED", "REMOTE_DELETED", "CONFLICT")) return@withTransaction
                db.sync().link(link.copy(completed = completed, state = "PENDING"))
                val op = db.sync().operation(active.id, date)
                db.sync().operation(SyncOperation(active.id, date, (op?.revision ?: 0) + 1))
            }
        }
        schedule(context)
    }

    suspend fun resolve(date: String, useRemote: Boolean) {
        db.writeMutex.withLock {
            db.withTransaction {
                val account = db.sync().active() ?: return@withTransaction
                val link = db.sync().link(account.id, date)?.takeIf { it.state == "CONFLICT" } ?: return@withTransaction
                val record = db.records().get(date) ?: return@withTransaction
                if (useRemote) {
                    db.records().upsert(record.copy(nextPriority = link.remoteTitle,
                        updatedAt = maxOf(record.updatedAt, System.currentTimeMillis())))
                }
                db.sync().link(link.copy(baseTitle = link.remoteTitle, baseCompleted = link.remoteCompleted,
                    completed = if (useRemote) link.remoteCompleted else link.completed, state = "PENDING"))
                val op = db.sync().operation(account.id, date)
                db.sync().operation(SyncOperation(account.id, date, (op?.revision ?: 0) + 1))
            }
        }
        DailyWidget.refresh(context)
        schedule(context)
    }

    /** Only reachable from an explicit confirmation for a detached, app-linked cloud task. */
    suspend fun deleteDetached(date: String) = mutex.withLock {
        val account = db.sync().active() ?: return@withLock
        val link = db.sync().link(account.id, date) ?: return@withLock
        if (link.state != "DETACHED" || link.taskId.isBlank() || account.listId.isBlank()) return@withLock
        remoteFactory(account.email).delete(account.listId, link.taskId)
        db.writeMutex.withLock { db.withTransaction {
            if (db.sync().link(account.id, date) == link) db.sync().link(link.copy(state = "REMOTE_DELETED"))
        } }
    }

    companion object {
        private const val WORK_NOW = "google-tasks-sync"
        private const val WORK_PERIODIC = "google-tasks-periodic"
        fun schedule(context: Context) {
            if (io.github.daily3minutes.BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) return
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<TasksSyncWorker>().setConstraints(constraints)
                    .setInitialDelay(1, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TasksSyncWorker>(30, TimeUnit.MINUTES).setConstraints(constraints).build())
        }
    }
}

class TasksSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = if ((applicationContext as DailyApp).sync.run()) Result.success() else Result.retry()
}
