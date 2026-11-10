package com.davidzhu.wantnote.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["path"], unique = true)],
)
data class Tag(
    @PrimaryKey val id: String,
    val path: String,
    val color: Long,
    val createdAt: Long,
) {
    companion object {
        val PALETTE: List<Long> = listOf(
            0xFF5B8FF9L,
            0xFF5AD8A6L,
            0xFF5D7092L,
            0xFFF6BD16L,
            0xFFE8684AL,
            0xFF6DC8ECL,
            0xFF9270CAL,
            0xFFFF9D4DL,
            0xFF269A99L,
            0xFFBB8FCEL,
        )

        fun defaultColor(): Long = PALETTE.first()

        fun normalizePath(raw: String): String {
            return raw.split('/')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("/")
        }
    }
}

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index("tagId")],
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagId: String,
)
