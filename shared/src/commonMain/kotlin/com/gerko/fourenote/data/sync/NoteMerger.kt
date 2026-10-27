package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.Note

/**
 * Three-way merge of a note edited on two devices since their last common sync. The merge itself
 * lives in the shared module ([NoteContentMerger]), so the iPad app merges the same way; this is
 * the Android side of it, over [Note].
 */
object NoteMerger {
    /**
     * [remoteFirst] breaks ties in ordering (see [NoteContentMerger.merge]): devices merging each
     * other's files pass opposite values, so they agree.
     */
    fun merge(base: Note?, local: Note, remote: Note, remoteFirst: Boolean = false): Note {
        val merged = NoteContentMerger.merge(base?.toContent(), local.toContent(), remote.toContent(), remoteFirst)
        return local.copy(
            title = merged.title,
            folderId = merged.folderId,
            locked = merged.locked,
            inkData = merged.inkData,
            createdAt = merged.createdAt,
            updatedAt = merged.updatedAt,
            rev = merged.rev,
        )
    }

    /**
     * Everything in either note; where both hold the same item, [first]'s version. The base for
     * merging a file from a device that had merged several files this device has too.
     */
    fun union(first: Note, second: Note): Note {
        val merged = NoteContentMerger.union(first.toContent(), second.toContent())
        return first.copy(
            title = merged.title,
            folderId = merged.folderId,
            locked = merged.locked,
            inkData = merged.inkData,
            createdAt = merged.createdAt,
            updatedAt = merged.updatedAt,
            rev = merged.rev,
        )
    }
}
