// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.daily3minutes.backup.BackupEnvelope
import io.github.daily3minutes.backup.BackupManager
import io.github.daily3minutes.backup.BackupValidationException
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** All fixtures use an isolated in-memory database; no production/private records are opened. */
@RunWith(AndroidJUnit4::class)
class DailyRepositoryInstrumentedTest {
    private lateinit var database: DailyDatabase
    private lateinit var repository: DailyRepository
    private val changes = AtomicInteger()
    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DailyDatabase::class.java).build()
        repository = DailyRepository(database, clock) { changes.incrementAndGet() }
    }

    @After fun tearDown() { database.close() }

    @Test fun separateDatesPreserveCreationAndBlankRecordsRequireExistingRow() = runBlocking<Unit> {
        repository.save("2026-10-23", "", "  ", "")
        assertTrue(repository.all().isEmpty())
        assertEquals(0, changes.get())
        repository.save("2026-10-23", "虚构甲", "", "明日")
        val created = repository.get("2026-10-23")!!.createdAt
        val later = DailyRepository(database, Clock.offset(clock, java.time.Duration.ofSeconds(2))) { changes.incrementAndGet() }
        later.save("2026-10-23", "虚构改写", "", "")
        repository.save("2026-10-24", "虚构乙", "", "")
        assertEquals(created, repository.get("2026-10-23")!!.createdAt)
        assertTrue(repository.get("2026-10-23")!!.updatedAt > created)
        assertEquals("虚构乙", repository.get("2026-10-24")!!.learned)
        later.save("2026-10-23", "", "", "")
        assertTrue(repository.get("2026-10-23")!!.isEmpty)
        assertEquals(created, repository.get("2026-10-23")!!.createdAt)
        val previousCount = changes.get()
        later.save("2026-10-23", "", "", "")
        assertEquals(previousCount, changes.get())
        repository.delete("2026-10-23")
        assertNull(repository.get("2026-10-23"))
    }

    @Test fun searchIsLiteralCaseInsensitiveAndDescendingAcrossAllThreeFields() = runBlocking<Unit> {
        repository.save("2026-10-22", "虚构 中文 Alpha 100%_", "", "")
        repository.save("2026-10-23", "", "虚构 中文 alpha ' \\", "")
        repository.save("2026-10-24", "", "", "虚构 中文 ALPHA")
        assertEquals(listOf("2026-10-24", "2026-10-23", "2026-10-22"), repository.search("ALpHa").first().map { it.date })
        assertEquals(3, repository.search("中文").first().size)
        assertEquals(1, repository.search("%_").first().size)
        assertEquals(1, repository.search("'").first().size)
        assertEquals(1, repository.search("\\").first().size)
        assertTrue(repository.search("' OR 1=1 --").first().isEmpty())
        assertEquals(3, repository.search("").first().size)
        assertEquals(listOf("2026-10-23", "2026-10-22"), repository.observeMonth("2026-10-01", "2026-10-23").first().map { it.date })
    }

    @Test fun handoffExcludesTodayAndDoesNotSkipTheClosestEmptyGoal() = runBlocking<Unit> {
        assertNull(repository.latestPast("2026-10-24"))
        repository.save("2026-10-20", "虚构旧日", "", "旧目标")
        repository.save("2026-10-23", "虚构最近日", "", "")
        repository.save("2026-10-24", "虚构今日", "", "今日的明日目标")
        assertEquals("2026-10-23", repository.latestPast("2026-10-24")!!.date)
        assertEquals("", repository.latestPast("2026-10-24")!!.nextPriority)
        assertEquals("2026-10-20", repository.latestPast("2026-10-22")!!.date)
        assertEquals("2026-10-23", repository.observeLatestPast("2026-10-24").first()!!.date)
    }

    @Test fun backupRoundTripRetainsMetadataAndDefaultImportIsIdempotent() = runBlocking<Unit> {
        val manager = BackupManager(repository, clock)
        val records = listOf(record("2026-10-22"), record("2026-10-23").copy(learned = "虚构中文\n🚀"))
        val original = envelope(records)
        assertEquals(2, manager.preview(original).added)
        manager.apply(original)
        assertEquals(records, repository.all())
        val exported = manager.export(repository.all())
        val parsed = manager.parse(ByteArrayInputStream(exported.toByteArray(Charsets.UTF_8)))
        records.forEach { repository.delete(it.date) }
        manager.apply(parsed)
        assertEquals(records, repository.all())
        val previousChanges = changes.get()
        val preview = manager.apply(parsed)
        assertEquals(0, preview.added)
        assertEquals(listOf("2026-10-22", "2026-10-23"), preview.conflicts)
        // Successful imports refresh the widget even when all rows were skipped.
        assertEquals(previousChanges + 1, changes.get())
        assertEquals(records, repository.all())
    }

    @Test fun conflictingImportSkipsByDefaultAndOverwritePreservesImportedMetadata() = runBlocking<Unit> {
        val manager = BackupManager(repository, clock)
        repository.save("2026-10-23", "原有虚构数据", "", "")
        val imported = record("2026-10-23").copy(learned = "导入虚构数据", createdAt = 100, updatedAt = 200)
        val incoming = envelope(listOf(imported, record("2026-10-24")))
        val preview = manager.preview(incoming)
        assertEquals(1, preview.added)
        assertEquals(listOf("2026-10-23"), preview.conflicts)
        manager.apply(incoming)
        assertEquals("原有虚构数据", repository.get("2026-10-23")!!.learned)
        manager.apply(incoming, overwrite = true)
        assertEquals(imported, repository.get("2026-10-23"))
    }

    @Test fun databaseFailureRollsBackTheEntireImportAndNeverCallsRefresh() = runBlocking<Unit> {
        repository.save("2026-10-23", "原有虚构数据", "", "")
        val before = repository.all()
        val previousChanges = changes.get()
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_synthetic_import BEFORE INSERT ON daily_records
            WHEN NEW.date = '2026-10-25'
            BEGIN SELECT RAISE(ABORT, 'synthetic rollback fixture'); END
        """.trimIndent())
        var failed = false
        try {
            BackupManager(repository, clock).apply(envelope(listOf(record("2026-10-23"), record("2026-10-24"), record("2026-10-25"))), true)
        } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(before, repository.all())
        assertEquals(previousChanges, changes.get())
    }

    @Test fun invalidDirectEnvelopeCannotWriteAnyRecord() = runBlocking<Unit> {
        val manager = BackupManager(repository, clock)
        var failed = false
        try {
            manager.apply(envelope(listOf(record("2026-10-23"), record("2026-02-30"))), true)
        } catch (_: BackupValidationException) { failed = true }
        assertTrue(failed)
        assertTrue(repository.all().isEmpty())
        assertEquals(0, changes.get())
    }

    @Test fun concurrentRepositoriesShareTheDatabaseWriteLock() = runBlocking<Unit> {
        val other = DailyRepository(database, Clock.offset(clock, java.time.Duration.ofSeconds(1)))
        coroutineScope {
            val tasks = (1..20).map { index -> async {
                (if (index % 2 == 0) repository else other).save("2026-10-23", "虚构并发 $index", "", "")
            } }
            tasks.forEach { it.await() }
        }
        assertEquals(1, repository.all().size)
        assertTrue(repository.get("2026-10-23")!!.updatedAt >= repository.get("2026-10-23")!!.createdAt)
    }

    private fun record(date: String) = DailyRecord(date, "虚构学习", "虚构疑问", "虚构目标", now.minusSeconds(100).toEpochMilli(), now.toEpochMilli())
    private fun envelope(records: List<DailyRecord>) = BackupEnvelope(1, now.toString(), records)
}
