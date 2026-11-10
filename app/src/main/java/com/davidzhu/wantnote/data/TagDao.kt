package com.davidzhu.wantnote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY path COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY path COLLATE NOCASE ASC")
    suspend fun getAll(): List<Tag>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: String): Tag?

    @Query("SELECT * FROM tags WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): Tag?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: Tag)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN note_tags ON note_tags.tagId = tags.id
        WHERE note_tags.noteId = :noteId
        ORDER BY tags.path COLLATE NOCASE ASC
        """,
    )
    fun observeForNote(noteId: String): Flow<List<Tag>>

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN note_tags ON note_tags.tagId = tags.id
        WHERE note_tags.noteId = :noteId
        ORDER BY tags.path COLLATE NOCASE ASC
        """,
    )
    suspend fun getForNote(noteId: String): List<Tag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCrossRef(ref: NoteTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCrossRefs(refs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearNoteTags(noteId: String)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun removeNoteTag(noteId: String, tagId: String)

    @Query("SELECT * FROM note_tags")
    suspend fun getAllCrossRefs(): List<NoteTagCrossRef>
}
