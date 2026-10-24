// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import java.util.Locale

/** App-authored text only. Diary answers are never passed to this translator. */
object TextCatalog {
    val english: Map<String, String> by lazy {
        val stream = checkNotNull(TextCatalog::class.java.getResourceAsStream("/i18n-en.tsv")) { "Missing translation catalog" }
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val fields = line.split('\t', limit = 2)
                require(fields.size == 2) { "Invalid translation entry" }
                fields[0] to fields[1]
            }.toList().also { require(it.map { pair -> pair.first }.distinct().size == it.size) { "Duplicate translation key" } }.toMap()
        }
    }
    private val templates by lazy {
        val placeholder = Regex("%[1-9][" + '$' + "]s")
        english.filterKeys { it.contains(placeholder) }.map { (source, translated) ->
            val fields = source.split(placeholder)
            Regex(fields.joinToString("(.*?)") { Regex.escape(it) }, RegexOption.DOT_MATCHES_ALL) to translated
        }
    }
    fun render(source: String, language: String, vararg args: Any?): String {
        if (language == "zh") return if (args.isEmpty()) source else source.format(Locale.ROOT, *args)
        if (args.isEmpty() && source.startsWith("导入被拒绝：")) {
            return "Import rejected: " + render(source.removePrefix("导入被拒绝："), language)
        }
        var index = 1
        val normalized = source.replace(Regex("%s")) { "%${index++}" + '$' + "s" }
        val exact = english[normalized]
        if (exact != null) return if (args.isEmpty()) exact else exact.format(Locale.ROOT, *args)
        if (args.isEmpty()) {
            for ((pattern, translated) in templates) {
                val match = pattern.matchEntire(source) ?: continue
                return translated.format(Locale.ROOT, *match.groupValues.drop(1).toTypedArray())
            }
        }
        return if (args.isEmpty()) source else source.format(Locale.ROOT, *args)
    }
}
