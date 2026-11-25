package com.davidzhu.wantnote.sync.dinox

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * 0.12.0：轻量 `GET /api/openapi/zettelboxes` 客户端与响应防御性解析单元测试。
 * 全部使用本地 mock Interceptor（不发真实网络请求，不含任何真实 Token）。
 */
class DinoxZettelBoxesTest {

    private fun mockClient(handler: (Request) -> Response): DinoxClient {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()
        return DinoxClient(baseUrl = DinoxModels.BASE_URL, http = okHttp)
    }

    private fun jsonResponse(request: Request, httpCode: Int, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(httpCode)
            .message(if (httpCode in 200..299) "OK" else "Error")
            .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    @Test
    fun clientCallsGetZettelboxesWithRawAuthorizationHeaderAndNeverListNotes() {
        var captured: Request? = null
        val client = mockClient { req ->
            captured = req
            jsonResponse(
                req,
                200,
                """{"code":"000000","msg":"ok","data":[{"id":"box-1","name":"读书笔记"},{"id":"box-2","name":"空卡片盒","noteCount":0}]}""",
            )
        }

        val raw = client.listZettelBoxes(" mock-unit-token ")
        val req = captured
        assertNotNull(req)
        assertEquals("GET", req!!.method)
        assertEquals("https://aisdk.chatgo.pro/api/openapi/zettelboxes", req.url.toString())
        assertEquals("mock-unit-token", req.header("Authorization"))
        assertFalse(req.header("Authorization").orEmpty().startsWith("Bearer"))

        val boxes = DinoxModels.parseZettelBoxes(raw)
        assertEquals(
            listOf(
                DinoxModels.DinoxBox(name = "空卡片盒", id = "box-2"),
                DinoxModels.DinoxBox(name = "读书笔记", id = "box-1"),
            ),
            boxes,
        )
    }

    @Test
    fun clientSurfacesBusinessErrorCodesWithoutLeakingTokenOrBody() {
        val secret = "mock-secret-never-leak"
        val client000001 = mockClient { req ->
            jsonResponse(req, 200, """{"code":"000001","msg":"token=$secret","data":null}""")
        }
        val err1 = runCatching { client000001.listZettelBoxes(secret) }.exceptionOrNull()
        assertNotNull(err1)
        assertTrue(err1 is DinoxApiException)
        assertEquals("Dinox 参数错误", err1!!.message)
        assertFalse(err1.message.orEmpty().contains(secret))

        val client000008 = mockClient { req ->
            jsonResponse(req, 200, """{"code":"000008","msg":"auth failed $secret","data":null}""")
        }
        val err8 = runCatching { client000008.listZettelBoxes(secret) }.exceptionOrNull()
        assertNotNull(err8)
        assertEquals("Token 无效或已过期（认证失败）", err8!!.message)
        assertFalse(err8.message.orEmpty().contains(secret))
    }

    @Test
    fun clientHandlesHttpErrorsTimeoutsAndBlankTokenSafely() {
        val secret = "mock-secret-token"
        val client404 = mockClient { req -> jsonResponse(req, 404, "not found $secret") }
        val err404 = runCatching { client404.listZettelBoxes(secret) }.exceptionOrNull()
        assertEquals("Dinox 接口不可用（HTTP 404）", err404?.message)
        assertFalse(err404?.message.orEmpty().contains(secret))

        val client401 = mockClient { req -> jsonResponse(req, 401, "unauthorized") }
        val err401 = runCatching { client401.listZettelBoxes(secret) }.exceptionOrNull()
        assertEquals("Token 无效或已过期（HTTP 401）", err401?.message)

        val client429 = mockClient { req -> jsonResponse(req, 429, "rate limit") }
        val err429 = runCatching { client429.listZettelBoxes(secret) }.exceptionOrNull()
        assertEquals("Dinox 请求过于频繁，请稍后再试（HTTP 429）", err429?.message)

        val clientTimeout = mockClient { throw SocketTimeoutException("read timed out $secret") }
        val errTimeout = runCatching { clientTimeout.listZettelBoxes(secret) }.exceptionOrNull()
        assertNotNull(errTimeout)
        assertTrue(errTimeout!!.message.orEmpty().contains("SocketTimeoutException"))
        assertFalse(errTimeout.message.orEmpty().contains(secret))

        val errBlank = runCatching { client404.listZettelBoxes("   ") }.exceptionOrNull()
        assertEquals("尚未配置 Dinox Token", errBlank?.message)
    }

    @Test
    fun parseZettelBoxesSupportsStringArrayAndWrappedObjectVariants() {
        val stringArray = """{"code":"000000","data":[" 灵感 ","读书笔记","","空盒"]}"""
        assertEquals(
            listOf(
                DinoxModels.DinoxBox("灵感", ""),
                DinoxModels.DinoxBox("空盒", ""),
                DinoxModels.DinoxBox("读书笔记", ""),
            ),
            DinoxModels.parseZettelBoxes(stringArray),
        )

        val wrappedList = """{"code":0,"data":{"list":[{"boxId":"id-1","boxName":"工作"},{"zettelBoxId":"id-2","zettelBoxName":"归档"}]}}"""
        assertEquals(
            listOf(
                DinoxModels.DinoxBox("工作", "id-1"),
                DinoxModels.DinoxBox("归档", "id-2"),
            ),
            DinoxModels.parseZettelBoxes(wrappedList),
        )

        val wrappedBoxes = """{"boxes":[{"_id":"id-a","path":"知识/编程"},{"id":"id-b","title":"随笔"},{"id":"id-c","label":"待整理"}]}"""
        assertEquals(
            listOf(
                DinoxModels.DinoxBox("待整理", "id-c"),
                DinoxModels.DinoxBox("知识/编程", "id-a"),
                DinoxModels.DinoxBox("随笔", "id-b"),
            ),
            DinoxModels.parseZettelBoxes(wrappedBoxes),
        )

        val wrappedItems = """{"code":"200","data":{"items":[{"id":"1","name":"项目"}],"records":[{"id":"2","name":"复盘"}]}}"""
        assertEquals(
            listOf(
                DinoxModels.DinoxBox("复盘", "2"),
                DinoxModels.DinoxBox("项目", "1"),
            ),
            DinoxModels.parseZettelBoxes(wrappedItems),
        )

        val wrappedZettelBoxes = """{"code":"000000","data":{"zettelBoxes":[{"id":"z1","name":"盒子A","notes":[{"title":"不是盒子"}]}]}}"""
        assertEquals(
            listOf(DinoxModels.DinoxBox("盒子A", "z1")),
            DinoxModels.parseZettelBoxes(wrappedZettelBoxes),
        )
    }

    @Test
    fun parseZettelBoxesHandlesDeduplicationNestedChildrenDeletedAndEmptyData() {
        val complex = """
        {
          "code": "000000",
          "data": [
            {"name": "读书"},
            {"id": "b-read", "name": "读书", "children": [{"id": "b-sub", "name": "技术书"}]},
            {"id": "b-del", "name": "已删除盒", "isDel": true},
            {"id": "b-empty", "name": "完全空盒", "noteCount": 0, "notes": []},
            {"id": "b-null", "name": null}
          ]
        }
        """.trimIndent()
        assertEquals(
            listOf(
                DinoxModels.DinoxBox("完全空盒", "b-empty"),
                DinoxModels.DinoxBox("技术书", "b-sub"),
                DinoxModels.DinoxBox("读书", "b-read"),
            ),
            DinoxModels.parseZettelBoxes(complex),
        )

        assertTrue(DinoxModels.parseZettelBoxes("""{"code":"000000","data":[]}""").isEmpty())
        assertTrue(DinoxModels.parseZettelBoxes("""{"code":"000000","data":null}""").isEmpty())
        assertTrue(DinoxModels.parseZettelBoxes("""{"code":"000000","data":{}}""").isEmpty())

        val doubleEncoded = org.json.JSONObject()
            .put("code", "000000")
            .put("data", """[{"id":"d1","name":"嵌套盒"}]""")
            .toString()
        assertEquals(
            listOf(DinoxModels.DinoxBox("嵌套盒", "d1")),
            DinoxModels.parseZettelBoxes("\uFEFF$doubleEncoded"),
        )

        val rootArray = """[{"id":"r1","name":"根数组盒"}]"""
        assertEquals(
            listOf(DinoxModels.DinoxBox("根数组盒", "r1")),
            DinoxModels.parseZettelBoxes(rootArray),
        )
    }

    @Test
    fun parseZettelBoxesRejectsGarbageWithoutLeakingBody() {
        val raw = "<html>502 bad gateway secret-payload</html>"
        val err = runCatching { DinoxModels.parseZettelBoxes(raw) }.exceptionOrNull()
        assertNotNull(err)
        assertEquals("Dinox 返回了无法解析的数据", err!!.message)
        assertFalse(err.message.orEmpty().contains("secret-payload"))
    }
}
