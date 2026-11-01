package com.ku.pierdoon.playback

/** How long the change of cassette between two tracks takes. */
const val CASSETTE_SOUND_MS = 2_000L

/** How long the tape takes to rewind at the end of a side. */
const val CASSETTE_REWIND_MS = 2_650L

const val CASSETTE_SOUND_RATE = 44_100

/** The file holds nothing but the plain header and the samples. */
private const val WAV_HEADER_BYTES = 44

private object CassetteResource

/**
 * A cassette being changed: eject, door, the tape going in, door, a key. Interleaved 16-bit stereo
 * at [CASSETTE_SOUND_RATE]. "cassette tape action.wav" by cognito perceptu (CC0), the rewind and
 * the quiet between the clicks taken out:
 * https://freesound.org/people/cognito%20perceptu/sounds/98330/
 */
fun cassetteChangeSound(): ShortArray = readSound("/sounds/cassette_change.wav")

/** The tape running back at speed: the rewind taken out of that same recording, same format. */
fun cassetteRewindSound(): ShortArray = readSound("/sounds/cassette_rewind.wav")

private fun readSound(resource: String): ShortArray {
    val stream = checkNotNull(CassetteResource::class.java.getResourceAsStream(resource)) { "No $resource" }
    val bytes = stream.use { it.readBytes() }
    return ShortArray((bytes.size - WAV_HEADER_BYTES) / 2) { i ->
        val at = WAV_HEADER_BYTES + i * 2
        ((bytes[at].toInt() and 0xFF) or (bytes[at + 1].toInt() shl 8)).toShort()
    }
}
