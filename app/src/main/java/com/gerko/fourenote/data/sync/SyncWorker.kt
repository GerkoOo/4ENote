package com.gerko.fourenote.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gerko.fourenote.FourENoteApp

/** A sync Android runs once a connection is back, even when 4ENote isn't open (see SyncManager.syncWhenOnline). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val synced = (applicationContext as FourENoteApp).syncManager.syncNow()
        return if (synced) Result.success() else Result.retry()
    }

    companion object {
        const val NAME = "sync-when-online"
    }
}
