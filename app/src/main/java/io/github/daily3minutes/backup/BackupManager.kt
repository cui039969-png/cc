// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.backup

import io.github.daily3minutes.data.DailyRecord
import io.github.daily3minutes.data.DailyRepository
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class BackupEnvelope(val schemaVersion: Int, val exportedAt: String, val records: List<DailyRecord>)

data class ImportPreview(val added: Int, val conflicts: List<String>) {
    val conflictCount: Int get() = conflicts.size
    // Invalid backups are rejected as a whole, never offered as a partially applicable preview.
    val invalidCount: Int get() = 0
}

/** Messages are deliberately content-free so a malformed private backup cannot leak into the UI/logs. */
class BackupValidationException(message: String) : IllegalArgumentException(message)

/** The pure codec is separated from Android/Room so malformed-file behavior can run in JVM tests. */
object BackupCodec {
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_RECORDS = 10_000
    private val rootKeys = setOf("schemaVersion", "exportedAt", "records")
    private val recordKeys = setOf("date", "learned", "unclear", "nextPriority", "createdAt", "updatedAt")
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    fun parse(input: InputStream): BackupEnvelope {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES - total + 1))
            if (read < 0) break
            if (read == 0) {
                // A defensive single-byte fallback also handles unusual InputStream implementations.
                val next = input.read()
                if (next < 0) break
                if (++total > MAX_BYTES) invalid("备份文件超过 10 MiB，未导入任何记录")
                output.write(next)
            } else {
                total += read
                if (total > MAX_BYTES) invalid("备份文件超过 10 MiB，未导入任何记录")
                output.write(buffer, 0, read)
            }
        }
        val content = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray())).toString()
        } catch (_: Exception) {
            invalid("备份文件必须是有效的 UTF-8 文本")
        }
        checkNestingAndDuplicateKeys(content)
        val root = try {
            json.parseToJsonElement(content)
        } catch (_: SerializationException) {
            invalid("备份文件不是有效的 JSON")
        } catch (_: IllegalArgumentException) {
            invalid("备份文件不是有效的 JSON")
        }
        checkStructure(root)
        val envelope = try {
            json.decodeFromJsonElement(BackupEnvelope.serializer(), root)
        } catch (_: SerializationException) {
            invalid("备份字段结构或类型无效")
        } catch (_: IllegalArgumentException) {
            invalid("备份字段结构或类型无效")
        }
        validate(envelope)
        return envelope
    }

    fun export(records: List<DailyRecord>, clock: Clock = Clock.systemUTC()): String {
        val envelope = BackupEnvelope(SCHEMA_VERSION, clock.instant().toString(), records.sortedBy { it.date })
        validate(envelope)
        // Bound the output while serializing one record at a time; a large local database must not
        // require building a potentially hundreds-of-megabytes JSON string before rejecting it.
        val footer = "\n  ]\n}\n"
        var byteCount = footer.toByteArray(Charsets.UTF_8).size
        val result = StringBuilder()
        fun appendBounded(chunk: String) {
            byteCount += chunk.toByteArray(Charsets.UTF_8).size
            if (byteCount > MAX_BYTES) invalid("导出数据超过 10 MiB，无法生成可导入的单文件备份")
            result.append(chunk)
        }
        appendBounded("{\n  \"schemaVersion\": $SCHEMA_VERSION,\n  \"exportedAt\": ${json.encodeToString(envelope.exportedAt)},\n  \"records\": [")
        envelope.records.forEachIndexed { index, record ->
            appendBounded((if (index == 0) "\n" else ",\n") + json.encodeToString(record).prependIndent("    "))
        }
        return result.append(footer).toString()
    }

    fun validate(envelope: BackupEnvelope) {
        if (envelope.schemaVersion != SCHEMA_VERSION) invalid("不支持此备份版本")
        try {
            require(envelope.exportedAt.endsWith('Z'))
            Instant.parse(envelope.exportedAt)
        } catch (_: Exception) {
            invalid("备份导出时间必须是 UTC ISO 时间")
        }
        if (envelope.records.size > MAX_RECORDS) invalid("备份最多允许 10000 条记录")
        val dates = hashSetOf<String>()
        for (record in envelope.records) {
            try {
                record.validate()
            } catch (_: IllegalArgumentException) {
                invalid("备份包含无效日期、文本长度或时间戳")
            }
            if (!dates.add(record.date)) invalid("备份中存在重复日期")
        }
    }

    private fun checkStructure(root: JsonElement) {
        val obj = root as? JsonObject ?: invalid("备份根节点必须是对象")
        if (obj.keys != rootKeys) invalid("备份根字段缺失或包含未知字段")
        requireInteger(obj.getValue("schemaVersion"))
        requireString(obj.getValue("exportedAt"))
        val records = obj["records"] as? JsonArray ?: invalid("备份 records 必须是数组")
        if (records.size > MAX_RECORDS) invalid("备份最多允许 10000 条记录")
        for (record in records) {
            val fields = record as? JsonObject ?: invalid("每条记录必须是对象")
            if (fields.keys != recordKeys) invalid("记录字段缺失或包含未知字段")
            for (name in listOf("date", "learned", "unclear", "nextPriority")) requireString(fields.getValue(name))
            requireInteger(fields.getValue("createdAt"))
            requireInteger(fields.getValue("updatedAt"))
        }
    }

    private fun requireString(element: JsonElement) {
        if (element !is JsonPrimitive || !element.isString) invalid("备份字符串字段类型无效")
    }

    private fun requireInteger(element: JsonElement) {
        if (element !is JsonPrimitive || element.isString || element.longOrNull == null ||
            !Regex("-?(0|[1-9][0-9]*)").matches(element.content)) {
            invalid("备份整数字段类型无效")
        }
    }

    private class JsonScope(val isObject: Boolean) {
        val keys = hashSetOf<String>()
        var expectingKey = isObject
    }

    /** Prevent recursive-parser stack exhaustion and ambiguous duplicate-object-key semantics. */
    private fun checkNestingAndDuplicateKeys(content: String) {
        val scopes = ArrayDeque<JsonScope>()
        var index = 0
        while (index < content.length) {
            when (content[index]) {
                '{', '[' -> {
                    if (scopes.size >= 32) invalid("备份 JSON 嵌套层级过深")
                    scopes.addLast(JsonScope(content[index] == '{'))
                }
                '}', ']' -> if (scopes.isNotEmpty()) scopes.removeLast()
                ',' -> scopes.lastOrNull()?.let { if (it.isObject) it.expectingKey = true }
                '"' -> {
                    val start = index
                    index++
                    while (index < content.length && content[index] != '"') {
                        if (content[index] == '\\') index++
                        index++
                    }
                    if (index >= content.length) invalid("备份文件不是有效的 JSON")
                    val scope = scopes.lastOrNull()
                    if (scope != null && scope.isObject && scope.expectingKey) {
                        val key = try {
                            json.decodeFromString<String>(content.substring(start, index + 1))
                        } catch (_: Exception) { invalid("备份文件不是有效的 JSON") }
                        if (!scope.keys.add(key)) invalid("备份 JSON 包含重复字段")
                        scope.expectingKey = false
                    }
                }
            }
            index++
        }
    }

    private fun invalid(message: String): Nothing = throw BackupValidationException(message)
}

class BackupManager(
    private val repository: DailyRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun parse(input: InputStream): BackupEnvelope = BackupCodec.parse(input)
    fun export(records: List<DailyRecord>): String = BackupCodec.export(records, clock)

    suspend fun preview(envelope: BackupEnvelope): ImportPreview {
        BackupCodec.validate(envelope)
        val result = repository.previewImport(envelope.records)
        return ImportPreview(result.added, result.conflicts)
    }

    suspend fun apply(envelope: BackupEnvelope, overwrite: Boolean = false): ImportPreview {
        BackupCodec.validate(envelope)
        val result = repository.importRecords(envelope.records, overwrite)
        return ImportPreview(result.added, result.conflicts)
    }
}
