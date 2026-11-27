package com.davidzhu.wantnote.transfer

import com.davidzhu.wantnote.data.AiProvider
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.DinoxCachedBox
import com.davidzhu.wantnote.data.DinoxRuleSyncMode
import com.davidzhu.wantnote.data.DinoxRuleType
import com.davidzhu.wantnote.data.DinoxSyncRule
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.data.NoteLink
import com.davidzhu.wantnote.sync.SyncAttachment
import com.davidzhu.wantnote.sync.SyncSnapshot
import com.davidzhu.wantnote.sync.SyncUserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.14.0 全量配置备份/恢复与 WebDAV/S3 云同步往返测试：
 * - 备份清单支持 boxes 与 dinoxSyncRules 计数且兼容旧清单；
 * - BackupBoxPlan 完整恢复卡片盒、去重重名盒、补建缺失盒，并同步重映射 Dinox 卡片盒同步规则目标盒；
 * - SyncSnapshot（WebDAV / S3 共用）往返保留 boxes、dinoxSyncRules、dinoxCachedBoxes、
 *   aiProviders、userSettings（且不覆盖本机 WebDAV/S3 凭据）、noteLinks 与自定义字体。
 */
class BoxBackupAndSyncTest {

    @Test
    fun backupManifestRoundTripsBoxesAndRulesAndAcceptsLegacyText() {
        val m = BackupManifest(notes = 3, boxes = 4, dinoxSyncRules = 2, appVersion = "0.14.0")
        val parsed = BackupManifest.fromText(m.toText())
        assertEquals(4, parsed.boxes)
        assertEquals(2, parsed.dinoxSyncRules)
        assertEquals(3, parsed.notes)

        val legacyText = m.toText().lines()
            .filterNot { it.startsWith("boxes=") || it.startsWith("dinoxSyncRules=") }
            .joinToString("\n")
        val legacy = BackupManifest.fromText(legacyText)
        assertEquals(0, legacy.boxes)
        assertEquals(0, legacy.dinoxSyncRules)
    }

    @Test
    fun backupBoxPlanRestoresBoxesAndRemapsNotesAndDinoxSyncRules() {
        val boxes = listOf(
            NoteBox(id = NoteBox.DEFAULT_ID, name = NoteBox.DEFAULT_NAME, createdAt = 0L),
            NoteBox(id = "b1", name = "工作", createdAt = 10L),
            NoteBox(id = "b1_dup", name = "工作", createdAt = 20L),
        )
        val notes = listOf(
            Note(id = "n1", title = "A", body = "", createdAt = 1L, updatedAt = 1L, boxId = "b1"),
            Note(id = "n2", title = "B", body = "", createdAt = 2L, updatedAt = 2L, boxId = "b1_dup"),
            Note(id = "n3", title = "C", body = "", createdAt = 3L, updatedAt = 3L, boxId = "b_missing"),
        )
        val rules = listOf(
            DinoxSyncRule(
                id = "r1",
                ruleType = DinoxRuleType.MANY_TO_ONE.name,
                sourceBoxesJson = """["收件箱","灵感"]""",
                targetBoxId = "b1_dup",
                followDinoxName = false,
                syncMode = DinoxRuleSyncMode.AUTO.name,
                autoIntervalMinutes = 30,
            ),
            DinoxSyncRule(
                id = "r2",
                ruleType = DinoxRuleType.ONE_TO_ONE.name,
                sourceBoxesJson = """["阅读"]""",
                targetBoxId = "b_rule_missing",
                followDinoxName = false,
                syncMode = DinoxRuleSyncMode.MANUAL.name,
            ),
            DinoxSyncRule(
                id = "r3",
                ruleType = DinoxRuleType.ONE_TO_ONE.name,
                sourceBoxesJson = """["随笔"]""",
                targetBoxId = "should_clear",
                followDinoxName = true,
                syncMode = DinoxRuleSyncMode.AUTO.name,
                autoIntervalMinutes = 60,
            ),
        )
        val plan = BackupBoxPlan.prepare(
            boxes = boxes,
            notes = notes,
            noteBoxNames = mapOf("n3" to "灵感"),
            syncRules = rules,
            ruleTargetBoxNames = mapOf("r2" to "读书卡片盒"),
            now = 99L,
        )

        val names = plan.boxes.map { it.name }.toSet()
        assertTrue(NoteBox.DEFAULT_NAME in names)
        assertTrue("工作" in names)
        assertTrue("灵感" in names)
        assertTrue("读书卡片盒" in names)
        assertEquals(4, plan.boxes.size)

        val byNote = plan.notes.associateBy { it.id }
        assertEquals("b1", byNote.getValue("n1").boxId)
        assertEquals("b1", byNote.getValue("n2").boxId)
        assertEquals("b_missing", byNote.getValue("n3").boxId)

        val byRule = plan.syncRules.associateBy { it.id }
        assertEquals("b1", byRule.getValue("r1").targetBoxId)
        assertEquals("b_rule_missing", byRule.getValue("r2").targetBoxId)
        assertEquals("", byRule.getValue("r3").targetBoxId)
    }

