// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.data

import android.util.Log
import androidx.room.withTransaction
import io.github.daily3minutes.domain.RecordRules
import io.github.daily3minutes.sync.queuePriority
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock

data class ImportResult(val added: Int, val conflicts: List<String>)

class DailyRepository(
    private val database: DailyDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val onChanged: suspend () -> Unit = {},
) {
    private val dao = database.records()

    fun observe(date: String): Flow<DailyRecord?> {
        RecordRules.validateDate(date)
        return dao.observe(date)
    }

    fun observeMonth(start: String, end: String): Flow<List<DailyRecord>> {
        RecordRules.validateDate(start)
        RecordRules.validateDate(end)
        require(start <= end) { "日期范围无效" }
        return dao.observeMonth(start, end)
    }

    fun observeAll(): Flow<List<DailyRecord>> = dao.observeAll()
    fun search(query: String): Flow<List<DailyRecord>> = dao.search(query)

    suspend fun get(date: String): DailyRecord? {
        RecordRules.validateDate(date)
        return dao.get(date)
    }

    suspend fun all(): List<DailyRecord> = dao.all()

    suspend fun latestPast(today: String): DailyRecord? {
        RecordRules.validateDate(today)
        return dao.latestPast(today)
    }

    fun observeLatestPast(today: String): Flow<DailyRecord?> {
        RecordRules.validateDate(today)
        return dao.observeLatestPast(today)
    }

    suspend fun save(date: String, learned: String, unclear: String, nextPriority: String) {
        RecordRules.validateDate(date)
        RecordRules.validateText(learned)
        RecordRules.validateText(unclear)
        RecordRules.validateText(nextPriority)
        val changed = database.writeMutex.withLock {
            database.withTransaction {
                val previous = dao.get(date)
                if (previous == null && learned.isBlank() && unclear.isBlank() && nextPriority.isBlank()) {
                    return@withTransaction false
                }
                if (previous != null && previous.learned == learned && previous.unclear == unclear &&
                    previous.nextPriority == nextPriority) {
                    return@withTransaction false
                }
                // Backward system clock changes never make a record's metadata internally invalid.
                val now = maxOf(0L, clock.millis(), previous?.updatedAt ?: 0L)
                dao.upsert(DailyRecord(date, learned, unclear, nextPriority, previous?.createdAt ?: now, now))
                database.queuePriority(date, previous?.nextPriority, nextPriority)
                true
            }
        }
        if (changed) notifyChanged()
    }

    suspend fun delete(date: String) {
        RecordRules.validateDate(date)
        val changed = database.writeMutex.withLock { database.withTransaction {
            val previous = dao.get(date)
            val removed = dao.delete(date) > 0
            if (removed) database.queuePriority(date, previous?.nextPriority, null)
            removed
        } }
        if (changed) notifyChanged()
    }

    /** Snapshot preview; import rechecks conflicts in its own transaction before applying anything. */
    internal suspend fun previewImport(records: List<DailyRecord>): ImportResult = database.writeMutex.withLock {
        database.withTransaction {
            val existing = dao.all().mapTo(hashSetOf()) { it.date }
            ImportResult(records.count { it.date !in existing }, records.map { it.date }.filter { it in existing }.sorted())
        }
    }

    /** No callbacks, partial commits or metadata rewrites occur if any insertion fails. */
    internal suspend fun importRecords(records: List<DailyRecord>, overwrite: Boolean): ImportResult {
        records.forEach(DailyRecord::validate)
        require(records.map { it.date }.distinct().size == records.size) { "备份中存在重复日期" }
        val result = database.writeMutex.withLock {
            database.withTransaction {
                val existing = dao.all().associateBy { it.date }
                val conflicts = records.map { it.date }.filter { it in existing }.sorted()
                for (record in records) {
                    val old = existing[record.date]
                    if ((old == null || overwrite) && old != record) {
                        dao.upsert(record)
                        database.queuePriority(record.date, old?.nextPriority, record.nextPriority)
                    }
                }
                ImportResult(records.count { it.date !in existing }, conflicts)
            }
        }
        // A completed import refreshes derived views even when all conflicts were skipped.
        notifyChanged()
        return result
    }

    private suspend fun notifyChanged() {
        try {
            onChanged()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A failed Launcher refresh must not turn a committed database save into a reported failure.
            Log.w("DailyRepository", "Data saved; the optional refresh callback will need a later retry")
        }
    }
}
