// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.audio

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import io.github.daily3minutes.domain.RecordRules

@Serializable
@Entity(tableName = "voice_notes", indices = [Index(value = ["date", "question"])])
data class VoiceNote(@PrimaryKey val id: String, val date: String, val question: Int,
    val durationMillis: Long, val createdAt: Long, val size: Long, val sha256: String) {
    fun validate() {
        require(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(id))
        RecordRules.validateDate(date)
        require(question in 0..2 && durationMillis in 1..600_000 && createdAt >= 0 && size in 1..MAX_FILE_BYTES)
        require(Regex("[0-9a-f]{64}").matches(sha256))
    }
    companion object { const val MAX_FILE_BYTES = 20L * 1024 * 1024 }
}

@Dao interface VoiceDao {
    @Query("SELECT * FROM voice_notes ORDER BY createdAt DESC, id") fun observeAll(): Flow<List<VoiceNote>>
    @Query("SELECT * FROM voice_notes ORDER BY id") suspend fun all(): List<VoiceNote>
    @Query("SELECT * FROM voice_notes WHERE id=:id") suspend fun get(id: String): VoiceNote?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(note: VoiceNote)
    @Query("DELETE FROM voice_notes WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM voice_notes WHERE date=:date") suspend fun deleteDate(date: String): Int
}
