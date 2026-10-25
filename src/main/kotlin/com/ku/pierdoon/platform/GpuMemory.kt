package com.ku.pierdoon.platform

import org.jetbrains.skia.DirectContext
import java.awt.Component
import java.awt.Container
import java.awt.Window

/**
 * Caps Skia's cache of GPU resources. By default it keeps up to 256 MB of textures, and every
 * cover ever drawn stays uploaded; with integrated graphics that memory is the process's own.
 *
 * Skiko doesn't expose its context, so it's reached by reflection: the window's SkiaLayer, its
 * redrawer, the redrawer's context handler. The limit is set under the redrawer's draw lock, the
 * one its render thread holds while drawing. If any of it doesn't match (another renderer, a
 * changed skiko), nothing happens and Skia keeps its default.
 */
object GpuMemory {
    /** Plenty for the covers of a screenful of albums and the open player. */
    const val NORMAL_BYTES = 64L * 1024 * 1024

    /** Hidden in the tray: nothing is drawn, everything cached can go. */
    const val HIDDEN_BYTES = 0L

    /** True once the limit is set; false when the context isn't there yet (no frame drawn) or can't be found. */
    fun limit(window: Window, bytes: Long): Boolean = runCatching {
        val layer = find(window) ?: return false
        val redrawer = layer.javaClass.getMethod("getRedrawer\$skiko").invoke(layer) ?: return false
        val handler = field(redrawer, "contextHandler") ?: return false
        val lock = field(redrawer, "drawLock") ?: return false
        val getContext = generateSequence<Class<*>>(handler.javaClass) { it.superclass }
            .firstNotNullOfOrNull { c -> c.declaredMethods.firstOrNull { it.name == "getContext" && it.parameterCount == 0 } }
            ?: return false
        getContext.isAccessible = true
        synchronized(lock) {
            val context = getContext.invoke(handler) as? DirectContext ?: return false
            context.resourceCacheLimit = bytes
        }
        true
    }.getOrDefault(false)

    private fun find(c: Component): Component? {
        if (c.javaClass.name == "org.jetbrains.skiko.SkiaLayer" || c.javaClass.superclass?.name == "org.jetbrains.skiko.SkiaLayer") return c
        if (c is Container) c.components.forEach { child -> find(child)?.let { return it } }
        return null
    }

    private fun field(obj: Any, name: String): Any? =
        generateSequence<Class<*>>(obj.javaClass) { it.superclass }
            .firstNotNullOfOrNull { c -> c.declaredFields.firstOrNull { it.name == name } }
            ?.apply { isAccessible = true }
            ?.get(obj)
}
