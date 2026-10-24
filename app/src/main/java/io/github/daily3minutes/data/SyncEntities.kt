// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sync_accounts")
data class SyncAccount(@PrimaryKey val id: String, val email: String, val listId: String = "",
    val sinceDate: String, val enabled: Boolean = true, val lastSuccess: Long = 0,
    val status: String = "READY")

@Entity(tableName = "sync_links", primaryKeys = ["accountId", "date"])
data class SyncLink(val accountId: String, val date: String, val taskId: String = "",
    val baseTitle: String = "", val baseCompleted: Boolean = false,
    val completed: Boolean = false, val due: String = "", val etag: String = "",
    val state: String = "PENDING", val remoteTitle: String = "", val remoteCompleted: Boolean = false)

@Entity(tableName = "sync_operations", primaryKeys = ["accountId", "date"])
data class SyncOperation(val accountId: String, val date: String, val revision: Long = 1,
    val kind: String = "UPSERT")

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_accounts WHERE enabled = 1 LIMIT 1") fun observeAccount(): Flow<SyncAccount?>
    @Query("SELECT * FROM sync_accounts WHERE enabled = 1 LIMIT 1") suspend fun active(): SyncAccount?
    @Query("SELECT * FROM sync_accounts WHERE id = :id") suspend fun account(id: String): SyncAccount?
    @Query("UPDATE sync_accounts SET enabled = 0") suspend fun disableAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun account(value: SyncAccount)
    @Query("SELECT * FROM sync_links ORDER BY date DESC") fun observeLinks(): Flow<List<SyncLink>>
    @Query("SELECT * FROM sync_links WHERE accountId = :accountId ORDER BY date") suspend fun links(accountId: String): List<SyncLink>
    @Query("SELECT * FROM sync_links WHERE accountId = :accountId AND date = :date") suspend fun link(accountId: String, date: String): SyncLink?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun link(value: SyncLink)
    @Query("SELECT * FROM sync_operations WHERE accountId = :accountId AND date = :date") suspend fun operation(accountId: String, date: String): SyncOperation?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun operation(value: SyncOperation)
    @Query("DELETE FROM sync_operations WHERE accountId = :accountId AND date = :date") suspend fun clearOperation(accountId: String, date: String)
    @Query("DELETE FROM sync_operations WHERE accountId = :accountId") suspend fun clearOperations(accountId: String)
}
