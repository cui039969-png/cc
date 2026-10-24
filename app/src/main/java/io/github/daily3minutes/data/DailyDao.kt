// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyDao {
    @Query("SELECT * FROM daily_records WHERE date = :date")
    fun observe(date: String): Flow<DailyRecord?>

    @Query("SELECT * FROM daily_records WHERE date >= :start AND date <= :end ORDER BY date DESC")
    fun observeMonth(start: String, end: String): Flow<List<DailyRecord>>

    @Query("SELECT * FROM daily_records ORDER BY date DESC")
    fun observeAll(): Flow<List<DailyRecord>>

    // instr treats %, _, quotes and backslashes as literal text; all values remain bound parameters.
    @Query("""
        SELECT * FROM daily_records
        WHERE :query = '' OR instr(lower(learned), lower(:query)) > 0
            OR instr(lower(unclear), lower(:query)) > 0
            OR instr(lower(nextPriority), lower(:query)) > 0
        ORDER BY date DESC
    """)
    fun search(query: String): Flow<List<DailyRecord>>

    @Query("SELECT * FROM daily_records WHERE date = :date")
    suspend fun get(date: String): DailyRecord?

    @Query("SELECT * FROM daily_records ORDER BY date ASC")
    suspend fun all(): List<DailyRecord>

    @Query("SELECT * FROM daily_records WHERE date < :today ORDER BY date DESC LIMIT 1")
    suspend fun latestPast(today: String): DailyRecord?

    @Query("SELECT * FROM daily_records WHERE date < :today ORDER BY date DESC LIMIT 1")
    fun observeLatestPast(today: String): Flow<DailyRecord?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DailyRecord)

    @Query("DELETE FROM daily_records WHERE date = :date")
    suspend fun delete(date: String): Int
}
