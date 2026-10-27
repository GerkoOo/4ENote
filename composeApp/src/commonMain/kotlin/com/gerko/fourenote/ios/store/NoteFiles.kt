package com.gerko.fourenote.ios.store

/**
 * The folder where notes are kept, one file per note. The desktop build uses a folder under the
 * user's home; the iPad build uses the app's Documents folder, so notes are included in backups.
 */
expect class NoteFiles() {
    /** File names in the notes folder, in no particular order. */
    fun list(): List<String>

    /** The file's text, or null if it is missing or cannot be read. */
    fun read(name: String): String?

    /** Writes [text] to [name], replacing any existing file atomically. */
    fun write(name: String, text: String)

    /** Removes [name] if it exists. */
    fun delete(name: String)
}
