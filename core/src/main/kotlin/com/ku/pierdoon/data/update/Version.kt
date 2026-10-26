package com.ku.pierdoon.data.update

/**
 * "1.10" > "1.9"; missing parts count as 0, so "1.2" == "1.2.0".
 * A pre-release comes before its release ("1.3-beta" < "1.3"), so a beta install is still
 * offered the final version. Two pre-releases of the same version aren't ranked: no offer
 * beats a guess that could be a downgrade.
 *
 * Shared by both apps' updaters, and tested on its own.
 */
fun isNewerVersion(candidate: String, current: String): Boolean {
    fun numbers(v: String) = v.substringBefore('-').split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = numbers(candidate)
    val b = numbers(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return '-' in current && '-' !in candidate
}
