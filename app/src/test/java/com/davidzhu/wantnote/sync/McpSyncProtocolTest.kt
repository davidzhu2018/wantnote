package com.davidzhu.wantnote.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 0.18.2：验证远程 MCP（Streamable HTTP / JSON-RPC 2.0）SSE 解包、工具自动发现与远端笔记提取。
 */
class McpSyncProtocolTest {

    @Test
    fun unwrapSseOrJsonExtractsJsonFromSseStream() {
        val sse = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}\n\n"
        val unwrapped = McpSyncService.unwrapSseOrJson(sse)
        val obj = JSONObject(unwrapped)
        assertEquals(1, obj.getInt("id"))
    }

    @Test
    fun toolDiscoveryPicksReadAndWriteTools() {
        val result = JSONObject(
            """
            {
              "tools": [
                {"name": "list_memos", "description": "List memos"},
                {"name": "create_memo", "description": "Create memo"}
              ]
            }
            """.trimIndent(),
        )
        val tools = McpSyncService.parseToolsList(result)
        assertEquals(2, tools.size)
        val readTool = McpSyncService.pickReadTool(tools)
        val writeTool = McpSyncService.pickWriteTool(tools)
        assertNotNull(readTool)
        assertNotNull(writeTool)
        assertEquals("list_memos", readTool!!.name)
        assertEquals("create_memo", writeTool!!.name)
    }

    @Test
    fun extractRemoteNotesParsesJsonPayloadFromMcpContent() {
        val callResult = JSONObject(
            """
            {
              "content": [
                {
                  "type": "text",
                  "text": "{\"notes\":[{\"title\":\"得到读书笔记\",\"content\":\"复利思维很重要\"}]}"
                }
              ]
            }
            """.trimIndent(),
        )
        val notes = McpSyncService.extractRemoteNotes(callResult)
        assertEquals(1, notes.size)
        assertEquals("得到读书笔记", notes.first().title)
        assertEquals("复利思维很重要", notes.first().body)
    }
}
