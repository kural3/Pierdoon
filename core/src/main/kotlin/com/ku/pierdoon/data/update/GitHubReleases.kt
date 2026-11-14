package com.ku.pierdoon.data.update

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import com.ku.pierdoon.data.webUrl
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * The releases page both apps update from: one repository, each app looking only for its own
 * file (an APK, an .msi). Blocking; call it off the main thread.
 */
object GitHubReleases {
    private const val RELEASES = "https://api.github.com/repos/kural3/Pierdoon/releases?per_page=30"

    /**
     * The newest published release with a file ending in [extension] attached, its version
     * without [tagPrefix] ("android-", "desktop-") or "v"; null when there is none.
     */
    fun latest(extension: String, tagPrefix: String, userAgent: String): AppRelease? {
        val conn = open(RELEASES, "application/vnd.github+json", userAgent)
        val releases = try {
            JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
        for (i in 0 until releases.length()) {
            val o = releases.getJSONObject(i)
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) continue
            val assets = o.optJSONArray("assets") ?: continue
            val file = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(extension, ignoreCase = true) } ?: continue
            return AppRelease(
                version = o.optString("tag_name").removePrefix(tagPrefix).removePrefix("v"),
                notes = o.optString("body"),
                fileUrl = file.getString("browser_download_url"),
                fileSize = file.optLong("size"),
                sha256 = sha256Of(file.optString("digest")),
            )
        }
        return null
    }

    /**
     * Downloads [release]'s file into [target]. Cancellable between chunks: the "Cancel" button
     * stops it within one read. Checked against the SHA-256 GitHub lists for it, when it lists
     * one; a short or mismatching file is deleted and an [IOException] thrown.
     */
    suspend fun download(release: AppRelease, target: File, userAgent: String, onProgress: (Float?) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(release.fileUrl, "application/octet-stream", userAgent)
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.fileSize.takeIf { it > 0 }
            var read = 0L
            var lastReported: Int? = -1
            DigestInputStream(conn.inputStream, digest).use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        val percent = total?.let { (read * 100 / it).toInt() }
                        // Unknown size: reported once as null, not on every chunk.
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(total?.let { read.toFloat() / it })
                        }
                    }
                }
            }
            if (total != null && read != total) throw IOException("Truncated download")
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (release.sha256 != null && actual != release.sha256) throw IOException("Checksum mismatch")
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, accept: String, userAgent: String): HttpURLConnection {
        val conn = webUrl(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("User-Agent", userAgent)
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP ${conn.responseCode}")
        }
        return conn
    }
}

/** The hex out of "sha256:<hex>", as GitHub gives an asset's digest; null for anything else. */
fun sha256Of(digest: String): String? =
    digest.removePrefix("sha256:").lowercase()
        .takeIf { digest.startsWith("sha256:") && it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
