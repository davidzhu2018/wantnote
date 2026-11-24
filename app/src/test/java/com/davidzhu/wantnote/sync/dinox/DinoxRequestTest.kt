package com.davidzhu.wantnote.sync.dinox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dinox 请求构造 / 响应解析 / 时间格式 / 密封 Token（凭据派生密钥）。
 * 不发任何网络请求，也不使用真实 Token。
 */
class DinoxRequestTest {

    @Test
    fun authHeaderIsRawTokenWithoutBearer() {
        // 文档：Authorization: <Token>，不加 Bearer 前缀。
        assertEquals("token-123", DinoxModels.authHeader(" token-123 "))
    }

    @Test
    fun listNotesBodyCarriesCursorAndBoxes() {
        val full = DinoxModels.listNotesBody(lastSyncIso = null)
        assertEquals("{}", full)
        val incremental = DinoxModels.listNotesBody(
            lastSyncIso = "2026-03-01T00:00:00.000Z",
            boxNames = listOf("读书笔记", "工作"),
        )
        assertTrue(incremental.contains("\"lastSyncTime\":\"2026-03-01T00:00:00.000Z\""))
        assertTrue(incremental.contains("\"zettelBoxNames\":[\"读书笔记\",\"工作\"]"))
        assertFalse(incremental.contains("zettelBoxIds"))
    }

    @Test
    fun probeBodyUsesIgnorableBoxName() {
        // 连接测试：按一个不存在的卡片盒过滤 → 服务端返回空列表，只验证认证。
        assertEquals(
            DinoxModels.listNotesBody(lastSyncIso = null, boxNames = listOf("__wantnote_probe__")),
            DinoxModels.probeBody(),
        )
    }

    @Test
    fun textInputBodyCarriesMarkdownTitleTagsAndCreatedAt() {
        val body = DinoxModels.textInputBody(
            content = "# 会议纪要\n\n今天讨论了进度",
            title = "项目会议",
            tags = listOf("工作", "会议"),
            zettelBoxNames = listOf("工作笔记"),
            createdAtIso = "2025-12-01T08:30:00.000Z",
        )
        assertTrue(body.contains("\"content\":\"# 会议纪要\\n\\n今天讨论了进度\""))
        assertTrue(body.contains("\"title\":\"项目会议\""))
        assertTrue(body.contains("\"tags\":[\"工作\",\"会议\"]"))
        assertTrue(body.contains("\"zettelBoxNames\":[\"工作笔记\"]"))
        assertTrue(body.contains("\"createdAt\":\"2025-12-01T08:30:00.000Z\""))
    }

    @Test
    fun textInputOmitsBlankOptionals() {
        val body = DinoxModels.textInputBody(content = "只有正文", title = "")
        assertFalse(body.contains("title"))
        assertFalse(body.contains("tags"))
        assertFalse(body.contains("createdAt"))
    }

    @Test
    fun parseListNotesGroupsAndTracksLatestUpdate() {
        val json = """
        {
          "code": "000000",
          "msg": "ok",
          "data": [
            {
              "date": "2026-03-04",
              "notes": [
                {
                  "noteId": "01HQ1",
                  "title": "TS 学习",
                  "content": "# 今日\n\n学习了 TypeScript",
                  "tags": ["编程"],
                  "zettelBoxes": ["技术笔记"],
                  "createTime": "2026-03-04T12:00:00.000Z",
                  "updateTime": "2026-03-04T12:00:00.000Z",
                  "type": "note",
                  "isAudio": false,
                  "audioUrl": "",
                  "parentNoteId": null,
                  "isDel": false
                },
                {
                  "noteId": "01HQ2",
                  "title": "批注",
                  "content": "补充",
                  "tags": [],
                  "zettelBoxes": ["技术笔记"],
                  "createTime": "2026-03-04T13:00:00.000Z",
                  "updateTime": "2026-03-04T13:00:00.000Z",
                  "type": "note",
                  "isAudio": false,
                  "audioUrl": "",
                  "parentNoteId": "01HQ1",
                  "isDel": false
                }
              ]
            },
            {
              "date": "2026-03-05",
              "notes": [
                {
                  "noteId": "01HQ3",
                  "title": "语音",
                  "content": "录音转写",
                  "tags": [],
                  "zettelBoxes": [],
                  "createTime": "2026-03-05T09:00:00.000Z",
                  "updateTime": "2026-03-05T09:00:00.000Z",
                  "type": "note",
                  "isAudio": true,
                  "audioUrl": "https://example.com/a.mp3",
                  "parentNoteId": null,
                  "isDel": true
                }
              ]
            }
          ]
        }
        """.trimIndent()
        val result = DinoxModels.parseListNotes(json)
        assertEquals(3, result.notes.size)
        assertEquals("01HQ1", result.notes[0].noteId)
        assertEquals(listOf("编程"), result.notes[0].tags)
        assertEquals("01HQ1", result.notes[1].parentNoteId)
        assertTrue(result.notes[2].isDel)
        assertEquals("2026-03-05T09:00:00.000Z", result.latestUpdateIso)
    }

