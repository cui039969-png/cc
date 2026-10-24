// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.room.withTransaction
import io.github.daily3minutes.data.DailyDatabase
import io.github.daily3minutes.domain.RecordRules
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.security.MessageDigest

interface VoiceCapture { fun start(file: File, onLimit: () -> Unit); fun stop(); fun release() }

/** Only a foreground, explicitly requested recording uses the microphone. */
class AndroidVoiceCapture(private val context: Context) : VoiceCapture {
    private var recorder: MediaRecorder? = null
    override fun start(file: File, onLimit: () -> Unit) {
        @Suppress("DEPRECATION") val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        recorder = r
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000); r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        r.setMaxDuration(600_000); r.setMaxFileSize(VoiceNote.MAX_FILE_BYTES)
        r.setOnInfoListener { _, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) onLimit()
        }
        r.prepare(); r.start()
    }
    override fun stop() { recorder?.stop() }
    override fun release() { recorder?.release(); recorder = null }
}

data class VoiceSession(val id: String, val date: String, val question: Int, val elapsedStart: Long)

class VoiceLibrary(val db: DailyDatabase, val directory: File,
    private val captureFactory: () -> VoiceCapture,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onChanged: suspend () -> Unit = {}) {
    internal val filesMutex = Mutex()
    private val captureMutex = Mutex()
    private var capture: VoiceCapture? = null
    private val active = MutableStateFlow<VoiceSession?>(null)
    val session = active.asStateFlow()
    val entries = db.voices().observeAll()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private var pending = false
    @Volatile private var generation = 0L
    @Volatile private var foreground = false
    init { directory.mkdirs() }
    fun clearMessage() { mutableMessage.value = null }
    fun setForeground(value: Boolean) { foreground = value; if (!value) finish() }
    fun file(note: VoiceNote): File { note.validate(); return File(directory, "${note.id}.m4a") }
    fun start(date: String, question: Int) {
        RecordRules.validateDate(date); require(question in 0..2)
        if (!foreground) return
        val request = synchronized(this) { if (pending || active.value != null) return; pending = true; generation }
        scope.launch {
            captureMutex.withLock {
                try {
                    if (active.value != null || !foreground || request != generation) return@withLock
                    val id = UUID.randomUUID().toString()
                    val temporary = File(directory, ".$id.pending")
                    val engine = captureFactory(); capture = engine
                    try {
                        engine.start(temporary) { finish() }
                        if (!foreground || request != generation) { runCatching { engine.stop() }; engine.release(); capture = null; temporary.delete(); return@withLock }
                        active.value = VoiceSession(id, date, question, SystemClock.elapsedRealtime())
                    } catch (failure: Exception) { engine.release(); capture = null; temporary.delete(); throw failure }
                } catch (_: Exception) { mutableMessage.value = "无法开始录音，请检查麦克风权限与可用空间。"
                } finally { synchronized(this@VoiceLibrary) { pending = false } }
            }
        }
    }
    fun finish() { synchronized(this) { generation++ }; scope.launch { finishAwait() } }
    suspend fun finishAwait() {
        synchronized(this) { generation++ }
        captureMutex.withLock {
        val current = active.value ?: return@withLock
        val source = File(directory, ".${current.id}.pending")
        val destination = File(directory, "${current.id}.m4a")
        try {
            capture?.stop()
            capture?.release(); capture = null
            val elapsed = (SystemClock.elapsedRealtime() - current.elapsedStart).coerceIn(1, 600_000)
            require(source.length() in 1..VoiceNote.MAX_FILE_BYTES)
            val note = VoiceNote(current.id, current.date, current.question, elapsed,
                System.currentTimeMillis().coerceAtLeast(0), source.length(), digest(source))
            filesMutex.withLock {
                require(source.renameTo(destination))
                try { db.writeMutex.withLock { db.voices().insert(note) } }
                catch (failure: Exception) { destination.delete(); throw failure }
            }
            mutableMessage.value = "录音已保存在本机。"
            runCatching { onChanged() }
        } catch (_: Exception) { source.delete(); mutableMessage.value = "录音未能保存，可能太短或空间不足，请重试。"
        } finally { capture?.release(); capture = null; active.value = null }
        }
    }
    suspend fun delete(note: VoiceNote) = filesMutex.withLock {
        val source = file(note)
        val trash = File(directory, ".${note.id}.trash")
        if (source.exists()) require(source.renameTo(trash))
        try { db.writeMutex.withLock { db.voices().delete(note.id) } }
        catch (failure: Exception) { if (trash.exists()) trash.renameTo(source); throw failure }
        trash.delete()
        runCatching { onChanged() }
    }
    /** Orphans are never presented as entries. Interrupted moves are recovered by their DB identity. */
    suspend fun pruneOrphans() = filesMutex.withLock {
        val live = db.writeMutex.withLock { db.voices().all().mapTo(hashSetOf()) { it.id } }
        directory.listFiles().orEmpty().forEach { f ->
            when {
                f.name.endsWith(".trash") -> {
                    val id = f.name.removePrefix(".").removeSuffix(".trash")
                    if (id in live) f.renameTo(File(directory, "$id.m4a")) else f.delete()
                }
                f.extension == "m4a" && f.nameWithoutExtension !in live -> f.delete()
                f.name.endsWith(".pending") && System.currentTimeMillis() - f.lastModified() > 86_400_000 -> f.delete()
            }
        }
    }
    companion object {
        fun digest(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) {
                val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
            } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
