package com.ku.pierdoon

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.media.browse.MediaBrowser
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ku.pierdoon.playback.PlaybackService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Walks the library the way Android Auto does: through the platform's media browser, on a phone with music. */
@RunWith(AndroidJUnit4::class)
class CarBrowseTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var browser: MediaBrowser

    @Before
    fun connect() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, permission)
        val connected = CountDownLatch(1)
        runOnMain {
            browser = MediaBrowser(
                context,
                ComponentName(context, PlaybackService::class.java),
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() = connected.countDown()
                },
                null,
            )
            browser.connect()
        }
        assertTrue("connected", connected.await(10, TimeUnit.SECONDS))
    }

    @After
    fun disconnect() = runOnMain { browser.disconnect() }

    private fun runOnMain(block: () -> Unit) {
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            block()
            done.countDown()
        }
        done.await()
    }

    private fun children(id: String): List<MediaBrowser.MediaItem> {
        var items: List<MediaBrowser.MediaItem>? = null
        val done = CountDownLatch(1)
        runOnMain {
            browser.subscribe(id, object : MediaBrowser.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowser.MediaItem>) {
                    items = children
                    done.countDown()
                }

                override fun onError(parentId: String) = done.countDown()
            })
        }
        assertTrue("children of $id", done.await(15, TimeUnit.SECONDS))
        runOnMain { browser.unsubscribe(id) }
        return checkNotNull(items) { "error loading $id" }
    }

    @Test
    fun rootHasTheFourTabsAndAllowsSearch() {
        assertEquals(4, children(browser.root).size)
        assertTrue(browser.extras?.getBoolean("android.media.browse.SEARCH_SUPPORTED") == true)
    }

    @Test
    fun everyTabLeadsToPlayableTracksWithCovers() {
        var tracksFound = false
        for (tab in children(browser.root)) {
            assertTrue(tab.isBrowsable)
            val items = children(tab.mediaId!!)
            println("CAR ${tab.description.title}: ${items.size}")
            val leaf = if (items.firstOrNull()?.isBrowsable == true) {
                // Favourites come first among playlists and may well be empty.
                items.firstNotNullOfOrNull { folder -> children(folder.mediaId!!).takeIf { it.isNotEmpty() } }
            } else {
                items
            }
            // A new install has no playlists, and favourites can be empty; the other tabs have the music.
            val first = leaf?.firstOrNull() ?: continue
            assertTrue(first.isPlayable)
            val art = checkNotNull(first.description.iconUri) { "no cover" }
            assertTrue("$art", art.toString().startsWith("content://${context.packageName}.carart/"))
            context.contentResolver.openFileDescriptor(art, "r")!!.use { assertTrue(it.statSize > 0) }
            tracksFound = true
        }
        assertTrue(tracksFound)
    }

    private fun tracks() = children(children(browser.root).last().mediaId!!)

    private fun afterQueueChange(controller: MediaController, change: () -> Unit, done: () -> Unit) {
        controller.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                // The controller first shows what it was asked for; the service's answer has plain ids.
                if (controller.currentMediaItem?.mediaId?.toLongOrNull() != null) {
                    controller.removeListener(this)
                    done()
                }
            }
        })
        change()
    }

    @Test
    fun pickingATrackQueuesItsListFromThere() {
        val tracks = tracks()
        val picked = tracks[tracks.size / 2]
        val done = CountDownLatch(1)
        var count = 0
        var index = -1
        var id = ""
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        runOnMain {
            afterQueueChange(controller, { controller.setMediaItem(MediaItem.Builder().setMediaId(picked.mediaId!!).build()) }) {
                count = controller.mediaItemCount
                index = controller.currentMediaItemIndex
                id = controller.currentMediaItem!!.mediaId
                done.countDown()
            }
        }
        assertTrue("queue set", done.await(10, TimeUnit.SECONDS))
        runOnMain { controller.release() }
        assertEquals(tracks.size, count)
        assertEquals(tracks.size / 2, index)
        // In the player a track has the plain id the history and the queue store expect.
        assertEquals(picked.mediaId!!.substringAfterLast('/'), id)
    }

    @Test
    fun aSpokenRequestPlaysWhatItNames() {
        val title = tracks().first().description.title.toString()
        val done = CountDownLatch(1)
        var found = ""
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = MediaController.Builder(context, token).buildAsync().get(10, TimeUnit.SECONDS)
        val spoken = MediaItem.Builder()
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(title).build())
            .build()
        runOnMain {
            afterQueueChange(controller, { controller.setMediaItem(spoken) }) {
                found = controller.currentMediaItem!!.mediaMetadata.title.toString()
                done.countDown()
            }
        }
        assertTrue("queue set", done.await(10, TimeUnit.SECONDS))
        runOnMain { controller.release() }
        assertEquals(title, found)
    }
}
