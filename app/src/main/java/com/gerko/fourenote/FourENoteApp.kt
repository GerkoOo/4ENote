package com.gerko.fourenote

import android.app.Application
import com.gerko.fourenote.data.AppDatabase
import com.gerko.fourenote.data.NoteRepository
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.attachments.BackgroundLoader
import com.gerko.fourenote.data.ink.InkStore
import com.gerko.fourenote.data.scan.ScanInbox
import com.gerko.fourenote.data.sync.SyncManager
import com.gerko.fourenote.data.sync.google.GoogleAuth
import com.gerko.fourenote.ui.editor.ToolPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FourENoteApp : Application() {
    /** Lives as long as the app, so a sync finishes even after its screen closes. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val attachments: AttachmentStore by lazy { AttachmentStore(this) }

    val backgrounds: BackgroundLoader by lazy { BackgroundLoader(attachments) }

    val toolPreferences: ToolPreferences by lazy { ToolPreferences(this) }

    val repository: NoteRepository by lazy {
        val db = AppDatabase.getInstance(this)
        NoteRepository(db.noteDao(), attachments, InkStore(java.io.File(filesDir, "ink")), appScope, db.folderDao())
    }

    val googleAuth: GoogleAuth by lazy { GoogleAuth(this) }

    /** The focus (Pomodoro) timer shown as a bar in the editor. */
    val focusTimer: com.gerko.fourenote.data.focus.FocusTimer by lazy { com.gerko.fourenote.data.focus.FocusTimer(this) }

    /** Scans taken from the home page, waiting for the note with their photo frame. */
    val scanInbox: ScanInbox by lazy { ScanInbox(java.io.File(filesDir, "inbox")) }

    val syncManager: SyncManager by lazy { SyncManager(this, repository, attachments, googleAuth, appScope, scanInbox) }
}
