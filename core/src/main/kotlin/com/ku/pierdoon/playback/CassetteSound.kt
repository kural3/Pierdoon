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

/** The rewind's parts: whir from the start, a stretch of it that loops cleanly, and the slowing down to its stop. */
private const val REWIND_LOOP_FROM_MS = 300
private const val REWIND_TAIL_FROM_MS = 2_250
private const val REWIND_JOIN_MS = 40
/** A 10-minute side rewinds in about 1.7 s, a half-hour one in 3 s, an hour or more in 4.5 s. */
private const val REWIND_BASE_MS = 1_000L
private const val REWIND_MS_PER_SIDE_SECOND = 1.1
private const val REWIND_MIN_MS = 1_500L
private const val REWIND_MAX_MS = 4_500L

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

/**
 * How long running the tape back takes after a side of [sideLengthMs]: a short side a moment, a
 * long one a few seconds. The recording itself is [CASSETTE_REWIND_MS].
 */
fun cassetteRewindMs(sideLengthMs: Long): Long =
    (REWIND_BASE_MS + (sideLengthMs / 1000 * REWIND_MS_PER_SIDE_SECOND).toLong()).coerceIn(REWIND_MIN_MS, REWIND_MAX_MS)

/**
 * The rewind made [ms] long: the steady whir cut short or run on (looped, the joins blended), then
 * the slowing down and the stop as recorded.
 */
fun cassetteRewindSound(ms: Long): ShortArray {
    val rewind = cassetteRewindSound()
    val tail = rewind.slice(REWIND_TAIL_FROM_MS, Int.MAX_VALUE / 1000)
    // The whir runs on under the first moments of the tail, where the two are blended.
    val bodyMs = (ms - cassetteSoundMs(tail)).coerceAtLeast(REWIND_JOIN_MS * 2L).toInt() + REWIND_JOIN_MS
    var body = rewind.slice(0, minOf(bodyMs, REWIND_TAIL_FROM_MS))
    val loop = rewind.slice(REWIND_LOOP_FROM_MS, REWIND_TAIL_FROM_MS)
    while (cassetteSoundMs(body) < bodyMs) body = joined(body, loop, REWIND_JOIN_MS)
    return joined(body.copyOf(minOf(body.size, frameAt(bodyMs).toInt())), tail, REWIND_JOIN_MS)
}

/** [a] then [b], the last [fadeMs] of one blended into the first of the other. */
private fun joined(a: ShortArray, b: ShortArray, fadeMs: Int): ShortArray {
    val overlap = minOf(frameAt(fadeMs).toInt(), a.size, b.size) / CHANNELS * CHANNELS
    val out = ShortArray(a.size + b.size - overlap)
    a.copyInto(out)
    val start = a.size - overlap
    for (i in 0 until overlap) {
        val t = (i / CHANNELS).toFloat() / (overlap / CHANNELS)
        out[start + i] = (a[start + i] * (1 - t) + b[i] * t).toInt().toShort()
    }
    b.copyInto(out, a.size, overlap)
    return out
}

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
