// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** v1 has no prior schema migration. Reopening a dedicated test file verifies persistence only. */
@RunWith(AndroidJUnit4::class)
class DatabaseReopenInstrumentedTest {
    @Test fun reopeningVersionOneRetainsSyntheticRecords() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "synthetic-reopen-instrumentation.db"
        context.deleteDatabase(name)
        val expected = DailyRecord("2026-10-23", "纯虚构持久化样例", "", "", 100L, 200L)
        try {
            val first = Room.databaseBuilder(context, DailyDatabase::class.java, name).build()
            try {
                first.records().upsert(expected)
            } finally { first.close() }
            val reopened = Room.databaseBuilder(context, DailyDatabase::class.java, name).build()
            try {
                assertEquals(expected, reopened.records().get("2026-10-23"))
            } finally { reopened.close() }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
