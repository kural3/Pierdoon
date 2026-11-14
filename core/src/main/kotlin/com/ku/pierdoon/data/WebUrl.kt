package com.ku.pierdoon.data

import java.net.URI
import java.net.URISyntaxException
import java.net.URL

/**
 * [link] as a [URL], made through [URI] (the `URL(String)` constructor is deprecated, and checked
 * nothing). Links from the web don't always keep to the rules a URI has: a space, a `|`, letters
 * past ASCII. Those are percent-encoded first; what's already encoded (`%20`) is left as it is.
 *
 * @throws java.net.MalformedURLException when it isn't an absolute link even then.
 */
fun webUrl(link: String): URL {
    val uri = try {
        URI(link)
    } catch (_: URISyntaxException) {
        try {
            URI(escapeUrl(link))
        } catch (e: URISyntaxException) {
            throw java.net.MalformedURLException(e.message)
        }
    }
    if (!uri.isAbsolute) throw java.net.MalformedURLException("Not an absolute link: $link")
    return uri.toURL()
}

/** Percent-encodes, as UTF-8, every character a URI may not hold as it is; a `%` stays (it's taken as encoded already). */
internal fun escapeUrl(link: String): String = buildString {
    for (b in link.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xFF
        if (c in 0x21..0x7E && c.toChar() !in UNSAFE) append(c.toChar())
        else append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
    }
}

private const val UNSAFE = "\"<>\\^`{|}"
private const val HEX = "0123456789ABCDEF"
