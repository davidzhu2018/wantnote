package com.davidzhu.wantnote.sync.dinox

import com.davidzhu.wantnote.transfer.TransferException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Dinox 开放接口的 HTTP 客户端（OkHttp）。
 *
 * - Token 只出现在请求头 `Authorization`，绝不进 URL、日志或异常文本；
 *   异常消息只保留简短的非敏感提示（网络类型/HTTP 状态/错误码）。
 * - 限流（200 次/小时）由调用方通过增量同步控制，这里不做缓存。
 */
class DinoxClient(
    private val baseUrl: String = DinoxModels.BASE_URL,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** POST /api/openapi/listNotes；成功返回原始 JSON，失败抛 [DinoxApiException]。 */
    fun listNotes(token: String, bodyJson: String): String {
        return post(token, DinoxModels.PATH_LIST_NOTES, bodyJson)
    }

    /** POST /api/openapi/text/input；成功返回原始 JSON。 */
    fun createNote(token: String, bodyJson: String): String {
        return post(token, DinoxModels.PATH_TEXT_INPUT, bodyJson)
    }

    private fun post(token: String, path: String, bodyJson: String): String {
        if (token.isBlank()) {
            throw DinoxApiException("尚未配置 Dinox Token")
        }
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", DinoxModels.authHeader(token))
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (error: IOException) {
            throw DinoxApiException("网络错误：${error.javaClass.simpleName}，请检查网络后重试")
        }
        return response.use { resp ->
            val bodyText = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
            if (!resp.isSuccessful) {
                throw DinoxApiException("网络错误（HTTP ${resp.code}）")
            }
            val root = runCatching { org.json.JSONObject(bodyText) }.getOrNull()
                ?: throw DinoxApiException("Dinox 返回了无法解析的数据")
            val code = root.optString("code")
            if (code != DinoxModels.CODE_SUCCESS) {
                throw DinoxApiException(DinoxModels.errorMessage(code), code)
            }
            bodyText
        }
    }
}

/** 把底层异常包装成面向用户的短消息（去掉堆栈、URL 与可能的敏感字段）。 */
fun dinoxShortMessage(error: Throwable): String {
    return when (error) {
        is DinoxApiException -> error.message ?: "Dinox 请求失败"
        is TransferException -> error.message ?: "请求失败"
        is IllegalArgumentException -> error.message ?: "参数不完整"
        else -> error.message?.take(80)?.ifBlank { null } ?: error.javaClass.simpleName
    }
}
