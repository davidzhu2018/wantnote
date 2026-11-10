package com.davidzhu.wantnote.sync

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import android.util.Base64

class SyncException(message: String) : Exception(message)

internal class WebDavClient(
    baseUrl: String,
    private val username: String,
    private val password: String,
) {
    private val root = normalizeBase(baseUrl)
    private val secrets = listOf(password, username).filter { it.isNotBlank() }

    fun test() {
        propfind("", depth = 0)
    }

    fun ensureFolder(relative: String) {
        val parts = relative.trim('/').split('/').filter { it.isNotEmpty() }
        var acc = ""
        for (part in parts) {
            acc = if (acc.isEmpty()) part else "$acc/$part"
            val code = request("MKCOL", acc, body = null, contentType = null).code
            if (code !in listOf(201, 204, 405, 409, 301, 200)) {
                if (code != 301 && code != 200 && code !in 200..299) {
                    // 405 = already exists on many servers; 409 may mean parent missing
                    if (code == 405 || code == 409) continue
                    throw SyncException("无法创建远程目录（HTTP $code）")
                }
            }
        }
    }

    fun put(relative: String, file: File) {
        file.inputStream().use { input ->
            put(relative, input, file.length(), "application/octet-stream")
        }
    }

    fun putText(relative: String, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        put(relative, bytes.inputStream(), bytes.size.toLong(), "application/json; charset=utf-8")
    }

    fun put(relative: String, input: InputStream, length: Long, contentType: String) {
        val response = request(
            method = "PUT",
            relative = relative,
            body = input,
            contentType = contentType,
            contentLength = length,
        )
        if (response.code !in 200..299) {
            throw SyncException("上传失败（HTTP ${response.code}）${suffix(response.body)}")
        }
    }

    fun getToFile(relative: String, dest: File) {
        dest.parentFile?.mkdirs()
        val url = join(relative)
        val connection = open(url, "GET")
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = readStream(connection.errorStream)
                throw SyncException("下载失败（HTTP $code）${suffix(err)}")
            }
            connection.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    fun getText(relative: String): String {
        val response = request("GET", relative, body = null, contentType = null)
        if (response.code !in 200..299) {
            throw SyncException("下载失败（HTTP ${response.code}）${suffix(response.body)}")
        }
        return response.body
    }

    fun exists(relative: String): Boolean {
        val code = request("PROPFIND", relative, body = null, contentType = null, extraHeaders = mapOf("Depth" to "0")).code
        return code in 200..299
    }

    private fun propfind(relative: String, depth: Int) {
        val response = request(
            "PROPFIND",
            relative,
            body = null,
            contentType = "application/xml",
            extraHeaders = mapOf("Depth" to depth.toString()),
        )
        if (response.code !in 200..299) {
            throw SyncException("无法连接 WebDAV（HTTP ${response.code}）${suffix(response.body)}")
        }
    }

    private data class Response(val code: Int, val body: String)

    private fun request(
        method: String,
        relative: String,
        body: InputStream?,
        contentType: String?,
        contentLength: Long? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Response {
        val connection = open(join(relative), method)
        try {
            if (contentType != null) {
                connection.setRequestProperty("Content-Type", contentType)
            }
            extraHeaders.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                if (contentLength != null && contentLength >= 0) {
                    connection.setFixedLengthStreamingMode(contentLength)
                }
                connection.outputStream.use { output -> body.copyTo(output) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = readStream(stream)
            return Response(code, text)
        } catch (error: Exception) {
            throw SyncException(Redacting.message(error, secrets))
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("Accept", "*/*")
            val token = Base64.encodeToString(
                "$username:$password".toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP,
            )
            setRequestProperty("Authorization", "Basic $token")
        }
        try {
            connection.requestMethod = method
        } catch (_: Exception) {
            // Some Android versions reject uncommon verbs via setter only.
            try {
                val field = HttpURLConnection::class.java.getDeclaredField("method")
                field.isAccessible = true
                field.set(connection, method)
            } catch (error: Exception) {
                throw SyncException("当前系统不支持 $method 请求")
            }
        }
        return connection
    }

    private fun join(relative: String): String {
        val rel = relative.trim().trimStart('/')
        return if (rel.isEmpty()) root else "$root$rel"
    }

    private fun suffix(body: String): String {
        val short = Redacting.message(Exception(body.trim().replace("\\s+".toRegex(), " ").take(160)), secrets)
        return if (short.isBlank()) "" else "：$short"
    }

    private fun readStream(stream: InputStream?): String {
        if (stream == null) return ""
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    companion object {
        fun normalizeBase(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) throw SyncException("请填写 WebDAV 服务器地址")
            if (!value.contains("://")) value = "https://$value"
            if (!value.endsWith("/")) value += "/"
            return value
        }
    }
}
