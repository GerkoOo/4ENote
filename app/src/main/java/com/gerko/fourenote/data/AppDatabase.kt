package com.gerko.fourenote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class, Folder::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun folderDao(): FolderDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Adds the sync bookkeeping columns. Existing notes start dirty so they upload on first sync. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE notes ADD COLUMN remoteVersion TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN remotePdfId TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN syncedSnapshot TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Folders on the home page, and which folder each note is in. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN folderId TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS folders (id TEXT NOT NULL, name TEXT NOT NULL, parentId TEXT, " +
                        "updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, PRIMARY KEY(id))",
                )
            }
        }

        /** Deleted notes: when each note was moved to the trash (null: not in it). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN trashedAt INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "4enote.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}
