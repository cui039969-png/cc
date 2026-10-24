// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import io.github.daily3minutes.domain.RecordRules
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "daily_records")
data class DailyRecord(
    @PrimaryKey val date: String,
    val learned: String,
    val unclear: String,
    val nextPriority: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    @get:Ignore
    val isEmpty: Boolean
        get() = learned.isBlank() && unclear.isBlank() && nextPriority.isBlank()

    fun validate() {
        RecordRules.validateDate(date)
        RecordRules.validateText(learned)
        RecordRules.validateText(unclear)
        RecordRules.validateText(nextPriority)
        require(createdAt >= 0 && updatedAt >= createdAt) { "记录时间戳无效" }
    }
}
