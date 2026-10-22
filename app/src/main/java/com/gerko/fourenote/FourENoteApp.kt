package com.gerko.fourenote

import android.app.Application
import com.gerko.fourenote.data.AppDatabase
import com.gerko.fourenote.data.NoteRepository
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.attachments.BackgroundLoader
import com.gerko.fourenote.data.ink.InkStore
import com.gerko.fourenote.data.sync.SyncManager
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
        NoteRepository(AppDatabase.getInstance(this).noteDao(), attachments, InkStore(java.io.File(filesDir, "ink")), appScope)
    }

    val syncManager: SyncManager by lazy { SyncManager(this, repository, attachments, appScope) }
}
