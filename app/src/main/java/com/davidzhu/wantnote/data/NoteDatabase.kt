package com.davidzhu.wantnote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [
        Note::class,
        NoteBlock::class,
        AppSettings::class,
        Tag::class,
        NoteTagCrossRef::class,
        AiProvider::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun blockDao(): BlockDao
    abstract fun settingsDao(): SettingsDao
    abstract fun tagDao(): TagDao
    abstract fun aiProviderDao(): AiProviderDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_blocks (
                        id TEXT NOT NULL PRIMARY KEY,
                        noteId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        text TEXT NOT NULL,
                        indent INTEGER NOT NULL,
                        checked INTEGER NOT NULL,
                        fontSize TEXT NOT NULL,
                        mediaPath TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        FOREIGN KEY(noteId) REFERENCES notes(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_blocks_noteId ON note_blocks(noteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_blocks_type ON note_blocks(type)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS settings (
                        id INTEGER NOT NULL PRIMARY KEY,
                        themeName TEXT NOT NULL,
                        fontScale TEXT NOT NULL,
                        dayNight TEXT NOT NULL,
                        compressImages INTEGER NOT NULL,
                        homeView TEXT NOT NULL,
                        webdavUrl TEXT NOT NULL,
                        webdavUsername TEXT NOT NULL,
                        webdavPassword TEXT NOT NULL,
                        s3Endpoint TEXT NOT NULL,
                        s3Bucket TEXT NOT NULL,
                        s3Region TEXT NOT NULL,
                        s3AccessKey TEXT NOT NULL,
                        s3SecretKey TEXT NOT NULL,
                        s3Prefix TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO settings (
                        id, themeName, fontScale, dayNight, compressImages, homeView,
                        webdavUrl, webdavUsername, webdavPassword,
                        s3Endpoint, s3Bucket, s3Region, s3AccessKey, s3SecretKey, s3Prefix
                    ) VALUES (
                        1, 'PAPER', 'STANDARD', 'LIGHT', 1, 'CARD',
                        '', '', '',
                        '', '', '', '', '', ''
                    )
                    """.trimIndent(),
                )

                val cursor = db.query("SELECT id, body FROM notes")
                while (cursor.moveToNext()) {
                    val noteId = cursor.getString(0)
                    val body = cursor.getString(1) ?: ""
                    val lines = if (body.isEmpty()) {
                        listOf("")
                    } else {
                        body.replace("\r\n", "\n").replace('\r', '\n').split("\n")
                    }
                    lines.forEachIndexed { index, line ->
                        val id = UUID.randomUUID().toString()
                        val escaped = line.replace("'", "''")
                        db.execSQL(
                            "INSERT INTO note_blocks (id, noteId, type, text, indent, checked, fontSize, mediaPath, sortOrder) " +
                                "VALUES ('$id', '${noteId.replace("'", "''")}', 'PARAGRAPH', '$escaped', 0, 0, 'NORMAL', '', $index)",
                        )
                    }
                }
                cursor.close()
            }
        }


        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tags (
                        id TEXT NOT NULL PRIMARY KEY,
                        path TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_path ON tags(path)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_tags (
                        noteId TEXT NOT NULL,
                        tagId TEXT NOT NULL,
                        PRIMARY KEY(noteId, tagId),
                        FOREIGN KEY(noteId) REFERENCES notes(id) ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_tags_noteId ON note_tags(noteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_tags_tagId ON note_tags(tagId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_providers (
                        id TEXT NOT NULL PRIMARY KEY,
                        presetId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        baseUrl TEXT NOT NULL,
                        apiKey TEXT NOT NULL,
                        availableModelsJson TEXT NOT NULL,
                        enabledModelsJson TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE settings ADD COLUMN aiSummaryProviderId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN aiSummaryModelId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN aiTitleTagProviderId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN aiTitleTagModelId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN aiSummaryText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN aiSummaryUpdatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): NoteDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                NoteDatabase::class.java,
                "wantnote.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
