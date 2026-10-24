// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** A single explicit language preference is shared by screens, widgets and notifications. */
object AppLocale {
    private var application: Context? = null
    fun initialize(context: Context) { application = context.applicationContext }
    fun preference(context: Context): String = context.getSharedPreferences("appearance", 0).getString("language", "system") ?: "system"
    fun language(context: Context? = application): String {
        if (context == null) return "zh" // Pure JVM domain/editor tests do not initialize Android.
        val choice = preference(application ?: context)
        return if (choice == "system") context.resources.configuration.locales[0].language else choice
    }
    fun locale(context: Context): Locale = if (language(context) == "zh") Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
    fun wrap(context: Context): Context {
        val choice = preference(context)
        if (choice == "system") return context
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(choice)); setLayoutDirection(Locale.forLanguageTag(choice))
        })
    }
    fun set(context: Context, language: String) {
        require(language in setOf("system", "zh", "en"))
        context.getSharedPreferences("appearance", 0).edit().putString("language", language).commit()
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(android.app.LocaleManager::class.java)
            .applicationLocales = LocaleList.forLanguageTags(if (language == "system") "" else language)
    }
    fun translate(source: String, vararg args: Any?): String = TextCatalog.render(source, language(), *args)
}

fun t(source: String, vararg args: Any?): String = AppLocale.translate(source, *args)
