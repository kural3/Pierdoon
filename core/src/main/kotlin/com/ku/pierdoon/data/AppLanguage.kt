package com.ku.pierdoon.data

/** Language of the interface. [tag] is a BCP 47 language tag; empty for the system's. */
enum class AppLanguage(val tag: String) {
    System(""),
    Russian("ru"),
    English("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag && it != System } ?: System
    }
}

/** Longest crossfade the settings offer, in seconds. */
const val MAX_CROSSFADE_SECONDS = 12
