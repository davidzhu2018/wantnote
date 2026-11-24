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
 * - 响应体读取期间的 IO 异常（如大包超时/断流）单独捕获为网络错误，
 *   不再误报为「无法解析的数据」。
 * - 响应信封校验委托给 [DinoxModels.validateEnvelope]，防御性兼容合法的变体响应。
 */
class DinoxClient(
    private val baseUrl: String = DinoxModels.BASE_URL,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
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
            if (!resp.isSuccessful) {
                throw DinoxApiException("网络错误（HTTP ${resp.code}）")
            }
            val bodyText = try {
                resp.body?.string().orEmpty()
            } catch (error: IOException) {
                throw DinoxApiException("网络超时或连接中断（${error.javaClass.simpleName}），请稍后重试")
            }
            DinoxModels.validateEnvelope(bodyText)
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
