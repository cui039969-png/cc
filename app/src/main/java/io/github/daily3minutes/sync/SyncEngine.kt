// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.sync

import kotlinx.coroutines.CancellationException

/** Network effects are limited to linked or marked tasks in the dedicated list. */
class SyncEngine(private val store: SyncStore, private val remote: TasksRemote) {
    suspend fun run(): Boolean {
        val account = store.active() ?: return true
        try {
            store.status(account.id, "SYNCING")
            val listId = remote.ensureList(account.listId.ifBlank { null })
            if (store.active()?.id != account.id) return true
            store.rememberList(account.id, listId)
            val tasks = remote.list(listId)
            val byId = tasks.associateBy { it.id }
            val byDate = tasks.filter { !it.deleted && it.date != null }.groupBy { it.date!! }
            var attention = false
            var retry = false
            for (date in store.dates(account)) {
                if (store.active()?.id != account.id) return true
                val before = store.snapshot(account.id, date)
                if (before.link?.state in setOf("DETACHED", "REMOTE_DELETED")) continue
                if (before.title.isNullOrBlank()) { store.mark(account.id, before, "DETACHED"); continue }
                if (!TaskSyncRules.eligible(before.title)) { store.mark(account.id, before, "TOO_LONG"); attention = true; continue }
                if (runCatching { TaskSyncRules.due(date) }.getOrNull()?.take(4)?.toIntOrNull() !in 1..9999) {
                    store.mark(account.id, before, "DATE_UNSUPPORTED"); attention = true; continue
                }
                if (before.link?.state == "CONFLICT") { attention = true; continue }
                val linkedId = before.link?.taskId.orEmpty()
                val candidates = byDate[date].orEmpty()
                if (linkedId.isBlank() && candidates.size > 1) {
                    store.mark(account.id, before, "DUPLICATE"); attention = true; continue
                }
                val cloud = if (linkedId.isNotBlank()) byId[linkedId] else candidates.singleOrNull()
                if (linkedId.isNotBlank() && (cloud == null || cloud.deleted)) {
                    store.mark(account.id, before, "REMOTE_DELETED"); attention = true; continue
                }
                if (cloud == null) {
                    // A lost POST response is recovered by the source-date marker on the next run.
                    val created = remote.create(listId, date, before.value)
                    if (!store.commit(account.id, before, created, created.value)) { attention = true; retry = true }
                    continue
                }
                val merged = if (linkedId.isBlank()) {
                    // Reconnecting never silently replaces a different existing local answer.
                    if (cloud.value.title == before.title) MergeResult(cloud.value, false) else MergeResult(null, true)
                } else TaskSyncRules.merge(TaskValue(before.link!!.baseTitle, before.link.baseCompleted), before.value, cloud.value)
                if (merged.conflict) {
                    store.mark(account.id, before, "CONFLICT", cloud); attention = true; continue
                }
                val value = merged.value!!
                try {
                    val result = if (value == cloud.value) cloud else remote.update(listId, cloud, value)
                    if (!store.commit(account.id, before, result, result.value)) { attention = true; retry = true }
                } catch (failure: TasksHttpException) {
                    if (failure.status == 412) {
                        // Keep the newer cloud version; reload it before presenting conflict choices.
                        store.mark(account.id, before, "RETRY"); attention = true; retry = true
                    } else throw failure
                }
            }
            store.status(account.id, if (attention) "ATTENTION" else "SYNCED", success = true)
            return !retry
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: TasksAuthRequired) {
            store.status(account.id, "AUTH_REQUIRED")
            return true // Needs foreground consent, not an endless background retry.
        } catch (failure: TasksHttpException) {
            store.status(account.id, if (failure.status == 404) "LIST_MISSING" else "API_ERROR")
            return failure.status !in setOf(408, 429, 500, 502, 503, 504)
        } catch (_: Exception) {
            store.status(account.id, "OFFLINE")
            return false
        }
    }
}
