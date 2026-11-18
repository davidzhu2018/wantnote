package com.davidzhu.wantnote.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupManifestTest {
    @Test
    fun manifestTextRoundTrip() {
        val manifest = BackupManifest(
            createdAt = 1700000000000L,
            appVersion = "0.7.0",
            notes = 3,
            blocks = 12,
            tags = 2,
            noteTags = 4,
            attachments = 5,
            fonts = 1,
        )
        val parsed = BackupManifest.fromText(manifest.toText())
        assertEquals(manifest, parsed)
    }

    @Test
    fun unknownFormatIsRejected() {
        try {
            BackupManifest.fromText("format=other\nversion=1\n")
            fail("应当拒绝非想记备份")
        } catch (error: BackupManifest.InvalidBackup) {
            assertTrue(error.message.orEmpty().contains("格式"))
        }
    }

    @Test
    fun futureVersionIsRejected() {
        val text = "format=${BackupManifest.FORMAT}\nversion=${BackupManifest.VERSION + 1}\ncreatedAt=0\n"
        try {
            BackupManifest.fromText(text)
            fail("应当拒绝高版本备份")
        } catch (error: BackupManifest.InvalidBackup) {
            assertTrue(error.message.orEmpty().contains("版本"))
        }
    }

    @Test
    fun requiredFilesAreListed() {
        assertEquals(
            listOf(
                "data/notes.json",
                "data/blocks.json",
                "data/tags.json",
                "data/note_tags.json",
                "data/settings.json",
                "data/ai_providers.json",
            ),
            BackupManifest.RequiredDataFiles,
        )
    }
}
