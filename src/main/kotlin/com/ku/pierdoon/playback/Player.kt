package com.ku.pierdoon.playback

import com.ku.pierdoon.data.Song

/** One entry of the queue: a song and the key that identifies this entry while it moves. */
data class MediaItem(val song: Song, val queueKey: Long = Song.newQueueKey()) {
    val mediaId: String get() = song.id.toString()
}

/**
 * The queue and transport, with Media3's semantics where the app relied on them: a list of
 * items, a current index, shuffle as a separate play order, repeat off / one / all.
 *
 * Used from the UI thread only; [AudioEngine] reports back on the same thread.
 */
class Player {

    enum class Event { Timeline, Transition, IsPlaying, Position, Shuffle, Repeat, State }

    fun interface Listener {
        fun onEvents(player: Player, events: Set<Event>)
    }

    private val listeners = ArrayList<Listener>()
    fun addListener(l: Listener) { listeners += l }
    fun removeListener(l: Listener) { listeners -= l }

    private val items = ArrayList<MediaItem>()
    /** Indices of [items] in shuffled play order; rebuilt when shuffle is switched on or the list changes. */
    private var shuffleOrder = IntArray(0)
    private var tokenSeq = 0L
    /** Token handed to the engine for the item now loaded, and the index it stands for. */
    private var loadedToken = -1L
    private var nextToken = -1L
    private var nextIndex = -1

    var currentMediaItemIndex: Int = -1
        private set

    val mediaItemCount: Int get() = items.size
    fun getMediaItemAt(index: Int): MediaItem = items[index]
    val currentMediaItem: MediaItem? get() = items.getOrNull(currentMediaItemIndex)

    var playbackState: Int = STATE_IDLE
        private set

    private var wantsPlay = false

    /** What the user wants: keeps "playing" across a track switch, unlike [isPlaying]. */
    var playWhenReady: Boolean
        get() = wantsPlay
        set(value) {
            if (wantsPlay == value) return
            wantsPlay = value
            if (value) {
                if (playbackState == STATE_ENDED) seekToDefaultPosition()
                if (playbackState == STATE_IDLE) prepare()
                engine.play()
            } else {
                engine.pause()
            }
            emit(Event.IsPlaying)
        }

    val isPlaying: Boolean get() = playWhenReady && playbackState == STATE_READY

