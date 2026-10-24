// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.sync

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Google REST API v1; HTTPS only, with bounded reads and no content/token logging. */
class GoogleTasksRemote(private val token: suspend () -> String,
    private val invalidate: suspend (String) -> Unit = {}) : TasksRemote {
    private fun path(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private suspend fun request(method: String, path: String, body: JsonObject? = null, etag: String = ""): JsonObject {
        val bearer = token()
        return withContext(Dispatchers.IO) {
            val connection = URL("https://tasks.googleapis.com/tasks/v1/$path").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Authorization", "Bearer $bearer")
                connection.setRequestProperty("Accept", "application/json")
                if (etag.isNotBlank()) connection.setRequestProperty("If-Match", etag)
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
                }
                val code = connection.responseCode
                if (code == 401) { invalidate(bearer); throw TasksAuthRequired() }
                if (code !in 200..299) throw TasksHttpException(code)
                if (code == 204) return@withContext buildJsonObject {}
                val bytes = connection.inputStream.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        check(output.size() + count <= 8 * 1024 * 1024) { "Google response exceeds limit" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            } finally { connection.disconnect() }
        }
    }
    private suspend fun pages(base: String): List<JsonObject> {
        val items = mutableListOf<JsonObject>()
        val seen = hashSetOf<String>()
        var next = ""
        do {
            val response = request("GET", base + if (next.isNotBlank()) "&pageToken=${path(next)}" else "")
            items += response["items"]?.jsonArray.orEmpty().map { it.jsonObject }
            check(items.size <= 100_000) { "Task limit reached" }
            next = response["nextPageToken"]?.jsonPrimitive?.contentOrNull.orEmpty()
            check(next.isEmpty() || seen.add(next)) { "Repeated Google page token" }
        } while (next.isNotEmpty())
        return items
    }
    override suspend fun ensureList(existingId: String?): String {
        if (existingId != null) return request("GET", "users/@me/lists/${path(existingId)}").getValue("id").jsonPrimitive.content
        // Only this dedicated list is inspected; tasks in all other lists are untouched.
        val candidates = pages("users/@me/lists?maxResults=100").filter { it["title"]?.jsonPrimitive?.content == TaskSyncRules.LIST_NAME }
        require(candidates.size <= 1) { "More than one app list exists" }
        return candidates.singleOrNull()?.getValue("id")?.jsonPrimitive?.content ?: request("POST", "users/@me/lists",
            buildJsonObject { put("title", TaskSyncRules.LIST_NAME) }).getValue("id").jsonPrimitive.content
    }
    override suspend fun list(listId: String) = pages("lists/${path(listId)}/tasks?maxResults=100&showCompleted=true&showHidden=true&showDeleted=true")
        .map(::task)

    override suspend fun create(listId: String, date: String, value: TaskValue): CloudTask {
        require(TaskSyncRules.eligible(value.title))
        return task(request("POST", "lists/${path(listId)}/tasks", buildJsonObject {
            put("title", value.title); put("notes", TaskSyncRules.notes(date)); put("due", TaskSyncRules.due(date))
            put("status", if (value.completed) "completed" else "needsAction")
        }))
    }
    override suspend fun update(listId: String, task: CloudTask, value: TaskValue): CloudTask {
        require(TaskSyncRules.eligible(value.title))
        return task(request("PATCH", "lists/${path(listId)}/tasks/${path(task.id)}", buildJsonObject {
            put("title", value.title); put("status", if (value.completed) "completed" else "needsAction")
            if (!value.completed) put("completed", JsonNull)
        }, task.etag))
    }
    override suspend fun delete(listId: String, taskId: String) {
        try { request("DELETE", "lists/${path(listId)}/tasks/${path(taskId)}") }
        catch (failure: TasksHttpException) { if (failure.status != 404) throw failure }
    }
    private fun task(json: JsonObject) = CloudTask(json.getValue("id").jsonPrimitive.content,
        TaskValue(json["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), json["status"]?.jsonPrimitive?.content == "completed"),
        TaskSyncRules.sourceDate(json["notes"]?.jsonPrimitive?.contentOrNull.orEmpty()),
        json["due"]?.jsonPrimitive?.contentOrNull?.take(10), json["etag"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        json["deleted"]?.jsonPrimitive?.booleanOrNull ?: false)
}
