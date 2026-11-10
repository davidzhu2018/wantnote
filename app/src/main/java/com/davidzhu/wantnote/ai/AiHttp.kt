package com.davidzhu.wantnote.ai

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal data class AiHttpResponse(
    val code: Int,
    val body: String,
) {
    val ok: Boolean get() = code in 200..299
}

/**
 * Minimal JSON HTTP helper. Never logs Authorization values or API keys.
 */
internal object AiHttp {
    fun joinUrl(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        val p = path.trim().trimStart('/')
        return "$b/$p"
    }

    fun get(url: String, bearer: String, connectTimeoutMs: Int = 15_000, readTimeoutMs: Int = 30_000): AiHttpResponse {
        return request("GET", url, bearer, body = null, connectTimeoutMs, readTimeoutMs)
    }

    fun postJson(
        url: String,
        bearer: String,
        json: String,
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 90_000,
    ): AiHttpResponse {
        return request("POST", url, bearer, body = json, connectTimeoutMs, readTimeoutMs)
    }

    private fun request(
        method: String,
        url: String,
        bearer: String,
        body: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): AiHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            if (bearer.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $bearer")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { out ->
                    out.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            return AiHttpResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }

    fun userSafeMessage(throwable: Throwable, apiKey: String): String {
        val raw = throwable.message.orEmpty()
        return redact(raw.ifBlank { throwable.javaClass.simpleName }, apiKey)
    }

    fun redact(text: String, apiKey: String): String {
        var out = text
        val key = apiKey.trim()
        if (key.length >= 4) {
            out = out.replace(key, "***")
        }
        out = out.replace(Regex("(?i)(Bearer\\s+)\\S+"), "$1***")
        return out
    }

    fun shortErrorBody(body: String, apiKey: String): String {
        val trimmed = redact(body.trim().replace("\\s+".toRegex(), " "), apiKey)
        return trimmed.take(180)
    }
}
