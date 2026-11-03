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

/** The deck's keys as they're pressed: each press a touch tighter or looser, softer or harder. */
fun cassetteKeyVariety(play: Boolean) = SoundVariety(cassetteKeySound(play), pitchSpread = 0.04f, gainSpreadDb = 1.5f)

/** Spins of the tape: each a little faster or slower, and so a little higher and shorter or lower and longer. */
fun cassetteWindVariety() = SoundVariety(cassetteWindSound(), pitchSpread = 0.06f, gainSpreadDb = 0f)

/**
 * One deck sound played again and again without sounding like a recording: every take is the same
 * sound sped up or slowed down (pitch and length together, as a motor running faster) by up to
 * [pitchSpread], and made louder or softer by up to [gainSpreadDb]. Two takes in a row never come
 * out alike.
 */
class SoundVariety(
    private val sound: ShortArray,
    private val pitchSpread: Float,
    private val gainSpreadDb: Float,
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
) {
    private var lastPitch = 1f

    fun next(): ShortArray {
        var pitch: Float
        // A third of the range apart from the last one, so a quick double press is heard as two.
        do {
            pitch = 1f + (random.nextFloat() * 2 - 1) * pitchSpread
        } while (pitchSpread > 0f && kotlin.math.abs(pitch - lastPitch) < pitchSpread / 3)
        lastPitch = pitch
        val db = (random.nextFloat() * 2 - 1) * gainSpreadDb
        return resampled(pitch, Math.pow(10.0, db / 20.0).toFloat())
    }

    private fun resampled(pitch: Float, gain: Float): ShortArray {
        val frames = sound.size / CHANNELS
        val outFrames = (frames / pitch).toInt()
        val out = ShortArray(outFrames * CHANNELS)
        for (i in 0 until outFrames) {
            val at = i * pitch
            val a = at.toInt().coerceAtMost(frames - 1)
            val b = (a + 1).coerceAtMost(frames - 1)
            val part = at - a
            for (c in 0 until CHANNELS) {
                val x = sound[a * CHANNELS + c] + (sound[b * CHANNELS + c] - sound[a * CHANNELS + c]) * part
                out[i * CHANNELS + c] = (x * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
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
