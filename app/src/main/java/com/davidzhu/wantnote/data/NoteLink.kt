package com.davidzhu.wantnote.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Persistent, bidirectional note-to-note relation.
 *
 * The relation is keyed by stable note IDs (never titles), so renaming a note
 * does not invalidate any link. Both foreign keys cascade on delete, which
 * guarantees a deleted note cannot leave dangling relations behind.
 */
@Entity(
    tableName = "note_links",
    primaryKeys = ["sourceNoteId", "targetNoteId"],
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["sourceNoteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["targetNoteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceNoteId"), Index("targetNoteId")],
)
data class NoteLink(
    val sourceNoteId: String,
    val targetNoteId: String,
    val createdAt: Long,
)

@Dao
interface NoteLinkDao {
    @Query("SELECT * FROM note_links")
    fun observeAll(): Flow<List<NoteLink>>

    @Query("SELECT * FROM note_links")
    suspend fun getAll(): List<NoteLink>

    @Query("SELECT * FROM note_links WHERE sourceNoteId = :noteId")
    suspend fun getOutgoing(noteId: String): List<NoteLink>

    @Query("SELECT * FROM note_links WHERE targetNoteId = :noteId")
    suspend fun getIncoming(noteId: String): List<NoteLink>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(links: List<NoteLink>)

    @Query("DELETE FROM note_links WHERE sourceNoteId = :noteId")
    suspend fun deleteOutgoing(noteId: String)

    @Query("DELETE FROM note_links WHERE sourceNoteId = :noteId AND targetNoteId = :targetNoteId")
    suspend fun deleteOne(noteId: String, targetNoteId: String)

    @Query("DELETE FROM note_links")
    suspend fun deleteAll()
}

/** A link together with the non-deleted note it points at. */
data class NoteLinkView(
    val noteId: String,
    val direction: LinkDirection,
)

enum class LinkDirection {
    OUTGOING,
    INCOMING,
}
