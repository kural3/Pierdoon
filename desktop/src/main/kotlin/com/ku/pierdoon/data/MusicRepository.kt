package com.ku.pierdoon.data

import com.ku.pierdoon.platform.AppDirs
import com.ku.pierdoon.platform.MediaProbe
import com.ku.pierdoon.platform.writeAtomically
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The library: every audio file under the music folders, with its tags.
 *
 * Reading tags is the slow part, so what was read is kept in an index keyed by path, size and
 * modification time; a rescan only opens files that are new or changed.
 */
class MusicRepository {

    private val overrides = TagOverrides.get()
    private val indexFile = File(AppDirs.cache, "library-index.json")
    private val lock = Any()
    private var index: MutableMap<String, Indexed>? = null

    private class Indexed(
        val size: Long,
        val modified: Long,
        val created: Long,
        val tags: com.ku.pierdoon.platform.ProbedTags,
    )

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val settings = SettingsStore.settings.value
        val folders = settings.musicFolders.map(::File).filter { it.isDirectory }
        val excluded = settings.excludedFolders.map { File(it).absolutePath.trimEnd('\\', '/').lowercase() }.toSet()
        val files = folders.flatMap { walk(it, excluded) }.distinctBy { it.absolutePath.lowercase() }
        val idx = loaded()
        val seen = HashSet<String>(files.size)
        val songs = coroutineScope {
            files.chunked(64).map { chunk ->
                async(Dispatchers.IO) { chunk.mapNotNull { f -> song(f, idx, withOverrides = true).also { seen += f.absolutePath } } }
            }.awaitAll().flatten()
        }
        val changed = synchronized(lock) {
            val stale = idx.keys.filter { it !in seen }
            stale.forEach(idx::remove)
            dirty || stale.isNotEmpty()
        }
        if (changed) saveIndex()
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        songs.filter { it.durationMs <= 0 || it.durationMs >= MIN_DURATION_MS }
            .sortedWith { a, b -> collator.compare(a.title, b.title) }
    }

    /** The track as its file tags it, without the user's in-app edits. */
    suspend fun fileTags(song: Song): Song? = withContext(Dispatchers.IO) {
        val f = song.file
        if (!f.isFile) return@withContext null
        synchronized(lock) { loaded().remove(f.absolutePath) }
        song(f, loaded(), withOverrides = false)
    }

    @Volatile private var dirty = false

    private fun song(file: File, idx: MutableMap<String, Indexed>, withOverrides: Boolean): Song? {
        val attrs = runCatching { Files.readAttributes(file.toPath(), BasicFileAttributes::class.java) }.getOrNull()
            ?: return null
        val size = attrs.size()
        val modified = attrs.lastModifiedTime().toMillis()
        val key = file.absolutePath
        val cached = synchronized(lock) { idx[key] }?.takeIf { it.size == size && it.modified == modified }
        val entry = cached ?: run {
            val tags = MediaProbe.tags(file) ?: return null
            Indexed(size, modified, attrs.creationTime().toMillis(), tags).also {
                synchronized(lock) { idx[key] = it }
                dirty = true
            }
        }
        val t = entry.tags
        val folder = file.parentFile?.absolutePath.orEmpty()
        val song = Song(
            id = Song.idFor(file),
            title = t.title.ifBlank { file.nameWithoutExtension },
            artist = t.artist,
            album = t.album,
            albumId = Song.albumIdFor(t.album, t.albumArtist.ifBlank { "" }, folder),
            durationMs = t.durationMs,
            dateAdded = minOf(entry.created, entry.modified) / 1000,
            path = file.absolutePath,
        )
        return if (withOverrides) overrides.apply(song) else song
    }

    private fun walk(root: File, excluded: Set<String>): List<File> =
        root.walkTopDown()
            .onEnter { !it.isHidden && it.name != "\$RECYCLE.BIN" && it.absolutePath.lowercase() !in excluded }
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            .toList()

    private fun loaded(): MutableMap<String, Indexed> = synchronized(lock) {
        index ?: readIndex().also { index = it }
    }

    private fun readIndex(): MutableMap<String, Indexed> {
        val out = HashMap<String, Indexed>()
        if (!indexFile.exists()) return out
        runCatching {
            val arr = JSONArray(indexFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out[o.getString("p")] = Indexed(
                    size = o.getLong("s"),
                    modified = o.getLong("m"),
                    created = o.optLong("c"),
                    tags = com.ku.pierdoon.platform.ProbedTags(
                        title = o.optString("t"),
                        artist = o.optString("a"),
                        album = o.optString("l"),
                        albumArtist = o.optString("aa"),
                        durationMs = o.optLong("d"),
                    ),
                )
            }
        }
        return out
    }

    private fun saveIndex() {
        val arr = JSONArray()
        synchronized(lock) {
            dirty = false
            index?.forEach { (path, e) ->
                arr.put(
                    JSONObject().put("p", path).put("s", e.size).put("m", e.modified).put("c", e.created)
                        .put("t", e.tags.title).put("a", e.tags.artist).put("l", e.tags.album)
                        .put("aa", e.tags.albumArtist).put("d", e.tags.durationMs)
                )
            }
        }
        writeAtomically(indexFile, arr.toString())
    }

    companion object {
        const val MIN_DURATION_MS = 10_000L
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "aif", "aiff",
            "ape", "wv", "mka", "webm", "dsf", "alac", "mpc", "tta",
        )
    }
}
