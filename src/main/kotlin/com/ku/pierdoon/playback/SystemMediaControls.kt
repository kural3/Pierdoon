package com.ku.pierdoon.playback

import com.ku.pierdoon.platform.ErrorLog
import com.ku.pierdoon.platform.Images
import com.ku.pierdoon.platform.WinShell
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.Executors
import javax.swing.SwingUtilities

/**
 * The Windows media overlay (the panel by the volume flyout, the lock screen, the taskbar's media
 * controls): what's playing, its cover, and play / pause / next / previous from there.
 *
 * WinRT's SystemMediaTransportControls, reached from a desktop window through its COM interop
 * interface and called through JNA by vtable slot. Every call runs on one thread of its own,
 * inside the multithreaded apartment. If any of it isn't there, the app goes on without it and
 * the media keys are caught by [MediaKeys] as before.
 */
object SystemMediaControls {

    /** Buttons pressed in the overlay, or media keys Windows routed to this session. */
    interface Buttons {
        fun play()
        fun pause()
        fun next()
        fun previous()
        fun stop()
    }

    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "media-controls").apply { isDaemon = true } }

    private var smtc: Pointer? = null
    private var updater: Pointer? = null
    private var music: Pointer? = null
    private var streamRefs: Pointer? = null
    private var lastCover: Pair<Long, Long>? = null
    private var buttons: Buttons? = null

    /** Whether the overlay is in use; read by the UI thread to decide on [MediaKeys]. */
    @Volatile var active = false
        private set

    /** Hooks the overlay up to [window]. @return true once it works. */
    fun install(window: java.awt.Window, buttons: Buttons): Boolean {
        if (!WinShell.isWindows) return false
        this.buttons = buttons
        val hwnd = runCatching { Native.getComponentPointer(window) }.getOrNull() ?: return false
        return runCatching { thread.submit<Boolean> { setUp(hwnd) }.get() }.getOrElse {
            ErrorLog.record("media controls", it)
            false
        }
    }

    /** Shows [song] (or clears the overlay with null). Cheap to call on every player event. */
    fun update(song: NowPlaying?, playing: Boolean, cover: (NowPlaying) -> ByteArray?) {
        if (!active) return
        thread.execute {
            runCatching { show(song, playing, cover) }.onFailure { ErrorLog.record("media controls", it) }
        }
    }

    private fun setUp(hwnd: Pointer): Boolean {
        combase.RoInitialize(RO_INIT_MULTITHREADED)
        val interop = activationFactory("Windows.Media.SystemMediaTransportControls", IID_SMTC_INTEROP)
        try {
            val out = PointerByReference()
            check(call(interop, 6, hwnd, IID_SMTC, out), "GetForWindow")
            smtc = out.value
        } finally {
            release(interop)
        }
        val s = smtc!!
        // Slots of ISystemMediaTransportControls: put_IsEnabled 11, put_IsPlayEnabled 13,
        // put_IsStopEnabled 15, put_IsPauseEnabled 17, put_IsPreviousEnabled 25, put_IsNextEnabled 27.
        for (slot in intArrayOf(11, 13, 15, 17, 25, 27)) check(call(s, slot, 1), "enable $slot")
        check(call(s, 32, ButtonHandler.self, LongByReference()), "add_ButtonPressed")

        val u = PointerByReference()
        check(call(s, 8, u), "get_DisplayUpdater")
        updater = u.value
        check(call(updater!!, 7, MEDIA_TYPE_MUSIC), "put_Type")
        val m = PointerByReference()
        check(call(updater!!, 12, m), "get_MusicProperties")
        music = m.value
        streamRefs = runCatching { activationFactory("Windows.Storage.Streams.RandomAccessStreamReference", IID_STREAM_REF_STATICS) }.getOrNull()
        active = true
        return true
    }

    private fun show(song: NowPlaying?, playing: Boolean, cover: (NowPlaying) -> ByteArray?) {
        val s = smtc ?: return
        val u = updater ?: return
        val m = music ?: return
        if (song == null) {
            call(u, 16) // ClearAll
            call(u, 7, MEDIA_TYPE_MUSIC)
            call(u, 17)
            call(s, 7, STATUS_STOPPED)
            lastCover = null
            return
        }
        withHString(song.title) { call(m, 7, it) }
        withHString(song.artist) { call(m, 11, it) }
        val coverKey = song.songId to song.albumId
        if (coverKey != lastCover) {
            lastCover = coverKey
            setThumbnail(u, cover(song))
        }
        call(u, 17) // Update
        call(s, 7, if (playing) STATUS_PLAYING else STATUS_PAUSED)
    }

    /** A small JPEG, handed over as an in-memory stream: no temp files, nothing to clean up. */
    private fun setThumbnail(updater: Pointer, bytes: ByteArray?) {
        val statics = streamRefs
        val jpeg = bytes?.let(Images::decode)?.let { Images.jpeg(Images.scaled(it, THUMB_PX)) }
        if (statics == null || jpeg == null) {
            call(updater, 11, Pointer.NULL)
            return
        }
        val stream = shlwapi.SHCreateMemStream(jpeg, jpeg.size) ?: return
        try {
            val ras = PointerByReference()
            check(shcore.CreateRandomAccessStreamOverStream(stream, 0, IID_RANDOM_ACCESS_STREAM, ras), "stream")
            try {
                val ref = PointerByReference()
                check(call(statics, 8, ras.value, ref), "CreateFromStream")
                call(updater, 11, ref.value)
                release(ref.value)
            } finally {
                release(ras.value)
            }
        } finally {
            release(stream)
        }
    }

    private fun onButton(button: Int) {
        val b = buttons ?: return
        SwingUtilities.invokeLater {
            when (button) {
                BUTTON_PLAY -> b.play()
                BUTTON_PAUSE -> b.pause()
                BUTTON_STOP -> b.stop()
                BUTTON_NEXT -> b.next()
                BUTTON_PREVIOUS -> b.previous()
            }
        }
    }

    // COM plumbing -------------------------------------------------------------------------

    private interface Combase : Library {
        fun RoInitialize(type: Int): Int
        fun WindowsCreateString(source: WString, length: Int, string: PointerByReference): Int
        fun WindowsDeleteString(string: Pointer?): Int
        fun RoGetActivationFactory(classId: Pointer, iid: Guid.GUID, factory: PointerByReference): Int
    }

    private interface Shcore : Library {
        fun CreateRandomAccessStreamOverStream(stream: Pointer, options: Int, iid: Guid.GUID, out: PointerByReference): Int
    }

    private interface Shlwapi : Library {
        fun SHCreateMemStream(data: ByteArray, size: Int): Pointer?
    }

    private val combase by lazy { Native.load("combase", Combase::class.java) }
    private val shcore by lazy { Native.load("shcore", Shcore::class.java) }
    private val shlwapi by lazy { Native.load("shlwapi", Shlwapi::class.java) }

    private fun activationFactory(className: String, iid: Guid.GUID): Pointer = withHString(className) { name ->
        val out = PointerByReference()
        check(combase.RoGetActivationFactory(name!!, iid, out), "factory $className")
        out.value
    }

    private fun <T> withHString(text: String, block: (Pointer?) -> T): T {
        val out = PointerByReference()
        // An empty HSTRING is a null pointer, which is what WindowsCreateString gives for "".
        check(combase.WindowsCreateString(WString(text), text.length, out), "string")
        try {
            return block(out.value)
        } finally {
            combase.WindowsDeleteString(out.value)
        }
    }

    /** Calls slot [index] of [obj]'s vtable with [obj] as `this`. */
    private fun call(obj: Pointer, index: Int, vararg args: Any?): Int {
        val vtable = obj.getPointer(0)
        val fn = Function.getFunction(vtable.getPointer(index.toLong() * Native.POINTER_SIZE))
        return fn.invokeInt(arrayOf(obj, *args))
    }

    private fun release(obj: Pointer?) {
        if (obj != null) call(obj, 2)
    }

    private fun check(hr: Int, what: String) {
        if (hr < 0) throw IllegalStateException("$what failed: 0x%08x".format(hr))
    }

    // JNA calls these through their interface method, so they're public interfaces of their own.
    interface QueryInterfaceFn : Callback { fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int }
    interface RefCountFn : Callback { fun invoke(self: Pointer): Int }
    interface InvokeFn : Callback { fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int }

    /**
     * The ButtonPressed handler, a COM object of our own: a pointer to a vtable of
     * QueryInterface, AddRef, Release, Invoke. It lives as long as the process, so the
     * reference count is only for show.
     */
    private object ButtonHandler {
        private val known = listOf(IID_UNKNOWN, IID_AGILE, IID_BUTTON_HANDLER).map { it.memoryLayout() }

        private val queryInterface = object : QueryInterfaceFn {
            override fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int {
                val asked = riid.getByteArray(0, 16)
                return if (known.any { it.contentEquals(asked) }) {
                    out.setPointer(0, self)
                    S_OK
                } else {
                    out.setPointer(0, Pointer.NULL)
                    E_NOINTERFACE
                }
            }
        }
        private val refCount = object : RefCountFn {
            override fun invoke(self: Pointer): Int = 1
        }
        private val onInvoke = object : InvokeFn {
            override fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int {
                if (args == null) return S_OK
                val button = IntByReference()
                if (runCatching { call(args, 6, button) }.getOrDefault(-1) >= 0) onButton(button.value)
                return S_OK
            }
        }

        private val vtable = Memory(4L * Native.POINTER_SIZE).apply {
            listOf<Callback>(queryInterface, refCount, refCount, onInvoke).forEachIndexed { i, cb ->
                setPointer(i.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(cb))
            }
        }

        val self: Pointer = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, vtable) }
    }

    /** As COM passes it (Data1-3 little-endian); GUID.toByteArray() would give the string's order. */
    private fun Guid.GUID.memoryLayout(): ByteArray = also { it.write() }.pointer.getByteArray(0, 16)

    private val IID_SMTC_INTEROP = Guid.GUID.fromString("{ddb0472d-c911-4a1f-86d9-dc3d71a95f5a}")
    private val IID_SMTC = Guid.GUID.fromString("{99fa3ff4-1742-42a6-902e-087d41f965ec}")
    private val IID_STREAM_REF_STATICS = Guid.GUID.fromString("{857309dc-3fbf-4e7d-986f-ef3b1a07a964}")
    private val IID_RANDOM_ACCESS_STREAM = Guid.GUID.fromString("{905a0fe1-bc53-11df-8c49-001e4fc686da}")
    private val IID_UNKNOWN = Guid.GUID.fromString("{00000000-0000-0000-c000-000000000046}")
    private val IID_AGILE = Guid.GUID.fromString("{94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90}")
    /** TypedEventHandler<SystemMediaTransportControls, SystemMediaTransportControlsButtonPressedEventArgs>. */
    private val IID_BUTTON_HANDLER = Guid.GUID.fromString("{0557e996-7b23-5bae-aa81-ea0d671143a4}")

    private const val RO_INIT_MULTITHREADED = 1
    private const val S_OK = 0
    private const val E_NOINTERFACE = 0x80004002.toInt()
    private const val MEDIA_TYPE_MUSIC = 1
    private const val STATUS_STOPPED = 2
    private const val STATUS_PLAYING = 3
    private const val STATUS_PAUSED = 4
    private const val BUTTON_PLAY = 0
    private const val BUTTON_PAUSE = 1
    private const val BUTTON_STOP = 2
    private const val BUTTON_NEXT = 6
    private const val BUTTON_PREVIOUS = 7
    private const val THUMB_PX = 300
}