    var shuffleModeEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) reshuffle(keepFirst = currentMediaItemIndex)
            updateNext()
            emit(Event.Shuffle, Event.Timeline)
        }

    var repeatMode: Int = REPEAT_MODE_OFF
        set(value) {
            if (field == value) return
            field = value
            updateNext()
            emit(Event.Repeat)
        }

    /** Pause when the current item ends instead of going on (the sleep timer's "end of track"). */
    var pauseAtEndOfMediaItems: Boolean = false
        set(value) {
            field = value
            updateNext()
        }

    var volume: Float
        get() = engine.volume
        set(value) { engine.volume = value.coerceIn(0f, 1f) }

    var skipSilenceEnabled: Boolean
        get() = engine.skipSilence
        set(value) { engine.skipSilence = value }

    /** Item duration in ms, or [TIME_UNSET] when unknown. */
    val duration: Long get() = currentMediaItem?.song?.durationMs?.takeIf { it > 0 } ?: TIME_UNSET

    private var lastPositionMs = 0L

    val currentPosition: Long
        get() {
            val p = engine.position()
            if (p != null && p.first == loadedToken) lastPositionMs = p.second
            return lastPositionMs.coerceAtLeast(0)
        }

    private val engine = AudioEngine(object : AudioEngine.Listener {
        override fun onTrackStarted(token: Long) {
            errorStreak = 0
            if (token == nextToken && nextIndex in items.indices) {
                // The engine moved on by itself (gapless): follow it.
                currentMediaItemIndex = nextIndex
                loadedToken = token
                lastPositionMs = 0
                nextToken = -1
                nextIndex = -1
                updateNext()
                emit(Event.Transition, Event.Timeline)
            }
        }

        override fun onEnded(token: Long) {
            if (token != loadedToken) return
            if (pauseAtEndOfMediaItems) {
                val following = nextIndexFor(currentMediaItemIndex, repeatMode)
                playWhenReadyInternal(false)
                if (following >= 0) {
                    currentMediaItemIndex = following
                    load(play = false, startMs = 0)
                    emit(Event.Transition, Event.Timeline)
                }
                emit(Event.IsPlaying)
                onPausedAtEnd?.invoke()
                return
            }
            playbackState = STATE_ENDED
            playWhenReadyInternal(false)
            emit(Event.State, Event.IsPlaying)
        }

        override fun onError(token: Long, error: Throwable) {
            if (token != loadedToken && token != nextToken) return
            // A broken file is skipped, as Media3 would after an error the app recovers from.
            val following = nextIndexFor(currentMediaItemIndex, if (repeatMode == REPEAT_MODE_ONE) REPEAT_MODE_ALL else repeatMode)
            if (following >= 0 && following != currentMediaItemIndex && errorStreak < items.size) {
                errorStreak++
                currentMediaItemIndex = following
                load(play = playWhenReady, startMs = 0)
                emit(Event.Transition, Event.Timeline)
            } else {
                errorStreak = 0
                playbackState = STATE_IDLE
                playWhenReadyInternal(false)
                emit(Event.State, Event.IsPlaying)
            }
        }
    })

    private var errorStreak = 0

    /** Called when the player paused itself at the end of an item ([pauseAtEndOfMediaItems]). */
    var onPausedAtEnd: (() -> Unit)? = null

    /** Without telling the engine: it has stopped on its own already. */
    private fun playWhenReadyInternal(value: Boolean) {
        wantsPlay = value
    }

    // Queue changes ------------------------------------------------------------------------

    fun setMediaItems(newItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        items.clear()
        items += newItems
        currentMediaItemIndex = if (items.isEmpty()) -1 else startIndex.coerceIn(0, items.lastIndex)
        lastPositionMs = startPositionMs.coerceAtLeast(0)
        if (shuffleModeEnabled) reshuffle(keepFirst = currentMediaItemIndex)
        if (items.isEmpty()) {
            stopEngine()
        } else {
            pendingStartMs = lastPositionMs
            playbackState = STATE_IDLE
            loadedToken = -1
        }
        emit(Event.Timeline, Event.Transition, Event.State)
    }

    private var pendingStartMs = 0L

    /** Opens the current item; until then nothing is decoded. */
    fun prepare() {
        if (currentMediaItemIndex !in items.indices) return
        if (playbackState == STATE_READY && loadedToken >= 0) return
        load(play = playWhenReady, startMs = pendingStartMs)
        pendingStartMs = 0
    }

    fun play() { playWhenReady = true }
    fun pause() { playWhenReady = false }

    fun stop() {
        lastPositionMs = currentPosition
        pendingStartMs = lastPositionMs
        stopEngine()
        emit(Event.State, Event.IsPlaying)
    }

    fun addMediaItem(item: MediaItem) = addMediaItems(items.size, listOf(item))
    fun addMediaItem(index: Int, item: MediaItem) = addMediaItems(index, listOf(item))
    fun addMediaItems(newItems: List<MediaItem>) = addMediaItems(items.size, newItems)

    fun addMediaItems(index: Int, newItems: List<MediaItem>) {
        if (newItems.isEmpty()) return
        val at = index.coerceIn(0, items.size)
        items.addAll(at, newItems)
        if (currentMediaItemIndex >= at && currentMediaItemIndex >= 0) currentMediaItemIndex += newItems.size
        if (shuffleModeEnabled) insertIntoShuffle(at, newItems.size)
        updateNext()
        emit(Event.Timeline)
    }

    fun removeMediaItem(index: Int) {
        if (index !in items.indices) return
        val wasCurrent = index == currentMediaItemIndex
        items.removeAt(index)
        if (shuffleModeEnabled) removeFromShuffle(index)
        when {
            items.isEmpty() -> {
                currentMediaItemIndex = -1
                stopEngine()
            }
            index < currentMediaItemIndex -> currentMediaItemIndex--
            wasCurrent -> {
                // The current track went: the one after it takes over, as in Media3.
                currentMediaItemIndex = currentMediaItemIndex.coerceAtMost(items.lastIndex)
                if (playbackState != STATE_IDLE) load(play = playWhenReady, startMs = 0)
            }
        }
        updateNext()
        emit(Event.Timeline, *(if (wasCurrent) arrayOf(Event.Transition) else emptyArray()))
    }

    fun moveMediaItem(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val item = items.removeAt(from)
        items.add(to, item)
        currentMediaItemIndex = when (currentMediaItemIndex) {
            from -> to
            in (from + 1)..to -> currentMediaItemIndex - 1
            in to until from -> currentMediaItemIndex + 1
            else -> currentMediaItemIndex
        }
        if (shuffleModeEnabled) reshuffle(keepFirst = currentMediaItemIndex)
        updateNext()
        emit(Event.Timeline)
    }

    /** Same entry, new tags: nothing is interrupted. */
    fun replaceMediaItem(index: Int, item: MediaItem) {
        if (index !in items.indices) return
        items[index] = item
        emit(Event.Timeline, *(if (index == currentMediaItemIndex) arrayOf(Event.Transition) else emptyArray()))
    }

    // Transport ----------------------------------------------------------------------------

    fun seekTo(positionMs: Long) {
        if (currentMediaItemIndex < 0) return
        lastPositionMs = positionMs.coerceAtLeast(0)
        if (playbackState == STATE_ENDED) {
            load(play = playWhenReady, startMs = lastPositionMs)
        } else if (loadedToken >= 0) {
            engine.seek(lastPositionMs)
        } else {
            pendingStartMs = lastPositionMs
        }
        emit(Event.Position)
    }

    fun seekTo(index: Int, positionMs: Long) {
        if (index !in items.indices) return
        currentMediaItemIndex = index
        lastPositionMs = positionMs
        if (playbackState == STATE_IDLE) {
            pendingStartMs = positionMs
            loadedToken = -1
        } else {
            load(play = playWhenReady, startMs = positionMs)
        }
        emit(Event.Transition, Event.Timeline, Event.Position)
    }

    fun seekToDefaultPosition() = seekTo(currentMediaItemIndex.coerceAtLeast(0), 0)
    fun seekToDefaultPosition(index: Int) {
        seekTo(index, 0)
        if (playbackState == STATE_IDLE) prepare()
    }

    fun seekToNext() {
        val n = nextIndexFor(currentMediaItemIndex, if (repeatMode == REPEAT_MODE_ONE) REPEAT_MODE_ALL else repeatMode)
        if (n >= 0) seekToDefaultPosition(n)
    }

    /** Back to the start of the track, or to the previous one within the first seconds. */
    fun seekToPrevious() {
        if (currentPosition > PREVIOUS_THRESHOLD_MS) return seekTo(0)
        val p = previousIndexFor(currentMediaItemIndex)
        if (p >= 0) seekToDefaultPosition(p) else seekTo(0)
    }

    fun release() = engine.release()

    fun setOutputDevice(name: String?) = engine.setDevice(name)

    // Play order ---------------------------------------------------------------------------

    /** Items in play order (shuffle-aware), for walking the queue from the current one. */
    fun nextIndexFor(index: Int, repeat: Int): Int {
        if (items.isEmpty() || index !in items.indices) return -1
        if (repeat == REPEAT_MODE_ONE) return index
        val order = playOrder()
        val pos = order.indexOf(index)
        return when {
            pos < order.lastIndex -> order[pos + 1]
            repeat == REPEAT_MODE_ALL -> order[0]
            else -> -1
        }
    }

    private fun previousIndexFor(index: Int): Int {
        if (items.isEmpty() || index !in items.indices) return -1
        val order = playOrder()
        val pos = order.indexOf(index)
        return when {
            pos > 0 -> order[pos - 1]
            repeatMode == REPEAT_MODE_ALL -> order.last()
            else -> -1
        }
    }

    private fun playOrder(): IntArray =
        if (shuffleModeEnabled && shuffleOrder.size == items.size) shuffleOrder else IntArray(items.size) { it }

    private fun reshuffle(keepFirst: Int) {
        val rest = items.indices.filter { it != keepFirst }.shuffled()
        shuffleOrder = (if (keepFirst in items.indices) listOf(keepFirst) + rest else rest).toIntArray()
    }

    private fun insertIntoShuffle(at: Int, count: Int) {
        val shifted = shuffleOrder.map { if (it >= at) it + count else it }.toMutableList()
        val pos = shifted.indexOf(currentMediaItemIndex).coerceAtLeast(0)
        // New items play after the current one, in a random place among what's left.
        (at until at + count).forEach { idx -> shifted.add((pos + 1..shifted.size).random(), idx) }
        shuffleOrder = shifted.toIntArray()
    }

    private fun removeFromShuffle(index: Int) {
        shuffleOrder = shuffleOrder.filter { it != index }.map { if (it > index) it - 1 else it }.toIntArray()
    }

    // Engine -------------------------------------------------------------------------------

    private fun load(play: Boolean, startMs: Long) {
        val item = currentMediaItem ?: return
        val token = ++tokenSeq
        loadedToken = token
        nextToken = -1
        nextIndex = -1
        lastPositionMs = startMs
        playbackState = STATE_READY
        engine.load(item.song.file, token, startMs, play)
        updateNext()
        emit(Event.State)
    }

    /** Tells the engine what follows, so it can switch without a gap. */
    private fun updateNext() {
        if (loadedToken < 0) return
        val n = if (pauseAtEndOfMediaItems) -1 else nextIndexFor(currentMediaItemIndex, repeatMode)
        if (n < 0) {
            if (nextIndex != -1) engine.setNext(null, -1)
            nextIndex = -1
            nextToken = -1
            return
        }
        val token = ++tokenSeq
        nextIndex = n
        nextToken = token
        engine.setNext(items[n].song.file, token)
    }

    private fun stopEngine() {
        engine.stop()
        loadedToken = -1
        nextToken = -1
        nextIndex = -1
        playbackState = STATE_IDLE
        playWhenReadyInternal(false)
    }

    private fun emit(vararg events: Event) {
        val set = events.toSet()
        listeners.toList().forEach { it.onEvents(this, set) }
    }

    companion object {
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val REPEAT_MODE_ALL = 2

        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4

        const val TIME_UNSET = Long.MIN_VALUE + 1
        private const val PREVIOUS_THRESHOLD_MS = 3_000L
    }
}
