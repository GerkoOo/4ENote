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
import kotlinx.coroutines.launch

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

    /** The optional fingerprint/face/PIN lock in front of the whole app. */
    val appPassword: com.gerko.fourenote.data.security.AppPassword by lazy {
        com.gerko.fourenote.data.security.AppPassword(this)
    }

    val appLock: com.gerko.fourenote.data.security.AppLock by lazy {
        com.gerko.fourenote.data.security.AppLock(this)
    }

    /** Which locked notes are open in this session (see NoteLock). */
    val noteLock: com.gerko.fourenote.data.security.NoteLock by lazy {
        com.gerko.fourenote.data.security.NoteLock(this)
    }

    /** Looks for new versions in the background (see UpdateWatch). */
    val updateWatch: com.gerko.fourenote.data.update.UpdateWatch by lazy {
        com.gerko.fourenote.data.update.UpdateWatch(this, BuildConfig.VERSION_NAME)
    }

    /** The focus (Pomodoro) timer shown as a bar in the editor. */
    val focusTimer: com.gerko.fourenote.data.focus.FocusTimer by lazy { com.gerko.fourenote.data.focus.FocusTimer(this) }

    /** Scans taken from the home page, waiting for the note with their photo frame. */
    val scanInbox: ScanInbox by lazy { ScanInbox(java.io.File(filesDir, "inbox")) }

    val syncManager: SyncManager by lazy { SyncManager(this, repository, attachments, googleAuth, appScope, scanInbox) }

    override fun onCreate() {
        super.onCreate()
        // Notes that have been in Deleted notes for long enough go for good, even if the app
        // is never opened on the trash screen.
        appScope.launch { runCatching { repository.purgeExpiredTrash() } }
    }
}
