package com.gerko.fourenote.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {
    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    suspend fun getById(id: String): Note? = dao.getById(id)

    suspend fun save(note: Note) = dao.upsert(note.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(note: Note) = dao.delete(note)
}
