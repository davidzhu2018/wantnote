package com.davidzhu.wantnote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY sortOrder ASC")
    suspend fun getByNoteId(noteId: String): List<NoteBlock>

    @Query("SELECT * FROM note_blocks WHERE type = 'TASK'")
    fun observeAllTasks(): Flow<List<NoteBlock>>

    @Query("SELECT * FROM note_blocks WHERE type = 'TASK'")
    suspend fun getAllTasks(): List<NoteBlock>

    @Query("SELECT * FROM note_blocks")
    suspend fun getAll(): List<NoteBlock>

    @Query("SELECT * FROM note_blocks WHERE mediaPath != ''")
    suspend fun getAllWithMedia(): List<NoteBlock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(blocks: List<NoteBlock>)

    @Query("DELETE FROM note_blocks WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("DELETE FROM note_blocks")
    suspend fun deleteAll()
}
