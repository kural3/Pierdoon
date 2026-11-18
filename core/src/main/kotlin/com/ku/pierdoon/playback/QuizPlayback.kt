package com.ku.pierdoon.playback

/**
 * Set while the quiz has the player: its snippets aren't listening, so they stay out of the
 * recently played, the play log and the statistics.
 */
object QuizPlayback {
    @Volatile
    var active: Boolean = false
}
