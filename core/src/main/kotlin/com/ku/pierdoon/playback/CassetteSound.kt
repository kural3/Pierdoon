package com.ku.pierdoon.playback

/** How long turning the cassette over at the end of side A takes. */
const val CASSETTE_SOUND_MS = 2_000L

/** How long the tape takes to rewind from the end of side B to the start of side A. */
const val CASSETTE_REWIND_MS = 2_650L

/** How long the spin to the next or previous track takes ([cassetteWindSound]'s length). */
const val CASSETTE_WIND_MS = 725L

const val CASSETTE_SOUND_RATE = 44_100

/** The file holds nothing but the plain header and the samples. */
private const val WAV_HEADER_BYTES = 44

private const val CHANNELS = 2

/** Where the steady whir of the rewind recording is, and where it stops with a clunk. */
private const val WIND_FROM_MS = 300
private const val WIND_TO_MS = 950
private const val WIND_STOP_FROM_MS = 2_575
private const val WIND_FADE_MS = 30

/** The keys in the change recording: the one pressed last ("play"), and the sharp one early on. */
private const val PLAY_KEY_FROM_MS = 1_852
private const val PLAY_KEY_TO_MS = 2_000
private const val PLAY_KEY_GAIN = 1.5f
private const val STOP_KEY_FROM_MS = 572
private const val STOP_KEY_TO_MS = 720
private const val KEY_FADE_MS = 40

private object CassetteResource

/**
 * A cassette being turned over: eject, door, the tape going in, door, a key. Interleaved 16-bit
 * stereo at [CASSETTE_SOUND_RATE]. "cassette tape action.wav" by cognito perceptu (CC0), the rewind
 * and the quiet between the clicks taken out:
 * https://freesound.org/people/cognito%20perceptu/sounds/98330/
 */
fun cassetteChangeSound(): ShortArray = readSound("/sounds/cassette_change.wav")

/** The tape running back at speed: the rewind taken out of that same recording, same format. */
fun cassetteRewindSound(): ShortArray = readSound("/sounds/cassette_rewind.wav")

/** A short spin of the tape to the next or previous track: a piece of the rewind, ending in its stop. */
fun cassetteWindSound(): ShortArray {
    val rewind = cassetteRewindSound()
    val whir = rewind.slice(WIND_FROM_MS, WIND_TO_MS)
    val stop = rewind.slice(WIND_STOP_FROM_MS, (rewind.size / CHANNELS * 1000L / CASSETTE_SOUND_RATE).toInt())
    whir.fadeIn(WIND_FADE_MS)
    whir.fadeOut(WIND_FADE_MS)
    return whir + stop
}

/** A key of the deck going down: "play" when [play], "stop" otherwise. Same format. */
fun cassetteKeySound(play: Boolean): ShortArray {
    val change = cassetteChangeSound()
    return if (play) {
        change.slice(PLAY_KEY_FROM_MS, PLAY_KEY_TO_MS).apply {
            scale(PLAY_KEY_GAIN)
            fadeOut(KEY_FADE_MS)
        }
    } else {
        change.slice(STOP_KEY_FROM_MS, STOP_KEY_TO_MS).apply { fadeOut(KEY_FADE_MS) }
    }
}

/** How long [samples] (interleaved stereo at [CASSETTE_SOUND_RATE]) play for. */
fun cassetteSoundMs(samples: ShortArray): Long = samples.size / CHANNELS * 1000L / CASSETTE_SOUND_RATE

private fun frameAt(ms: Int) = ms.toLong() * CASSETTE_SOUND_RATE / 1000 * CHANNELS

private fun ShortArray.slice(fromMs: Int, toMs: Int): ShortArray =
    copyOfRange(frameAt(fromMs).toInt().coerceIn(0, size), frameAt(toMs).toInt().coerceIn(0, size))

private fun ShortArray.scale(gain: Float) {
    for (i in indices) this[i] = (this[i] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}

private fun ShortArray.fadeIn(ms: Int) {
    val n = frameAt(ms).toInt().coerceAtMost(size)
    for (i in 0 until n) this[i] = (this[i] * (i / CHANNELS).toFloat() / (n / CHANNELS)).toInt().toShort()
}

private fun ShortArray.fadeOut(ms: Int) {
    val n = frameAt(ms).toInt().coerceAtMost(size)
    for (i in 0 until n) {
        val at = size - n + i
        this[at] = (this[at] * ((n - i) / CHANNELS).toFloat() / (n / CHANNELS)).toInt().toShort()
    }
}

private fun readSound(resource: String): ShortArray {
    val stream = checkNotNull(CassetteResource::class.java.getResourceAsStream(resource)) { "No $resource" }
    val bytes = stream.use { it.readBytes() }
    return ShortArray((bytes.size - WAV_HEADER_BYTES) / 2) { i ->
        val at = WAV_HEADER_BYTES + i * 2
        ((bytes[at].toInt() and 0xFF) or (bytes[at + 1].toInt() shl 8)).toShort()
    }
}
