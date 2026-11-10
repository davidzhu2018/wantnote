package com.davidzhu.wantnote.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NoteRepository(private val dao: NoteDao) {
    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    fun observeById(id: String): Flow<Note?> = dao.observeById(id)

    fun search(query: String): Flow<List<Note>> = dao.search(query)

    suspend fun getById(id: String): Note? = dao.getById(id)

    suspend fun createBlank(): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = "",
            body = "",
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(note)
        return note
    }

    suspend fun save(id: String, title: String, body: String): Note? {
        val existing = dao.getById(id) ?: return null
        val updated = existing.copy(
            title = title,
            body = body,
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(updated)
        return updated
    }

    suspend fun deleteIfBlank(id: String): Boolean {
        val existing = dao.getById(id) ?: return false
        if (existing.title.isBlank() && existing.body.isBlank()) {
            dao.delete(id)
            return true
        }
        return false
    }
}
