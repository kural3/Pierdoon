package com.ku.pierdoon.ui.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager

/** System file dialogs and the clipboard. All of them block, so call them on the UI thread. */
object Files {
    /** The window dialogs belong to; set once it exists. */
    var owner: Frame? = null

    private val IMAGE_EXT = listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    /** Picks a picture with the native Open dialog; null if cancelled. */
    fun pickImage(title: String): File? {
        val dialog = FileDialog(owner, title, FileDialog.LOAD)
        // Windows' dialog ignores FilenameFilter; a wildcard list is what it understands.
        dialog.file = IMAGE_EXT.joinToString(";") { "*.$it" }
        dialog.setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in IMAGE_EXT }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name).takeIf { it.isFile }
    }

    /** Picks a folder; null if cancelled. */
    fun pickFolder(title: String, start: File? = null): File? {
        val chooser = withSystemLook { JFileChooser(start) }
        chooser.dialogTitle = title
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        return if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun clipboardText(): String? = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull()

    /** Swing dialogs in Windows' own look rather than Java's. */
    private fun <T> withSystemLook(block: () -> T): T {
        val previous = UIManager.getLookAndFeel()
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        return try {
            block().also { c -> if (c is java.awt.Component) SwingUtilities.updateComponentTreeUI(c) }
        } finally {
            runCatching { UIManager.setLookAndFeel(previous) }
        }
    }
}

/** "1.4 MB", as Android's Formatter.formatShortFileSize puts it. */
fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val units = listOf("КБ", "МБ", "ГБ")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value < 10) "%.1f %s".format(value, units[unit]) else "%.0f %s".format(value, units[unit])
}
