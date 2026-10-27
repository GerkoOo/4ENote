package com.gerko.fourenote.ios.store

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * Notes live in Documents/notes on the iPad, so they are included in the app's backups and are
 * visible to the Files app when the app opts in.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class NoteFiles actual constructor() {
    private val dir: String = run {
        val documents = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).firstOrNull() as? String ?: NSTemporaryDirectory()
        "$documents/notes"
    }

    actual fun list(): List<String> {
        val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, null) ?: return emptyList()
        return names.mapNotNull { it as? String }
    }

    actual fun read(name: String): String? =
        NSString.stringWithContentsOfFile("$dir/$name", NSUTF8StringEncoding, null)

    actual fun write(name: String, text: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        // writeToFile:atomically:YES writes a temp file and renames it, so a crash mid-write
        // cannot leave a half note.
        NSString.create(string = text).writeToFile("$dir/$name", true, NSUTF8StringEncoding, null)
    }

    actual fun delete(name: String) {
        NSFileManager.defaultManager.removeItemAtPath("$dir/$name", null)
    }
}
