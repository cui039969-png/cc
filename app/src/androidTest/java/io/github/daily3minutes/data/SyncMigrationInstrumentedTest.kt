// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.daily3minutes.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncMigrationInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun schemaOneMigrationRetainsEveryFieldAndCreatesEmptySyncTables() {
        val name = "synthetic-schema-v040"
        try {
            // Load the actual exported v1 schema. Room itself validates v2 when opening below.
            // This avoids an ABI mismatch in room-testing's precompiled JSON serializers.
            val schema = org.json.JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("io.github.daily3minutes.data.DailyDatabase/1.json")
                .bufferedReader().use { it.readText() }).getJSONObject("database")
            val legacy = object : android.database.sqlite.SQLiteOpenHelper(context, name, null, 1) {
                override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                }
                override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, old: Int, new: Int) = error("Unexpected legacy upgrade")
            }
            legacy.writableDatabase.apply {
                execSQL("INSERT INTO daily_records VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>("2026-10-24", "Synthetic learning 学习", "Synthetic question", "Synthetic priority", 100L, 200L))
            }
            legacy.close()
            val upgraded = Room.databaseBuilder(context, DailyDatabase::class.java, name)
                .addMigrations(DailyDatabase.MIGRATION_1_2).build()
            upgraded.openHelper.writableDatabase.apply {
                query("SELECT * FROM daily_records").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Synthetic learning 学习", it.getString(it.getColumnIndexOrThrow("learned")))
                    assertEquals("Synthetic question", it.getString(it.getColumnIndexOrThrow("unclear")))
                    assertEquals("Synthetic priority", it.getString(it.getColumnIndexOrThrow("nextPriority")))
                    assertEquals(100L, it.getLong(it.getColumnIndexOrThrow("createdAt")))
                    assertEquals(200L, it.getLong(it.getColumnIndexOrThrow("updatedAt")))
                }
                query("SELECT COUNT(*) FROM sync_accounts").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
            upgraded.close()
        } finally { context.deleteDatabase(name) }
    }

    @Test fun priorityAndQueueCommitTogetherAndClearingDetaches() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DailyDatabase::class.java).build()
        try {
            db.sync().account(SyncAccount("synthetic", "test@example.invalid", sinceDate = "2026-10-18"))
            val repository = DailyRepository(db)
            repository.save("2026-10-24", "Learn", "Question", "Goal")
            assertNotNull(db.sync().operation("synthetic", "2026-10-24"))
            db.sync().clearOperation("synthetic", "2026-10-24")
            repository.save("2026-10-24", "New learning", "Question", "Goal")
            assertNull(db.sync().operation("synthetic", "2026-10-24"))
            db.sync().link(SyncLink("synthetic", "2026-10-24", "task", "Goal"))
            repository.save("2026-10-24", "New learning", "Question", "")
            assertEquals("DETACHED", db.sync().link("synthetic", "2026-10-24")!!.state)
            assertEquals("New learning", repository.get("2026-10-24")!!.learned)
        } finally { db.close() }
    }

    @Test fun cloudCommitPreservesOtherAnswersAndRejectsStaleSnapshot() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DailyDatabase::class.java).build()
        try {
            val repository = DailyRepository(db)
            repository.save("2026-10-24", "Keep learning", "Keep question", "Old goal")
            val old = repository.get("2026-10-24")!!
            db.sync().account(SyncAccount("synthetic", "test@example.invalid", sinceDate = "2026-10-18"))
            val store = RoomSyncStore(db)
            val snapshot = store.snapshot("synthetic", "2026-10-24")
            val cloud = CloudTask("task", TaskValue("Cloud goal", true), "2026-10-24")
            assertTrue(store.commit("synthetic", snapshot, cloud, cloud.value))
            val changed = repository.get("2026-10-24")!!
            assertEquals(old.learned, changed.learned); assertEquals(old.unclear, changed.unclear)
            assertEquals(old.createdAt, changed.createdAt); assertEquals("Cloud goal", changed.nextPriority)
            assertFalse(store.commit("synthetic", snapshot, cloud.copy(value = TaskValue("Stale")), TaskValue("Stale")))
            assertEquals("Cloud goal", repository.get("2026-10-24")!!.nextPriority)
        } finally { db.close() }
    }
}
