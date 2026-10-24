// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.backup

import io.github.daily3minutes.data.DailyRecord
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC)
    private val first = DailyRecord(
        "2026-10-23", "虚构：掌握函数单调性判断\n第二行 🚀", "虚构：参数分类讨论", "虚构：完成两道练习",
        Instant.parse("2026-10-23T10:00:00Z").toEpochMilli(), Instant.parse("2026-10-23T10:01:00Z").toEpochMilli(),
    )
    private val second = first.copy(date = "2026-10-24", learned = "虚构：100%_ ' \\ SQL")

    @Test fun roundTripPreservesAllFieldsAndMetadataAndSortsAscending() {
        val content = BackupCodec.export(listOf(second, first), clock)
        val decoded = parse(content)
        assertEquals(1, decoded.schemaVersion)
        assertEquals("2026-10-24T10:00:00Z", decoded.exportedAt)
        assertEquals(listOf(first, second), decoded.records)
        assertTrue(content.indexOf("\"date\": \"${first.date}\"") < content.indexOf("\"date\": \"${second.date}\""))
    }

    @Test fun emptyBackupIsValid() {
        assertTrue(parse(BackupCodec.export(emptyList(), clock)).records.isEmpty())
    }

    @Test fun schemaVersionIsRequiredAndFutureVersionIsRejected() {
        val json = validJson()
        reject(json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
        reject(json.replace("\"schemaVersion\": 1,", ""))
        reject(json.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\""))
        reject(json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1.0"))
    }

    @Test fun unknownFieldsAndMissingRecordFieldsAreRejected() {
        reject(validJson().replace("\"schemaVersion\": 1", "\"extra\": true, \"schemaVersion\": 1"))
        reject(validJson().replace("\"learned\":", "\"surprise\": null, \"learned\":"))
        reject(validJson().replace("\"unclear\": \"虚构：参数分类讨论\",", ""))
    }

    @Test fun everyFieldMustHaveItsDeclaredJsonType() {
        val json = validJson()
        reject(json.replace("\"records\": [", "\"records\": {"))
        reject(json.replace("\"date\": \"2026-10-23\"", "\"date\": 20261023"))
        reject(json.replace("\"unclear\": \"虚构：参数分类讨论\"", "\"unclear\": null"))
        reject(json.replace("\"nextPriority\": \"虚构：完成两道练习\"", "\"nextPriority\": []"))
        reject(json.replace("\"createdAt\": ${first.createdAt}", "\"createdAt\": \"${first.createdAt}\""))
        reject(json.replace("\"updatedAt\": ${first.updatedAt}", "\"updatedAt\": 1.5"))
    }

    @Test fun invalidDatesDuplicateDatesAndBadTimestampsAreRejected() {
        reject(validJson().replace("2026-10-23\"", "2026-02-30\""))
        reject(validJson().replace(first.createdAt.toString(), "-1"))
        reject(validJson().replace(first.updatedAt.toString(), "0"))
        assertThrows(BackupValidationException::class.java) { BackupCodec.export(listOf(first, first), clock) }
        val repeated = validJson().replaceAfter("\"records\": [", "\n" + recordJson() + ",\n" + recordJson() + "\n]}" )
        reject(repeated)
    }

    @Test fun exportTimestampMustBeUtcIsoInstant() {
        reject(validJson().replace("2026-10-24T10:00:00Z", "tomorrow"))
        reject(validJson().replace("2026-10-24T10:00:00Z", "2026-10-24T18:00:00+08:00"))
    }

    @Test fun fieldLimitCountsUnicodeCodePointsWithoutTruncation() {
        val allowed = first.copy(learned = "🚀".repeat(20_000))
        assertEquals(allowed, parse(BackupCodec.export(listOf(allowed), clock)).records.single())
        assertThrows(BackupValidationException::class.java) { BackupCodec.export(listOf(allowed.copy(learned = allowed.learned + "字")), clock) }
        reject(validJson().replace("虚构：参数分类讨论", "字".repeat(20_001)))
    }

    @Test fun recordCountLimitRejectsWithoutSilentlyDroppingRows() {
        val tooMany = (0..10_000).map { first.copy(date = LocalDate.of(2000, 1, 1).plusDays(it.toLong()).toString()) }
        assertThrows(BackupValidationException::class.java) { BackupCodec.export(tooMany, clock) }
        reject("{\"schemaVersion\":1,\"exportedAt\":\"2026-10-24T10:00:00Z\",\"records\":[" + List(10_001) { "{}" }.joinToString(",") + "]}")
    }

    @Test fun oversizedInputStopsAtFirstByteBeyondLimit() {
        val input = object : InputStream() {
            var consumed = 0
            override fun read(): Int { consumed++; return ' '.code }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                bytes.fill(' '.code.toByte(), offset, offset + length)
                consumed += length
                return length
            }
        }
        assertThrows(BackupValidationException::class.java) { BackupCodec.parse(input) }
        assertEquals(BackupCodec.MAX_BYTES + 1, input.consumed)
    }

    @Test fun exactlyTenMiBIsAllowedAndExportCountsEscapedUtf8Bytes() {
        val json = validJson()
        val remaining = BackupCodec.MAX_BYTES - json.toByteArray(Charsets.UTF_8).size
        assertEquals(first, parse(json + " ".repeat(remaining)).records.single())
        val escapedText = "\u0000".repeat(20_000)
        val large = (0..99).map { first.copy(date = LocalDate.of(2026, 1, 1).plusDays(it.toLong()).toString(), learned = escapedText) }
        assertThrows(BackupValidationException::class.java) { BackupCodec.export(large, clock) }
    }

    @Test fun invalidUtf8BrokenJsonAndWrongRootAreRejected() {
        assertThrows(BackupValidationException::class.java) { BackupCodec.parse(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }
        listOf("", "{", "[]", "null", "{\"schemaVersion\":1,}").forEach(::reject)
    }

    @Test fun duplicateObjectKeysAndExcessiveNestingAreRejected() {
        reject(validJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2, \"schemaVersion\": 1"))
        reject(validJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schema\\u0056ersion\": 1"))
        reject("[".repeat(1000) + "]".repeat(1000))
    }

    @Test fun validationErrorNeverEchoesPrivateRecordContent() {
        val error = assertThrows(BackupValidationException::class.java) { parse(validJson().replace("2026-10-23\"", "invalid-private-token\"")) }
        assertFalse(error.message.orEmpty().contains("invalid-private-token"))
        assertFalse(error.message.orEmpty().contains(first.learned))
    }

    @Test fun directEnvelopeValidationAlsoRejectsInvalidCallerConstructedData() {
        assertThrows(BackupValidationException::class.java) {
            BackupCodec.validate(BackupEnvelope(1, "2026-10-24T10:00:00Z", listOf(first.copy(updatedAt = -1))))
        }
    }

    private fun validJson() = BackupCodec.export(listOf(first), clock)
    private fun parse(content: String) = BackupCodec.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))
    private fun reject(content: String) { assertThrows(BackupValidationException::class.java) { parse(content) } }
    private fun recordJson(): String = validJson().substringAfter("\"records\": [").substringBeforeLast("]").trim()
}
