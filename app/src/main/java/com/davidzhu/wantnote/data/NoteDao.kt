package com.davidzhu.wantnote.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class NoteWithBlocks(
    @Embedded val note: Note,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val blocks: List<NoteBlock>,
)

data class NoteWithDetails(
    @Embedded val note: Note,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val blocks: List<NoteBlock>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = NoteTagCrossRef::class,
            parentColumn = "noteId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<Tag>,
) {
    val firstMedia: NoteBlock?
        get() = blocks.sortedBy { it.sortOrder }.firstOrNull { it.isMedia }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Transaction
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAllWithBlocks(): Flow<List<NoteWithBlocks>>

    @Transaction
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAllWithDetails(): Flow<List<NoteWithDetails>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getByIdWithDetails(id: String): NoteWithDetails?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<Note?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): Note?

    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%'
        ORDER BY updatedAt DESC
        """,
    )
    fun search(q: String): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)
}
