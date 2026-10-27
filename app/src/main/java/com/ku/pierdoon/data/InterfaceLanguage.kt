package com.ku.pierdoon.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * The interface language.
 *
 * Android 13+ keeps a per-app language itself (it's also in the system's app settings) and
 * applies it everywhere in the app. Before that it's kept here, and every component that shows
 * text wraps its context in [wrap]: the activity and the services that post notifications.
 */
object InterfaceLanguage {
    private const val PREFS = "settings"
    private const val KEY = "language"

    fun current(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) AppLanguage.System else AppLanguage.fromTag(locales[0].language)
        } else {
            stored(context)
        }

    /** Switches to [language]; the activity is recreated with it. */
    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system recreates the activity by itself.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == AppLanguage.System) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
            return
        }
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putString(KEY, language.tag) }
        activity.recreate()
    }

    /** [base] with the chosen language; [base] itself on Android 13+ or with the system's. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = stored(base)
        // The device's own, unlike Locale.getDefault(), which a chosen language replaced.
        val locale = if (language == AppLanguage.System) Resources.getSystem().configuration.locales[0]
            else Locale.forLanguageTag(language.tag)
        // Dates and numbers formatted outside resources follow it too.
        Locale.setDefault(locale)
        if (language == AppLanguage.System) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    private fun stored(context: Context): AppLanguage =
        AppLanguage.fromTag(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
}
