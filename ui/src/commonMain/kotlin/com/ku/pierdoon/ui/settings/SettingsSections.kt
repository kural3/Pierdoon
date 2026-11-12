package com.ku.pierdoon.ui.settings

import com.ku.pierdoon.ui.resources.Res
import com.ku.pierdoon.ui.resources.settings_group_app
import com.ku.pierdoon.ui.resources.settings_group_music
import com.ku.pierdoon.ui.resources.settings_group_sound
import org.jetbrains.compose.resources.StringResource

/** The headings the sections are listed under. */
enum class SectionGroup(val title: StringResource) {
    Music(Res.string.settings_group_music),
    Sound(Res.string.settings_group_sound),
    App(Res.string.settings_group_app),
}

/**
 * A page of settings, opened from the list on the Settings tab, in the order they're listed.
 * Sync is the phone's (with the computer), System the computer's (Windows itself).
 */
enum class SettingsSection(val group: SectionGroup, val onPhone: Boolean = true, val onComputer: Boolean = true) {
    Library(SectionGroup.Music), Lyrics(SectionGroup.Music), Downloads(SectionGroup.Music),
    Playback(SectionGroup.Sound), Equalizer(SectionGroup.Sound),
    Appearance(SectionGroup.App),
    Sync(SectionGroup.App, onComputer = false),
    System(SectionGroup.App, onPhone = false),
    Backup(SectionGroup.App), Updates(SectionGroup.App), Beta(SectionGroup.App), About(SectionGroup.App);

    /** Its place on its own app's list, which picks its icon's shape: Sync and System share one. */
    val iconIndex: Int
        get() = when {
            this == System -> Sync.ordinal
            ordinal > System.ordinal -> ordinal - 1
            else -> ordinal
        }

    companion object {
        val phone: List<SettingsSection> = entries.filter { it.onPhone }
        val computer: List<SettingsSection> = entries.filter { it.onComputer }
    }
}
