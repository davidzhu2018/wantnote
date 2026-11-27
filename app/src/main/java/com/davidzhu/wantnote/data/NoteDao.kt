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
    @Relation(parentColumn = "boxId", entityColumn = "id")
    val noteBox: NoteBox?,
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
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Transaction
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAllWithBlocks(): Flow<List<NoteWithBlocks>>

    @Transaction
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAllWithDetails(): Flow<List<NoteWithDetails>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getByIdWithDetails(id: String): NoteWithDetails?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<Note?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): Note?

    @Query("SELECT id FROM notes WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>

    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getAll(): List<Note>

    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%'
        ORDER BY pinned DESC, updatedAt DESC
        """,
    )
    fun search(q: String): Flow<List<Note>>

    // NOTE: never use OnConflictStrategy.REPLACE for notes. REPLACE deletes the
    // existing row before inserting; note_tags has ON DELETE CASCADE, so the
    // note's tag associations would be silently wiped on every save. Insert
    // with IGNORE first and UPDATE in place when the row already exists.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(note: Note): Long

    @Query(
        """
        UPDATE notes SET title = :title, body = :body, mdPath = :mdPath,
            createdAt = :createdAt, updatedAt = :updatedAt, boxId = :boxId,
            locationName = :locationName, locationAddress = :locationAddress,
            locationLat = :locationLat, locationLng = :locationLng,
            pinned = :pinned, starred = :starred
        WHERE id = :id
        """,
    )
    suspend fun updateColumns(
        id: String,
        title: String,
        body: String,
        mdPath: String,
        createdAt: Long,
        updatedAt: Long,
        boxId: String,
        locationName: String,
        locationAddress: String,
        locationLat: Double,
        locationLng: Double,
        pinned: Boolean,
        starred: Boolean,
    )

    @Transaction
    suspend fun upsert(note: Note) {
        if (insertIfAbsent(note) == -1L) {
            updateColumns(
                note.id, note.title, note.body, note.mdPath, note.createdAt, note.updatedAt, note.boxId,
                note.locationName, note.locationAddress, note.locationLat, note.locationLng, note.pinned, note.starred,
            )
        }
    }

    @Transaction
    suspend fun upsertAll(notes: List<Note>) {
        notes.forEach { note ->
            if (insertIfAbsent(note) == -1L) {
                updateColumns(
                    note.id, note.title, note.body, note.mdPath, note.createdAt, note.updatedAt, note.boxId,
                    note.locationName, note.locationAddress, note.locationLat, note.locationLng, note.pinned, note.starred,
                )
            }
        }
    }

    /** 0.18.0：更新笔记的地点 POI（传空串即清除位置）。 */
    @Query(
        """
        UPDATE notes SET locationName = :locationName, locationAddress = :locationAddress,
            locationLat = :locationLat, locationLng = :locationLng
        WHERE id = :noteId
        """,
    )
    suspend fun updateLocation(
        noteId: String,
        locationName: String,
        locationAddress: String,
        locationLat: Double,
        locationLng: Double,
    )

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :noteId")
    suspend fun setPinned(noteId: String, pinned: Boolean)

    @Query("UPDATE notes SET starred = :starred WHERE id = :noteId")
    suspend fun setStarred(noteId: String, starred: Boolean)

    @Query("UPDATE notes SET reviewed = :reviewed WHERE id = :noteId")
    suspend fun setReviewed(noteId: String, reviewed: Boolean)

    @Query("UPDATE notes SET reviewed = 1 WHERE id IN (:noteIds)")
    suspend fun markReviewedBatch(noteIds: List<String>)

    /** 0.10.0：把一篇笔记分配到卡片盒（boxId 传空串 = 取消分配）。 */
    @Query("UPDATE notes SET boxId = :boxId WHERE id = :noteId")
    suspend fun assignBox(noteId: String, boxId: String)

    /** 0.10.0：删除卡片盒时的安全迁移——整盒笔记移入目标盒。 */
    @Query("UPDATE notes SET boxId = :toBoxId WHERE boxId = :fromBoxId")
    suspend fun moveNotes(fromBoxId: String, toBoxId: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
