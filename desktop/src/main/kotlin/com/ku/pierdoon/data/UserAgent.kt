package com.ku.pierdoon.data

import com.ku.pierdoon.BuildConfig

/**
 * Sent to every web service the app talks to (LRCLIB, Deezer, MusicBrainz, GitHub).
 * MusicBrainz asks clients for a name, a version and a way to reach the author.
 */
val USER_AGENT = "Pierdoon-Desktop/${BuildConfig.VERSION_NAME} (https://github.com/kural3/Pierdoon)"
