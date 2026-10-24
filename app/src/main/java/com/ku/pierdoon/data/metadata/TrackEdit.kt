package com.ku.pierdoon.data.metadata

import java.io.File

/** What happens to a track's cover when an edit is saved. */
sealed interface CoverEdit {
    data object Keep : CoverEdit
    data object Remove : CoverEdit
    /** [file] is a JPEG already scaled for storing (see [com.ku.pierdoon.data.writeScaledJpeg]). */
    data class Replace(val file: File) : CoverEdit
}

data class TrackEdit(
    val title: String,
    val artist: String,
    val album: String,
    val cover: CoverEdit,
)

/** Where an edit is saved: the app's own records, or the audio file's tags. */
enum class SaveTarget { App, File }
