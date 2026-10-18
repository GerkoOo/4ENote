package com.gerko.fourenote

import android.app.Application
import com.gerko.fourenote.data.AppDatabase
import com.gerko.fourenote.data.NoteRepository

class FourENoteApp : Application() {
    val repository: NoteRepository by lazy {
        NoteRepository(AppDatabase.getInstance(this).noteDao())
    }
}