    @Test
    fun syncSnapshotRoundTripsFullUserConfigRulesBoxesLinksAndPreservesLocalSyncCreds() {
        val boxes = listOf(
            NoteBox(id = NoteBox.DEFAULT_ID, name = NoteBox.DEFAULT_NAME, createdAt = 0L),
            NoteBox(id = "b_read", name = "阅读笔记", createdAt = 100L),
        )
        val notes = listOf(
            Note(id = "n1", title = "书摘", body = "内容", createdAt = 1L, updatedAt = 2L, mdPath = "notes/n1.md", boxId = "b_read"),
        )
        val rules = listOf(
            DinoxSyncRule(
                id = "rule_1",
                ruleType = DinoxRuleType.ONE_TO_ONE.name,
                sourceBoxesJson = """["Dinox阅读"]""",
                targetBoxId = "b_read",
                followDinoxName = false,
                syncMode = DinoxRuleSyncMode.AUTO.name,
                autoIntervalMinutes = 45,
                lastSyncAt = 12345L,
            ),
        )
        val cachedBoxes = listOf(DinoxCachedBox(name = "Dinox阅读", fetchedAt = 777L, remoteId = "z_1"))
        val providers = listOf(
            AiProvider(
                id = "p1",
                presetId = "openai",
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-test",
                availableModelsJson = """["gpt-4o"]""",
                enabledModelsJson = """["gpt-4o"]""",
                updatedAt = 888L,
            ),
        )
        val remoteSettings = AppSettings.DEFAULT.copy(
            themeName = "SAGE",
            dayNight = "DARK",
            fontScale = "LARGE",
            themeCustomJson = """{"USER_1":{"lightBg":123}}""",
            editorFontId = "CUSTOM",
            customFontPath = "fonts/my.ttf",
            webdavPassword = "SHOULD_NOT_BE_IN_SNAPSHOT",
            s3SecretKey = "SHOULD_NOT_BE_IN_SNAPSHOT",
        )
        val snapshot = SyncSnapshot(
            exportedAt = 500L,
            notes = notes,
            blocks = emptyList(),
            tags = emptyList(),
            noteTags = emptyList(),
            noteLinks = listOf(NoteLink("n1", "n2", 300L)),
            attachments = emptyList(),
            fonts = listOf(SyncAttachment("fonts/my.ttf", 1024L, "abc")),
            boxes = boxes,
            noteBoxNames = mapOf("n1" to "阅读笔记"),
            aiProviders = providers,
            dinoxSyncRules = rules,
            ruleTargetBoxNames = mapOf("rule_1" to "阅读笔记"),
            dinoxCachedBoxes = cachedBoxes,
            userSettings = SyncUserSettings.from(remoteSettings),
        )

        val json = snapshot.toJson()
        assertTrue(!json.contains("SHOULD_NOT_BE_IN_SNAPSHOT"))

        val restored = SyncSnapshot.fromJson(json)
        assertEquals(2, restored.boxes.size)
        assertEquals("b_read", restored.notes.single().boxId)
        assertEquals(1, restored.dinoxSyncRules.size)
        assertEquals("阅读笔记", restored.ruleTargetBoxNames["rule_1"])
        assertEquals(45, restored.dinoxSyncRules.single().autoIntervalMinutes)
        assertEquals(1, restored.dinoxCachedBoxes.size)
        assertEquals("z_1", restored.dinoxCachedBoxes.single().remoteId)
        assertEquals(1, restored.aiProviders.size)
        assertEquals("p1", restored.aiProviders.single().id)
        assertEquals(1, restored.noteLinks.size)
        assertEquals(1, restored.fonts.size)

        val localWithCreds = AppSettings.DEFAULT.copy(
            webdavUrl = "https://dav.example.com",
            webdavPassword = "LOCAL_WEBDAV_SECRET",
            s3SecretKey = "LOCAL_S3_SECRET",
        )
        val userSettings = restored.userSettings
        assertNotNull(userSettings)
        val merged = userSettings!!.applyOnto(localWithCreds)
        assertEquals("SAGE", merged.themeName)
        assertEquals("DARK", merged.dayNight)
        assertEquals("LARGE", merged.fontScale)
        assertEquals("""{"USER_1":{"lightBg":123}}""", merged.themeCustomJson)
        assertEquals("fonts/my.ttf", merged.customFontPath)
        assertEquals("LOCAL_WEBDAV_SECRET", merged.webdavPassword)
        assertEquals("LOCAL_S3_SECRET", merged.s3SecretKey)
    }
}