    @Test
    fun parseListNotesSurfacesAuthFailure() {
        val json = """{"code": "000008", "msg": "auth failed", "data": null}"""
        val error = runCatching { DinoxModels.parseListNotes(json) }.exceptionOrNull()
        assertNotNull(error)
        assertEquals("Token 无效或已过期（认证失败）", error!!.message)
    }

    @Test
    fun isoTimeRoundTripsUtc() {
        val ms = 1_760_000_000_000L
        val iso = DinoxModels.isoFromEpochMs(ms)
        assertTrue(iso.endsWith("Z"))
        assertEquals(ms, DinoxModels.epochMsFromIso(iso))
        // 无毫秒变体也能解析。
        assertEquals(ms, DinoxModels.epochMsFromIso(iso.replace(".000Z", "Z")))
        assertEquals(0L, DinoxModels.epochMsFromIso(""))
        assertEquals(0L, DinoxModels.epochMsFromIso("not-a-time"))
    }

    @Test
    fun splitBoxNamesSupportsCommonSeparators() {
        assertEquals(listOf("读书笔记", "工作"), DinoxModels.splitBoxNames("读书笔记、工作"))
        assertEquals(listOf("a", "b"), DinoxModels.splitBoxNames("a,b"))
        assertEquals(listOf("a", "b", "c"), DinoxModels.splitBoxNames("a；b c"))
        assertEquals(listOf("单个"), DinoxModels.splitBoxNames(" 单个 "))
        assertTrue(DinoxModels.splitBoxNames("").isEmpty())
    }

    @Test
    fun portableCipherRoundTripsWithSameSecret() {
        val token = "dinox-secret-token-abc"
        val sealed = DinoxPortableCipher.seal(token, secret = "webdav-pass")
        assertNotNull(sealed)
        assertTrue(sealed!!.startsWith("WN1:"))
        assertFalse(sealed.contains(token))
        assertEquals(token, DinoxPortableCipher.open(sealed, secret = "webdav-pass"))
    }

    @Test
    fun portableCipherRejectsWrongSecretAndGarbage() {
        val sealed = DinoxPortableCipher.seal("token", secret = "pass-a")
        assertNull(DinoxPortableCipher.open(sealed!!, secret = "pass-b"))
        assertNull(DinoxPortableCipher.open("not-sealed", secret = "pass-a"))
        assertNull(DinoxPortableCipher.open("WN1:###", secret = "pass-a"))
        assertNull(DinoxPortableCipher.seal("token", secret = ""))
        assertNull(DinoxPortableCipher.open(null.orEmpty(), secret = "pass-a"))
    }

    @Test
    fun syncSecretFollowsMethod() {
        assertEquals("web-pass", DinoxPortableCipher.syncSecret("WEBDAV", "web-pass", "s3-key"))
        assertEquals("s3-key", DinoxPortableCipher.syncSecret("S3", "web-pass", "s3-key"))
        assertEquals("web-pass", DinoxPortableCipher.syncSecret("other", "web-pass", "s3-key"))
    }

    @Test
    fun errorMessageMappingIsShortAndSecretFree() {
        assertEquals("Dinox 参数错误", DinoxModels.errorMessage("000001"))
        assertEquals("Dinox 服务器内部错误", DinoxModels.errorMessage("000005"))
        assertEquals("Dinox 资源不存在", DinoxModels.errorMessage("000006"))
        assertEquals("Token 无效或已过期（认证失败）", DinoxModels.errorMessage("000008"))
        assertTrue(DinoxModels.errorMessage("999999").contains("999999"))
    }

    @Test
    fun stripLeadingTitleDropsMatchingH1Only() {
        assertEquals(
            "正文第一段",
            DinoxSyncService.stripLeadingTitle("会议纪要", "# 会议纪要\n\n正文第一段"),
        )
        assertEquals(
            "## 小节\n还在",
            DinoxSyncService.stripLeadingTitle("会议纪要", "## 小节\n还在"),
        )
        assertEquals(
            "# 其他标题\n正文",
            DinoxSyncService.stripLeadingTitle("会议纪要", "# 其他标题\n正文"),
        )
    }

    @Test
    fun resolveFollowBoxNameUsesFirstSelectedMatchNotUnselected() {
        // 笔记同时属于未选中的「其他」和选中的 ABC：必须进 ABC，不能误建「其他」。
        assertEquals(
            "ABC",
            DinoxSyncService.resolveFollowBoxName(
                remoteBoxes = listOf("其他", "ABC"),
                sourceBoxes = listOf("ABC", "DEF"),
            ),
        )
    }

    @Test
    fun resolveFollowBoxNameFallsBackToSingleSource() {
        assertEquals(
            "ABC",
            DinoxSyncService.resolveFollowBoxName(
                remoteBoxes = emptyList(),
                sourceBoxes = listOf("ABC"),
            ),
        )
    }

    @Test
    fun resolveFollowBoxNameReturnsNullWhenMultipleSourcesDoNotIntersect() {
        assertNull(
            DinoxSyncService.resolveFollowBoxName(
                remoteBoxes = listOf("其他"),
                sourceBoxes = listOf("ABC", "DEF"),
            ),
        )
    }

