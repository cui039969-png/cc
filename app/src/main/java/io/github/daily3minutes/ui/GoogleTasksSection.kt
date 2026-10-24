// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.daily3minutes.BuildConfig
import io.github.daily3minutes.DailyApp
import io.github.daily3minutes.data.DailyRecord
import io.github.daily3minutes.data.SyncLink
import io.github.daily3minutes.sync.GoogleTaskAuth
import io.github.daily3minutes.sync.SyncCoordinator
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun GoogleTasksSection(model: DailyViewModel, enabled: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as DailyApp
    val coordinator = app.sync
    val account by coordinator.account.collectAsState(initial = null)
    val allLinks by coordinator.links.collectAsState(initial = emptyList())
    val links = allLinks.filter { it.accountId == account?.id }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<DailyRecord>?>(null) }
    var includeAll by rememberSaveable { mutableStateOf(false) }
    var pendingId by rememberSaveable { mutableStateOf("") }
    var pendingEmail by rememberSaveable { mutableStateOf("") }
    var pendingSince by rememberSaveable { mutableStateOf("") }
    var disconnect by remember { mutableStateOf(false) }
    var deleteDate by remember { mutableStateOf<String?>(null) }
    val configured = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    val services = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
    fun failure() { model.message(t("Google 连接未完成，记录仍保存在本机。可稍后重试。")) }
    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        scope.launch {
            try {
                if (result.resultCode != Activity.RESULT_OK || result.data == null) { failure(); return@launch }
                val authorization = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data!!)
                if (GoogleTaskAuth.TASKS_SCOPE !in authorization.grantedScopes) { failure(); return@launch }
                if (pendingId.isBlank() || pendingEmail.isBlank() || pendingSince.isBlank()) { failure(); return@launch }
                coordinator.connect(pendingId, pendingEmail, pendingSince)
                model.message(t("已连接 Google Tasks，仅同步预览中确认的交接任务和之后的新任务。"))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { failure()
            } finally { busy = false; pendingId = ""; pendingEmail = ""; pendingSince = "" }
        }
    }
    Column(Modifier.fillMaxWidth().testTag("google-tasks-section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("Google Tasks（可选）"), style = MaterialTheme.typography.titleMedium)
        Text(t("只同步“明天优先解决哪一件事”和任务完成状态。另外两个回答不会上传。未连接也能正常使用。"),
            style = MaterialTheme.typography.bodySmall)
        if (!configured) Text(t("此测试包尚未配置 Google 公开授权，连接暂不可用。配置和审核完成后会发布可连接版本。"),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("google-config-pending"))
        else if (!services) Text(t("此设备的 Google 服务暂不可用。本地记录和文件备份仍可使用。"))
        if (account != null) {
            Text(account!!.email, style = MaterialTheme.typography.bodyMedium)
            Text(syncStatusLabel(account!!.status), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("google-sync-status"))
            if (account!!.lastSuccess > 0) Text(t("上次同步：%s", Instant.ofEpochMilli(account!!.lastSuccess).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))),
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = enabled && !busy, onClick = { model.afterSaved { SyncCoordinator.schedule(context) } },
                modifier = Modifier.fillMaxWidth().testTag("sync-now")) { Text(t("立即同步")) }
        }
        OutlinedButton(enabled = enabled && !busy && configured && services, modifier = Modifier.fillMaxWidth().testTag("connect-google"),
            onClick = {
                model.afterSaved { preview = app.repository.all(); includeAll = false }
            }) { Text(if (account == null) t("连接 Google Tasks") else t("重新授权或调整同步范围")) }
        if (account != null) TextButton(enabled = enabled && !busy, onClick = { disconnect = true }) { Text(t("断开连接")) }
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cui039969-png/cc/blob/main/docs/GOOGLE_TASKS.md"))) }
                .onFailure { model.message(t("无法打开浏览器，请稍后重试。")) }
        }) { Text(t("Google 接入说明与进度")) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (links.isNotEmpty()) {
            Text(t("同步任务"), style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(links.sortedByDescending { it.date }, key = { it.date }) { link ->
                    SyncTaskRow(link, enabled && !busy, onCompleted = { checked -> scope.launch { coordinator.setCompleted(link.date, checked) } },
                        onResolve = { useRemote -> model.afterSaved { coordinator.resolve(link.date, useRemote) } },
                        onDelete = { deleteDate = link.date })
                }
            }
        }
    }
    preview?.let { records ->
        val since = if (includeAll) "0001-01-01" else LocalDate.now().minusDays(6).toString()
        val selected = records.filter { it.date >= since && it.nextPriority.isNotBlank() }
        AlertDialog(onDismissRequest = { preview = null }, title = { Text(t("确认交接任务同步范围")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t("同步会创建独立的 Daily 3 Minutes 列表。Google 授权界面会提示可管理所有任务，本应用只操作自己的任务。"))
                    Row {
                        FilterChip(selected = !includeAll, onClick = { includeAll = false }, label = { Text(t("近7天")) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = includeAll, onClick = { includeAll = true }, label = { Text(t("全部记录")) })
                    }
                    Text(t("将连接 %s 条交接目标；超长目标会保留在本地并提示处理。", selected.size))
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(selected.takeLast(20), key = { it.date }) { record ->
                            Text("${record.date}  ${record.nextPriority}", maxLines = 2, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    Text(t("本地与云端同时改动时会保留两份供你选择，不自动覆盖。"), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = {
                preview = null; pendingSince = since; busy = true
                scope.launch {
                    try {
                        val credential = CredentialManager.create(context).getCredential(context,
                            GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()).build())
                        val google = GoogleIdTokenCredential.createFrom(credential.credential.data)
                        pendingEmail = google.id
                        // SDK-supplied identity is used only to scope local mappings, not as server authentication.
                        val claims = String(android.util.Base64.decode(google.idToken.split('.')[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8)
                        pendingId = org.json.JSONObject(claims).getString("sub")
                        val authorization = Identity.getAuthorizationClient(context).authorize(GoogleTaskAuth.request(pendingEmail)).await()
                        if (authorization.hasResolution()) {
                            val intent = authorization.pendingIntent ?: throw IllegalStateException()
                            resolution.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                        } else {
                            if (GoogleTaskAuth.TASKS_SCOPE !in authorization.grantedScopes) throw IllegalStateException()
                            coordinator.connect(pendingId, pendingEmail, pendingSince)
                            busy = false; pendingId = ""; pendingEmail = ""; pendingSince = ""
                            model.message(t("Google Tasks 已连接，正在同步交接任务。"))
                        }
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { busy = false; pendingId = ""; pendingEmail = ""; pendingSince = ""; failure() }
                }
            }) { Text(t("选择账号并授权")) } },
            dismissButton = { TextButton(onClick = { preview = null }) { Text(t("取消")) } })
    }
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text(t("断开 Google Tasks？")) },
        text = { Text(t("将停止同步。本地回答和云端任务都会保留。若要撤销 Google 授权，可在 Google 账号的第三方连接中移除此应用。")) },
        confirmButton = { TextButton(onClick = { disconnect = false; scope.launch {
            coordinator.disconnect()
            runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
            model.message(t("已断开连接，本地和云端内容均保留。"))
        } }) { Text(t("断开连接")) } }, dismissButton = { TextButton(onClick = { disconnect = false }) { Text(t("取消")) } })
    deleteDate?.let { date -> AlertDialog(onDismissRequest = { deleteDate = null }, title = { Text(t("删除已解除关联的云端任务？")) },
        text = { Text(t("将从 Google Tasks 删除 %s 对应的任务，不影响本地其他回答。", date)) },
        confirmButton = { TextButton(onClick = { deleteDate = null; scope.launch {
            try { coordinator.deleteDetached(date); model.message(t("云端任务已删除。")) }
            catch (_: Exception) { model.message(t("云端删除未完成，请检查连接后重试。")) }
        } }) { Text(t("确认删除")) } }, dismissButton = { TextButton(onClick = { deleteDate = null }) { Text(t("取消")) } }) }
}

private fun syncStatusLabel(status: String): String = when (status) {
    "READY" -> t("等待同步")
    "SYNCING" -> t("正在同步")
    "SYNCED" -> t("同步完成")
    "ATTENTION" -> t("部分任务需要处理，请查看下方状态")
    "AUTH_REQUIRED" -> t("授权已失效或被撤销，请重新授权")
    "LIST_MISSING" -> t("云端列表已不存在，请先断开后重新连接")
    "API_ERROR" -> t("Google 服务返回错误，请稍后重试")
    else -> t("当前无法联网，将在网络恢复后重试")
}

@Composable
private fun SyncTaskRow(link: SyncLink, enabled: Boolean, onCompleted: (Boolean) -> Unit,
    onResolve: (Boolean) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val repository = (context.applicationContext as DailyApp).repository
    val record by remember(link.date) { repository.observe(link.date) }.collectAsState(initial = null)
    OutlinedCard(Modifier.fillMaxWidth().testTag("sync-task-${link.date}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(link.date, style = MaterialTheme.typography.labelLarge)
            when (link.state) {
                "CONFLICT" -> {
                    Text(t("两边都有修改，需要选择保留的版本。"))
                    Text(t("本地：%s", record?.nextPriority.orEmpty()), style = MaterialTheme.typography.bodySmall)
                    Text(t("Google：%s", link.remoteTitle), style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = enabled, onClick = { onResolve(false) }) { Text(t("保留本地并同步")) }
                    TextButton(enabled = enabled, onClick = { onResolve(true) }) { Text(t("采用 Google 版本")) }
                }
                "DETACHED" -> {
                    Text(t("已解除关联，云端任务仍保留。"), style = MaterialTheme.typography.bodySmall)
                    if (link.taskId.isNotBlank()) TextButton(enabled = enabled, onClick = onDelete) { Text(t("删除这条云端任务")) }
                }
                "REMOTE_DELETED" -> Text(t("云端任务已删除，本地回答仍保留。"), style = MaterialTheme.typography.bodySmall)
                "TOO_LONG" -> Text(t("目标超过 Google 的 1024 字符上限，未上传，也未截断。"), style = MaterialTheme.typography.bodySmall)
                "DATE_UNSUPPORTED" -> Text(t("该日期无法转换为 Google 任务日期，回答仍保留在本地。"), style = MaterialTheme.typography.bodySmall)
                "DUPLICATE" -> Text(t("云端存在同日期的多个任务，请先在 Google Tasks 中整理后重试。"), style = MaterialTheme.typography.bodySmall)
                else -> {
                    Row {
                        Checkbox(checked = link.completed, enabled = enabled && link.taskId.isNotBlank(), onCheckedChange = onCompleted)
                        Column {
                            Text(if (link.completed) t("已完成") else t("待完成"))
                            Text(if (link.state == "SYNCED") t("已同步") else t("待同步"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (link.due.isNotBlank()) Text(t("Google 任务日期：%s", link.due), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
