package com.ku.pierdoon.ui.edit

import androidx.compose.runtime.Composable
import com.ku.pierdoon.data.Song
import com.ku.pierdoon.data.metadata.MetadataCandidate
import com.ku.pierdoon.data.metadata.TrackEdit
import com.ku.pierdoon.ui.components.PickedImage
import kotlinx.coroutines.flow.Flow
import java.io.File

/** What [EditSongScreen] asks of the app: each one's view model does the work its own way. */
interface TrackEditor : PlatformTrackEditor {
    /** The file's format takes tags and the app may write it. */
    suspend fun canWriteTags(song: Song): Boolean

    /** A title, artist, album or cover saved in the app for this track. */
    fun hasInAppEdits(songId: Long): Boolean

    suspend fun saveInApp(song: Song, edit: TrackEdit)

    /** Writes the edit into the file itself; false if that failed. */
    suspend fun saveToFile(song: Song, edit: TrackEdit): Boolean

    /** Drops what was saved in the app for the track. */
    suspend fun resetEdits(song: Song)

    /** Online matches; each catalogue's answers are added as they come. */
    fun searchMetadata(query: String, durationMs: Long): Flow<List<MetadataCandidate>>

    /** The picked picture made into a cover file waiting to be saved; null if it isn't a picture. */
    suspend fun importCover(image: PickedImage): File?

    suspend fun downloadCover(url: String): File?
}

/** What only one app's editor has (Android asks the user before a file of theirs is written). */
expect interface PlatformTrackEditor

/**
 * Asks the system whether the app may write the track's file; [onResult] gets the answer.
 * Only Android asks: on a computer the answer is yes at once.
 */
@Composable
internal expect fun rememberWriteConsent(editor: TrackEditor, onResult: (granted: Boolean) -> Unit): (Song) -> Unit
