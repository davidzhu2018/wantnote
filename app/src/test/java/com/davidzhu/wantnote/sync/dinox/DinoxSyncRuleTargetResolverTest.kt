package com.davidzhu.wantnote.sync.dinox

import com.davidzhu.wantnote.data.DinoxRuleSyncMode
import com.davidzhu.wantnote.data.DinoxRuleType
import com.davidzhu.wantnote.data.DinoxSyncRule
import com.davidzhu.wantnote.data.DinoxSyncRuleCodec
import com.davidzhu.wantnote.data.NoteBox
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.11.0 N4-N9：卡片盒同步规则目标解析与校验单元测试（纯 JVM，不联网）。
 *
 * 重点覆盖：
 * - 一对一启用「沿用 Dinox 卡片盒名称」时，本机没有同名盒就自动新建同名盒；
 * - 本机已有同名盒时直接复用，不重复或另造名字；
 * - 多对一及非沿用模式绝不新建本地卡片盒；
 * - 规则校验与规范化（含自动同步间隔）。
 */
class DinoxSyncRuleTargetResolverTest {

    private class FakeLocalBoxLookup(initial: List<NoteBox> = emptyList()) : LocalBoxLookup {
        private val boxes = initial.toMutableList()
        val createdNames = mutableListOf<String>()

        override suspend fun findById(id: String): NoteBox? = boxes.firstOrNull { it.id == id }

        override suspend fun findByName(name: String): NoteBox? = boxes.firstOrNull { it.name == name }

        override suspend fun create(name: String): NoteBox? {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || boxes.any { it.name == trimmed }) return null
            val created = NoteBox(
                id = "created-${boxes.size + 1}",
                name = trimmed,
                createdAt = 100L + boxes.size,
            )
            boxes += created
            createdNames += trimmed
            return created
        }

        fun allBoxes(): List<NoteBox> = boxes.toList()
    }

    @Test
    fun oneToOneFollowDinoxNameCreatesMissingLocalBox() = runBlocking {
        val store = FakeLocalBoxLookup(
            listOf(NoteBox(id = NoteBox.DEFAULT_ID, name = NoteBox.DEFAULT_NAME, createdAt = 0L)),
        )
        val rule = DinoxSyncRule(
            id = "rule-1",
            ruleType = DinoxRuleType.ONE_TO_ONE.name,
            sourceBoxesJson = DinoxSyncRuleCodec.encodeSourceBoxes(listOf("读书笔记")),
            targetBoxId = "",
            followDinoxName = true,
            syncMode = DinoxRuleSyncMode.MANUAL.name,
        )

        val resolved = DinoxSyncRuleTargetResolver.resolveTargetBox(rule, store)

        assertTrue(resolved.isSuccess)
        val box = resolved.getOrThrow()
        assertEquals("读书笔记", box.name)
        assertEquals(listOf("读书笔记"), store.createdNames)
        assertEquals(2, store.allBoxes().size)
    }

    @Test
    fun oneToOneFollowDinoxNameReusesExistingSameNameBoxWithoutCreating() = runBlocking {
        val existing = NoteBox(id = "local-reading", name = "读书笔记", createdAt = 10L)
        val store = FakeLocalBoxLookup(listOf(existing))
        val rule = DinoxSyncRule(
            id = "rule-2",
            ruleType = DinoxRuleType.ONE_TO_ONE.name,
            sourceBoxesJson = DinoxSyncRuleCodec.encodeSourceBoxes(listOf("读书笔记")),
            targetBoxId = "",
            followDinoxName = true,
            syncMode = DinoxRuleSyncMode.AUTO.name,
            autoIntervalMinutes = 60,
        )

        val resolved = DinoxSyncRuleTargetResolver.resolveTargetBox(rule, store)

        assertTrue(resolved.isSuccess)
        assertEquals("local-reading", resolved.getOrThrow().id)
        assertTrue(store.createdNames.isEmpty())
        assertEquals(1, store.allBoxes().size)
    }

    @Test
    fun manyToOneNeverFollowsOrCreatesBox() = runBlocking {
        val target = NoteBox(id = "local-work", name = "工作汇总", createdAt = 20L)
        val store = FakeLocalBoxLookup(listOf(target))
        val rawRule = DinoxSyncRule(
            id = "rule-3",
            ruleType = DinoxRuleType.MANY_TO_ONE.name,
            sourceBoxesJson = DinoxSyncRuleCodec.encodeSourceBoxes(listOf("周报", "会议")),
            targetBoxId = "local-work",
            followDinoxName = true, // 即使传入 true，多对一也必须忽略/清零。
            syncMode = DinoxRuleSyncMode.MANUAL.name,
        )

        val normalized = DinoxSyncRuleTargetResolver.normalize(rawRule)
        assertFalse(normalized.followDinoxName)

        val resolved = DinoxSyncRuleTargetResolver.resolveTargetBox(normalized, store)
        assertTrue(resolved.isSuccess)
        assertEquals("local-work", resolved.getOrThrow().id)
        assertTrue(store.createdNames.isEmpty())
    }

    @Test
    fun explicitTargetBoxMissingFailsWithoutInventingNewBox() = runBlocking {
        val store = FakeLocalBoxLookup(emptyList())
        val rule = DinoxSyncRule(
            id = "rule-4",
            ruleType = DinoxRuleType.ONE_TO_ONE.name,
            sourceBoxesJson = DinoxSyncRuleCodec.encodeSourceBoxes(listOf("灵感")),
            targetBoxId = "deleted-box-id",
            followDinoxName = false,
            syncMode = DinoxRuleSyncMode.MANUAL.name,
        )

        val resolved = DinoxSyncRuleTargetResolver.resolveTargetBox(rule, store)

        assertTrue(resolved.isFailure)
        assertTrue(store.createdNames.isEmpty())
        assertTrue(store.allBoxes().isEmpty())
    }

    @Test
    fun validateAndNormalizeEnforceRuleConstraints() {
        val autoTooShort = DinoxSyncRule(
            id = "r1",
            ruleType = DinoxRuleType.ONE_TO_ONE.name,
            sourceBoxesJson = DinoxSyncRuleCodec.encodeSourceBoxes(listOf("工作")),
            followDinoxName = true,
            syncMode = DinoxRuleSyncMode.AUTO.name,
            autoIntervalMinutes = 5,
        )
        assertNotNull(DinoxSyncRuleTargetResolver.validate(autoTooShort))

        val autoValid = autoTooShort.copy(autoIntervalMinutes = 30)
        assertNull(DinoxSyncRuleTargetResolver.validate(autoValid))
        assertEquals("30 分钟", DinoxSyncRuleCodec.formatInterval(30))
        assertEquals("2 小时", DinoxSyncRuleCodec.formatInterval(120))
        assertEquals("1 天", DinoxSyncRuleCodec.formatInterval(1440))

        val manyNoTarget = DinoxSyncRule(
            id = "r2",
            ruleType = DinoxRuleType.MANY_TO_ONE.name,
            sourceBoxesJson = DinoxSyncRuleCodec.encodeSourceBoxes(listOf("A", "B")),
            targetBoxId = "",
            followDinoxName = true,
            syncMode = DinoxRuleSyncMode.MANUAL.name,
        )
        assertNotNull(DinoxSyncRuleTargetResolver.validate(DinoxSyncRuleTargetResolver.normalize(manyNoTarget)))
    }
}
