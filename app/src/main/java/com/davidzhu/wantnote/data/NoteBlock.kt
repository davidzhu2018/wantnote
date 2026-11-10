package com.davidzhu.wantnote.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BlockType {
    PARAGRAPH,
    TASK,
    ORDERED,
    UNORDERED,
    DIVIDER,
    IMAGE,
    VIDEO,
}

enum class BlockFontSize(val label: String, val sp: Int) {
    SMALL("小", 14),
    NORMAL("中", 18),
    LARGE("大", 22),
    XLARGE("特大", 28),
    ;

    fun next(): BlockFontSize {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    companion object {
        fun fromStorage(value: String?): BlockFontSize {
            return entries.firstOrNull { it.name == value } ?: NORMAL
        }
    }
}

@Entity(
    tableName = "note_blocks",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index("type")],
)
data class NoteBlock(
    @PrimaryKey val id: String,
    val noteId: String,
    val type: String,
    val text: String,
    val indent: Int,
    val checked: Boolean,
    val fontSize: String,
    val mediaPath: String,
    val sortOrder: Int,
) {
    val blockType: BlockType
        get() = BlockType.entries.firstOrNull { it.name == type } ?: BlockType.PARAGRAPH

    val blockFontSize: BlockFontSize
        get() = BlockFontSize.fromStorage(fontSize)

    val isMedia: Boolean
        get() = blockType == BlockType.IMAGE || blockType == BlockType.VIDEO

    fun isBlank(): Boolean {
        return when (blockType) {
            BlockType.DIVIDER, BlockType.IMAGE, BlockType.VIDEO -> false
            BlockType.TASK -> text.isBlank() && !checked
            else -> text.isBlank()
        }
    }

    companion object {
        fun paragraph(
            id: String,
            noteId: String,
            text: String,
            indent: Int = 0,
            sortOrder: Int,
            fontSize: BlockFontSize = BlockFontSize.NORMAL,
        ): NoteBlock {
            return NoteBlock(
                id = id,
                noteId = noteId,
                type = BlockType.PARAGRAPH.name,
                text = text,
                indent = indent,
                checked = false,
                fontSize = fontSize.name,
                mediaPath = "",
                sortOrder = sortOrder,
            )
        }
    }
}
