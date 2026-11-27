package com.davidzhu.wantnote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class BoxNoteCount(val boxId: String, val noteCount: Int)

@Dao
interface NoteBoxDao {
    @Query("SELECT * FROM note_boxes ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<NoteBox>>

    @Query("SELECT * FROM note_boxes ORDER BY createdAt ASC")
    suspend fun getAll(): List<NoteBox>

    @Query("SELECT * FROM note_boxes WHERE id = :id")
    suspend fun getById(id: String): NoteBox?

    @Query("SELECT * FROM note_boxes WHERE name = :name")
    suspend fun getByName(name: String): NoteBox?

    /** 名字唯一，冲突时返回 -1（调用方按重名提示）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(box: NoteBox): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(boxes: List<NoteBox>)

    @Query("DELETE FROM note_boxes")
    suspend fun deleteAll()

    @Update
    suspend fun update(box: NoteBox)

    @Query("DELETE FROM note_boxes WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        SELECT boxId AS boxId, COUNT(*) AS noteCount FROM notes
        WHERE boxId != ''
        GROUP BY boxId
        """,
    )
    suspend fun noteCounts(): List<BoxNoteCount>
}
