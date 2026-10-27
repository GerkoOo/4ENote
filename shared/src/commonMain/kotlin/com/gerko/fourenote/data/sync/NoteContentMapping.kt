package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.Note

/** The note as it travels in a note file (see [NoteFileFormat]). */
fun Note.toContent(): NoteContent = NoteContent(
    id = id,
    title = title,
    inkData = inkData,
    createdAt = createdAt,
    updatedAt = updatedAt,
    folderId = folderId,
    locked = locked,
    rev = rev,
    remotePdfId = remotePdfId,
)
