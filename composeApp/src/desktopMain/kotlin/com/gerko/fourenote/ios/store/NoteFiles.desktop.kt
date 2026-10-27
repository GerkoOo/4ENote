package com.gerko.fourenote.ios.store

import java.io.File

/**
 * Notes live in ~/.4enote-ios-notes on the desktop, which only exists so Linux can compile and
 * test the iPad app's UI. Tests pass their own folder through the internal constructor.
 */
actual class NoteFiles actual constructor() {
    private val folder: File = File(System.getProperty("user.home"), ".4enote-ios-notes")

    internal constructor(folder: File) : this() {
        this.folderOverride = folder
    }

    private var folderOverride: File? = null

    private val dir: File get() = folderOverride ?: folder

    actual fun list(): List<String> {
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { it.isFile }.map { it.name }
    }

    actual fun read(name: String): String? = try {
        File(dir, name).takeIf { it.isFile }?.readText()
    } catch (e: Exception) {
        null
    }

    actual fun write(name: String, text: String) {
        dir.mkdirs()
        // Write to a temp file first, then rename, so a crash mid-write cannot leave a half note.
        val target = File(dir, name)
        val temp = File(dir, "$name.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
    }

    actual fun delete(name: String) {
        File(dir, name).delete()
    }
}
