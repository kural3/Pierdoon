package com.ku.pierdoon.platform

import com.ku.pierdoon.data.AppLanguage
import java.util.Locale

/**
 * The interface language. Compose resources pick strings by the JVM's default locale, so a
 * language chosen in the settings becomes the default; "system" puts back the one Windows gave.
 * The UI has to be composed again afterwards to read its strings anew.
 */
object InterfaceLanguage {
    private val system: Locale = Locale.getDefault()

    fun apply(language: AppLanguage) {
        Locale.setDefault(if (language == AppLanguage.System) system else Locale.forLanguageTag(language.tag))
    }
}
