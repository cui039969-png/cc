// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.backup

import androidx.room.withTransaction
import io.github.daily3minutes.audio.VoiceLibrary
import io.github.daily3minutes.audio.VoiceNote
import io.github.daily3minutes.data.DailyRecord
import io.github.daily3minutes.data.DailyRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import java.util.UUID
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

@Serializable data class VoiceManifest(val archiveVersion: Int, val recordings: List<VoiceNote>)

class VoiceArchivePreview(val envelope: BackupEnvelope, val manifest: VoiceManifest,
    internal val directory: File, internal val manager: VoiceArchiveManager) : Closeable {
    override fun close() { directory.deleteRecursively() }
}

/** A new ZIP container keeps the existing JSON schema1 untouched. No untrusted paths are extracted. */
class VoiceArchiveManager(private val library: VoiceLibrary, private val repository: DailyRepository, private val cache: File) {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false; encodeDefaults = true }
    private val db get() = library.db

    suspend fun export(output: OutputStream): Int {
        library.finishAwait()
        return library.filesMutex.withLock {
        val (records, voices) = db.writeMutex.withLock { db.withTransaction { repository.all() to db.voices().all() } }
        require(voices.size <= MAX_RECORDINGS && voices.sumOf { it.size } <= MAX_AUDIO_BYTES) { "录音备份超过单文件上限" }
        ZipOutputStream(object : FilterOutputStream(output) { override fun close() { flush() } }).use { zip ->
            fun text(name: String, body: String) { zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            text("journal.json", BackupCodec.export(records))
            text("manifest.json", json.encodeToString(VoiceManifest(1, voices)))
            voices.forEach { voice ->
                voice.validate(); val file = library.file(voice)
                require(file.length() == voice.size && VoiceLibrary.digest(file) == voice.sha256) { "录音文件缺失或已改变" }
                zip.putNextEntry(ZipEntry("recordings/${voice.id}.m4a")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
        (records.map { it.date } + voices.map { it.date }).distinct().size
        }
    }

    fun parse(input: InputStream): VoiceArchivePreview {
        val directory = File(cache, "voice-import-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            var journal: ByteArray? = null; var manifest: ByteArray? = null
            val seen = hashSetOf<String>(); val audioIds = hashSetOf<String>()
            var total = 0L
            ZipInputStream(object : FilterInputStream(input) {
                var count = 0L
                override fun read(): Int = super.read().also { if (it >= 0) { count++; require(count <= MAX_AUDIO_BYTES + 12L*1024*1024) } }
                override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len).also {
                    if (it > 0) { count += it; require(count <= MAX_AUDIO_BYTES + 12L*1024*1024) }
                }
            }).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && seen.add(name) && seen.size <= MAX_RECORDINGS + 2) { "备份包含重复或无效条目" }
                    val audio = Regex("recordings/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.m4a").matchEntire(name)
                    require(name == "journal.json" || name == "manifest.json" || audio != null) { "备份包含未知路径" }
                    val limit = when (name) { "journal.json" -> 10L*1024*1024; "manifest.json" -> 1024L*1024; else -> VoiceNote.MAX_FILE_BYTES }
                    val target = if (audio != null) File(directory, "${audio.groupValues[1]}.m4a") else null
                    val bytes = if (target == null) ByteArrayOutputStream() else null
                    (target?.outputStream() ?: bytes!!).use { destination ->
                        var size = 0L; val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer); if (count < 0) break
                            size += count; total += count
                            require(size <= limit && total <= MAX_AUDIO_BYTES + 11L*1024*1024) { "备份解压后超过上限" }
                            destination.write(buffer, 0, count)
                        }
                    }
                    when (name) { "journal.json" -> journal = bytes!!.toByteArray(); "manifest.json" -> manifest = bytes!!.toByteArray()
                        else -> audioIds.add(audio!!.groupValues[1]) }
                }
            }
            val envelope = BackupCodec.parse(ByteArrayInputStream(requireNotNull(journal)))
            val metadata = requireNotNull(manifest).toString(Charsets.UTF_8)
            BackupCodec.checkNestingAndDuplicateKeys(metadata)
            val description = json.decodeFromString<VoiceManifest>(metadata)
            require(description.archiveVersion == 1 && description.recordings.size <= MAX_RECORDINGS)
            require(description.recordings.map { it.id }.toSet() == audioIds && description.recordings.map { it.id }.distinct().size == description.recordings.size)
            description.recordings.forEach { voice ->
                voice.validate(); val file = File(directory, "${voice.id}.m4a")
                require(file.length() == voice.size && VoiceLibrary.digest(file) == voice.sha256) { "录音校验值不一致" }
            }
            require(description.recordings.sumOf { it.size } <= MAX_AUDIO_BYTES)
            return VoiceArchivePreview(envelope, description, directory, this)
        } catch (failure: Throwable) { directory.deleteRecursively(); throw failure }
    }

    suspend fun preview(preview: VoiceArchivePreview): ImportPreview = db.writeMutex.withLock { db.withTransaction {
        val existing = repository.all().map { it.date }.toSet() + db.voices().all().map { it.date }
        val incoming = preview.envelope.records.map { it.date }.toSet() + preview.manifest.recordings.map { it.date }
        ImportPreview(incoming.count { it !in existing }, incoming.filter { it in existing }.sorted())
    } }

    /** Stage verified files first; diary changes and voice metadata commit in one Room transaction. */
    suspend fun apply(preview: VoiceArchivePreview, overwrite: Boolean): ImportPreview = library.filesMutex.withLock {
        withContext(NonCancellable) {
            val copied = mutableListOf<File>()
            try {
                db.writeMutex.withLock { db.withTransaction {
                    val currentVoices = db.voices().all().associateBy { it.id }
                    val existingDates = repository.all().map { it.date }.toSet() + currentVoices.values.map { it.date }
                    val incomingDates = preview.envelope.records.map { it.date }.toSet() + preview.manifest.recordings.map { it.date }
                    val accepted = incomingDates.filter { overwrite || it !in existingDates }.toSet()
                    val selected = preview.manifest.recordings.filter { it.date in accepted }
                    selected.forEach { voice ->
                        val old = currentVoices[voice.id]
                        require(old == null || old == voice) { "录音标识冲突，未覆盖本机音频" }
                        if (old == null) {
                            val source = File(preview.directory, "${voice.id}.m4a")
                            require(source.length() == voice.size && VoiceLibrary.digest(source) == voice.sha256)
                            val target = library.file(voice)
                            require(!target.exists()) { "本机存在待整理音频，请重试" }
                            copied += target
                            source.copyTo(target, overwrite = false)
                            db.voices().insert(voice)
                        }
                    }
                    repository.importInTransaction(preview.envelope.records.filter { it.date in accepted }, overwrite)
                    ImportPreview(incomingDates.count { it !in existingDates }, incomingDates.filter { it in existingDates }.sorted())
                } }
            } catch (failure: Throwable) { copied.forEach { it.delete() }; throw failure }
        }
    }.also { runCatching { repository.notifyChanged() } }
    companion object { const val MAX_RECORDINGS = 1000; const val MAX_AUDIO_BYTES = 512L * 1024 * 1024 }
}
