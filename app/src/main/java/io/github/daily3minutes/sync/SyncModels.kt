// Copyright 2026 Daily 3 Minutes contributors. SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.sync

import java.time.LocalDate

data class TaskValue(val title: String, val completed: Boolean = false)
data class CloudTask(val id: String, val value: TaskValue, val date: String?, val due: String? = null,
    val etag: String = "", val deleted: Boolean = false)
data class MergeResult(val value: TaskValue?, val conflict: Boolean)

/** Field-level three-way merge; unrelated completion and wording edits can merge safely. */
object TaskSyncRules {
    const val MAX_TITLE_UNITS = 1024
    const val LIST_NAME = "Daily 3 Minutes"
    private val marker = Regex("(?m)^\\[daily3minutes:v1:(\\d{4}-\\d{2}-\\d{2})]$")
    fun notes(date: String): String = "Daily 3 Minutes / 每天3分钟\n[daily3minutes:v1:$date]"
    fun sourceDate(notes: String): String? = marker.find(notes)?.groupValues?.get(1)?.takeIf {
        runCatching { LocalDate.parse(it) }.isSuccess
    }
    fun eligible(title: String): Boolean = title.isNotBlank() && title.length <= MAX_TITLE_UNITS
    fun due(date: String): String = LocalDate.parse(date).plusDays(1).also {
        require(it.year in 1..9999) { "Task date outside supported range" }
    }.toString() + "T00:00:00.000Z"

    fun merge(base: TaskValue, local: TaskValue, remote: TaskValue): MergeResult {
        val title = mergeField(base.title, local.title, remote.title)
        val completed = mergeField(base.completed, local.completed, remote.completed)
        return if (title == null || completed == null) MergeResult(null, true)
            else MergeResult(TaskValue(title, completed), false)
    }
    private fun <T> mergeField(base: T, local: T, remote: T): T? = when {
        local == remote -> local
        local == base -> remote
        remote == base -> local
        else -> null
    }
}

interface TasksRemote {
    suspend fun ensureList(existingId: String?): String
    suspend fun list(listId: String): List<CloudTask>
    suspend fun create(listId: String, date: String, value: TaskValue): CloudTask
    suspend fun update(listId: String, task: CloudTask, value: TaskValue): CloudTask
    suspend fun delete(listId: String, taskId: String)
}

class TasksAuthRequired : Exception("Google authorization required")
class TasksHttpException(val status: Int) : Exception("Google Tasks HTTP $status")