    // ---- 0.11.0 N10：listNotes 防御性解析（稍不规则但仍成功的 payload） --------

    @Test
    fun parseListNotesHandlesBomNumericCodeAndNullData() {
        val json = "\uFEFF  {\"code\": 0, \"msg\": \"ok\", \"data\": null}  "
        val result = DinoxModels.parseListNotes(json)
        assertTrue(result.notes.isEmpty())
        assertNull(result.latestUpdateIso)
    }

    @Test
    fun parseListNotesHandlesFlatArrayAndBoxObjects() {
        val json = """
        {
          "code": "200",
          "data": [
            {
              "id": "n-flat-1",
              "title": null,
              "content": "正文内容",
              "tags": [{"name": "读书"}, {"tagName": "摘录"}],
              "zettelBoxes": [{"id": "b1", "name": "读书笔记"}, {"boxName": "长期知识"}],
              "createTime": "2026-04-01 08:30:00",
              "updateTime": "2026-04-01T09:15:00+08:00",
              "parentNoteId": null,
              "isDel": 0
            },
            {
              "noteId": "n-flat-2",
              "title": "已删笔记",
              "body": "旧正文",
              "tags": "工作、复盘",
              "zettelBoxNames": "工作盒",
              "createTime": 1760000000000,
              "updateTime": 1760000005000,
              "isDel": "true"
            }
          ]
        }
        """.trimIndent()
        val result = DinoxModels.parseListNotes(json)
        assertEquals(2, result.notes.size)

        val first = result.notes[0]
        assertEquals("n-flat-1", first.noteId)
        assertEquals("", first.title) // null 字段不应被解析成字面量 "null"
        assertEquals(listOf("读书", "摘录"), first.tags)
        assertEquals(listOf("读书笔记", "长期知识"), first.zettelBoxes)
        assertNull(first.parentNoteId)
        assertFalse(first.isDel)

        val second = result.notes[1]
        assertEquals("n-flat-2", second.noteId)
        assertEquals("旧正文", second.content)
        assertEquals(listOf("工作", "复盘"), second.tags)
        assertEquals(listOf("工作盒"), second.zettelBoxes)
        assertTrue(second.isDel)
        assertTrue(DinoxModels.epochMsFromIso(second.updateTimeIso) == 1_760_000_005_000L)
    }

    @Test
    fun parseListNotesHandlesWrappedObjectDateMapAndRootArray() {
        val wrapped = """
        {
          "success": true,
          "data": {
            "list": [
              {
                "date": "2026-05-01",
                "notes": [
                  {
                    "noteId": "w-1",
                    "title": "包裹结构",
                    "content": "内容",
                    "zettelBoxes": ["技术"],
                    "updateTime": "2026-05-01T10:00:00.123456Z"
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()
        val wrappedResult = DinoxModels.parseListNotes(wrapped)
        assertEquals(1, wrappedResult.notes.size)
        assertEquals(listOf("技术"), wrappedResult.notes[0].zettelBoxes)
        assertTrue(DinoxModels.epochMsFromIso(wrappedResult.notes[0].updateTimeIso) > 0L)

        val dateMap = """
        {
          "code": "000000",
          "data": {
            "2026-05-02": [
              {"noteId": "m-1", "title": "日期映射", "content": "ok", "zettelBoxes": ["收件箱"]}
            ]
          }
        }
        """.trimIndent()
        val mapResult = DinoxModels.parseListNotes(dateMap)
        assertEquals(1, mapResult.notes.size)
        assertEquals("m-1", mapResult.notes[0].noteId)
        assertEquals(listOf("收件箱"), mapResult.notes[0].zettelBoxes)

        val rootArray = """
        [
          {"noteId": "a-1", "title": "顶层数组", "content": "hello", "zettelBoxes": ["灵感"]}
        ]
        """.trimIndent()
        val arrResult = DinoxModels.parseListNotes(rootArray)
        assertEquals(1, arrResult.notes.size)
        assertEquals(listOf("灵感"), arrResult.notes[0].zettelBoxes)
    }

    @Test
    fun parseListNotesHandlesDoubleEncodedDataStringAndRejectsGarbageWithoutLeakingBody() {
        val inner = """[{"date":"2026-05-03","notes":[{"noteId":"s-1","title":"t","content":"c","zettelBoxes":["随手记"]}]}]"""
        val doubleEncoded = org.json.JSONObject()
            .put("code", "000000")
            .put("data", inner)
            .toString()
        val parsed = DinoxModels.parseListNotes(doubleEncoded)
        assertEquals(1, parsed.notes.size)
        assertEquals(listOf("随手记"), parsed.notes[0].zettelBoxes)

        val secretBody = "<html>secret-token-should-never-appear</html>"
        val error = runCatching { DinoxModels.parseListNotes(secretBody) }.exceptionOrNull()
        assertNotNull(error)
        assertEquals("Dinox 返回了无法解析的数据", error!!.message)
        assertFalse(error.message.orEmpty().contains("secret-token"))
    }
}
