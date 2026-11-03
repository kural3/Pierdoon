package com.ku.pierdoon.data.download

/** What the user asked for. Neither is converted: both are the site's own audio, as is. */
enum class DownloadFormat {
    /** AAC in .m4a: plays and takes tags everywhere. */
    M4a,

    /** Opus, YouTube's best audio; repackaged from WebM into .opus without re-encoding. */
    Opus,
}
