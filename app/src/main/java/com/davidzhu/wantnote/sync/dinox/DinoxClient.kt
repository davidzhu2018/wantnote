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
 * - 列卡片盒走轻量 `GET /api/openapi/zettelboxes`（15s 短超时），
 *   绝不在选择器路径上触发全量 `listNotes(null)`。
 * - 响应信封校验委托给 [DinoxModels.validateEnvelope]，防御性兼容合法的变体响应。
 */
open class DinoxClient(
    private val baseUrl: String = DinoxModels.BASE_URL,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val lightweightHttp: OkHttpClient = http.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * GET /api/openapi/zettelboxes：轻量获取用户全部 Dinox 卡片盒（含空盒）。
     * 使用短超时（15s）；成功返回原始 JSON，失败抛 [DinoxApiException]。
     */
    open fun listZettelBoxes(token: String): String {
        return get(token, DinoxModels.PATH_ZETTEL_BOXES, lightweight = true)
    }

    /** POST /api/openapi/listNotes；成功返回原始 JSON，失败抛 [DinoxApiException]。 */
    open fun listNotes(token: String, bodyJson: String): String {
        return post(token, DinoxModels.PATH_LIST_NOTES, bodyJson)
    }

    /** POST /api/openapi/text/input；成功返回原始 JSON。 */
    open fun createNote(token: String, bodyJson: String): String {
        return post(token, DinoxModels.PATH_TEXT_INPUT, bodyJson)
    }

    private fun get(token: String, path: String, lightweight: Boolean): String {
        if (token.isBlank()) {
            throw DinoxApiException("尚未配置 Dinox Token")
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", DinoxModels.authHeader(token))
            .get()
            .build()
        return execute(request, if (lightweight) lightweightHttp else http)
    }

    private fun post(token: String, path: String, bodyJson: String): String {
        if (token.isBlank()) {
            throw DinoxApiException("尚未配置 Dinox Token")
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", DinoxModels.authHeader(token))
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()
        return execute(request, http)
    }

    private fun execute(request: Request, client: OkHttpClient): String {
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw DinoxApiException("网络错误：${error.javaClass.simpleName}，请检查网络后重试")
        }
        return response.use { resp ->
            if (!resp.isSuccessful) {
                val message = when (resp.code) {
                    401, 403 -> "Token 无效或已过期（HTTP ${resp.code}）"
                    404 -> "Dinox 接口不可用（HTTP 404）"
                    429 -> "Dinox 请求过于频繁，请稍后再试（HTTP 429）"
                    else -> "网络错误（HTTP ${resp.code}）"
                }
                throw DinoxApiException(message)
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

    companion object {
        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
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
