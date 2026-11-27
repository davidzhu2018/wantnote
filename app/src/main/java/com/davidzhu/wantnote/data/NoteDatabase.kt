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
        NoteLink::class,
        NoteBox::class,
        DinoxCachedBox::class,
        DinoxSyncRule::class,
    ],
    version = 18,
    exportSchema = false,
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun blockDao(): BlockDao
    abstract fun settingsDao(): SettingsDao
    abstract fun tagDao(): TagDao
    abstract fun aiProviderDao(): AiProviderDao
    abstract fun noteLinkDao(): NoteLinkDao
    abstract fun noteBoxDao(): NoteBoxDao
    abstract fun dinoxBoxDao(): DinoxBoxDao
    abstract fun dinoxSyncRuleDao(): DinoxSyncRuleDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN compressVideos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN editorFontId TEXT NOT NULL DEFAULT 'SYSTEM'")
                db.execSQL("ALTER TABLE settings ADD COLUMN customFontPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN syncMethod TEXT NOT NULL DEFAULT 'WEBDAV'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN aiSummaryScope TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 0.8.0: structured Markdown headings and stable-ID note links.
         * Only additive changes: existing notes/blocks/tags keep their IDs and
         * content untouched, so a 0.7.0 database opens without a rewrite.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note_blocks ADD COLUMN headingLevel INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_links (
                        sourceNoteId TEXT NOT NULL,
                        targetNoteId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(sourceNoteId, targetNoteId),
                        FOREIGN KEY(sourceNoteId) REFERENCES notes(id) ON DELETE CASCADE,
                        FOREIGN KEY(targetNoteId) REFERENCES notes(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_links_sourceNoteId ON note_links(sourceNoteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_links_targetNoteId ON note_links(targetNoteId)")
            }
        }

        /**
         * 0.9.0: each note body gains a Markdown document path
         * (`notes/<id>.md` under the app data root), plus settings columns
         * for the editor mode, custom themes, Dinox sync state and the
         * forced-migration flag. The actual blocks → Markdown conversion
         * runs later through the forced, resumable upgrade flow in the UI;
         * this migration only adds columns so a 0.8.0 database opens
         * unchanged.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN mdPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN editorMode TEXT NOT NULL DEFAULT 'WYSIWYG'")
                db.execSQL("ALTER TABLE settings ADD COLUMN themeCustomJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE settings ADD COLUMN dinoxTokenSealed TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN dinoxSealMethod TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN dinoxLastSyncAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN dinoxSyncBox TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN dinoxAutoSyncMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN markdownMigrationDone INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 0.10.0: notes gain a 卡片盒 column plus a `note_boxes` table.
         * Only additive: existing notes keep boxId = ''（未分配），the default
         * 「暂存箱」 box is created idempotently so deleting any other box can
         * always move its notes somewhere safe. No note content is touched.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN boxId TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_boxes (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_boxes_name ON note_boxes(name)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO note_boxes (id, name, createdAt)
                    VALUES ('${NoteBox.DEFAULT_ID}', '${NoteBox.DEFAULT_NAME}', 0)
                    """.trimIndent(),
                )
            }
        }

        /**
         * 0.11.0: additive migration for Dinox cached box list + last fetch timestamp
         * and persistent 卡片盒同步 rules. Existing notes, boxes, tags and settings are untouched.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN dinoxBoxesFetchedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dinox_cached_boxes (
                        name TEXT NOT NULL PRIMARY KEY,
                        fetchedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dinox_sync_rules (
                        id TEXT NOT NULL PRIMARY KEY,
                        ruleType TEXT NOT NULL,
                        sourceBoxesJson TEXT NOT NULL,
                        targetBoxId TEXT NOT NULL,
                        followDinoxName INTEGER NOT NULL,
                        syncMode TEXT NOT NULL,
                        autoIntervalMinutes INTEGER NOT NULL,
                        lastSyncAt INTEGER NOT NULL,
                        lastRunAt INTEGER NOT NULL,
                        lastStatus TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * 0.12.0: additive migration adding optional remote box ID (`remoteId`) to
         * `dinox_cached_boxes` so boxes returned by `GET /api/openapi/zettelboxes`
         * persist their ID alongside name. Existing cached rows keep `''`.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dinox_cached_boxes ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 0.16.5: additive migration adding 5 AI capability switches to `settings`
         * (all enabled by default).
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN aiEnableTitle INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN aiEnableTag INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN aiEnableBox INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN aiEnableSummary INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN aiEnableInsight INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN autoCheckUpdate INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN profileNickname TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN profileAvatarPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN heatmapColorHex TEXT NOT NULL DEFAULT '#2EA043'")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN uiStyle TEXT NOT NULL DEFAULT 'MIUIX'")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN locationName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN locationAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN locationLat REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE notes ADD COLUMN locationLng REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE settings ADD COLUMN mapProvider TEXT NOT NULL DEFAULT 'AMAP'")
                db.execSQL("ALTER TABLE settings ADD COLUMN mapApiKey TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN flomoMcpUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN flomoMcpToken TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN flomoWebhookUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN flomoLastSyncAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN dedaoMcpUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN dedaoMcpToken TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN dedaoLastSyncAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN reviewed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN readwiseCategoryTagsJson TEXT NOT NULL DEFAULT '{}'")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN readwiseToken TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN readwiseCategoryBoxesJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE settings ADD COLUMN readwiseAutoSyncMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN readwiseLastSyncAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN wereadApiKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN wereadBoxId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN wereadAutoSyncMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN wereadLastSyncAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): NoteDatabase {
            // 0.6.0：数据库优先放在 Documents/WantNote（卸载后可找回），
            // 未授权时回退到旧私有位置；首次启动由 AppStorage.prepare 迁移旧数据。
            val dbFile = AppStorage.dbFile(context)
            dbFile.parentFile?.mkdirs()
            return Room.databaseBuilder(
                context.applicationContext,
                NoteDatabase::class.java,
                dbFile.absolutePath,
            )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                )
                .build()
        }
    }
}
