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

    /**
     * Short, user-facing failure reason for a failed PROPFIND (connection
     * test). Never includes the server's raw response body (often an XML
     * error document) — just the status code plus actionable Chinese hints,
     * with special-casing for the two most common 坚果云 mistakes.
     */
    private fun propfindFailure(code: Int): String = when (code) {
        401 -> "无法连接 WebDAV（HTTP 401）：用户名或密码不正确。" +
            "坚果云需要使用网页端「账户信息 → 安全选项」里生成的「第三方应用密码」，而不是登录密码。"
        403 -> "无法连接 WebDAV（HTTP 403）：服务器拒绝了请求。" +
            "请检查服务器地址是否包含 /dav/（坚果云应填 https://dav.jianguoyun.com/dav/），" +
            "并确认使用的是坚果云「第三方应用密码」而不是网页登录密码。"
        404 -> "无法连接 WebDAV（HTTP 404）：地址不存在。请确认服务器地址和路径是否正确。"
        in 500..599 -> "无法连接 WebDAV（HTTP $code）：服务器暂时出错，请稍后重试。"
        else -> "无法连接 WebDAV（HTTP $code）"
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
        val code = propfindCode(relative, depth = 0)
        return code in 200..299
    }

    private fun propfind(relative: String, depth: Int) {
        val code = propfindCode(relative, depth)
        if (code !in 200..299) {
            throw SyncException(propfindFailure(code))
        }
    }

    /**
     * PROPFIND with a standard, explicit body (resourcetype etc.). Some
     * servers — including 坚果云 — are picky about empty-body PROPFIND or
     * non-canonical URLs, so the request always sends a well-formed XML
     * payload and a proper Content-Type.
     */
    private fun propfindCode(relative: String, depth: Int): Int {
        val body = PROPFIND_BODY.toByteArray(StandardCharsets.UTF_8)
        return request(
            "PROPFIND",
            relative,
            body = body.inputStream(),
            contentType = "application/xml; charset=utf-8",
            contentLength = body.size.toLong(),
            extraHeaders = mapOf("Depth" to depth.toString()),
        ).code
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
        val cleaned = rel.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }.joinToString("/")
        return if (cleaned.isEmpty()) root else "$root$cleaned"
    }

    private fun suffix(body: String): String {
        val trimmed = body.trim()
        // Never surface raw XML error documents to the user.
        if (trimmed.isEmpty() || trimmed.startsWith("<") || trimmed.startsWith("<?xml")) return ""
        val short = Redacting.message(Exception(trimmed.replace("\\s+".toRegex(), " ").take(160)), secrets)
        return if (short.isBlank()) "" else "：$short"
    }

    private fun readStream(stream: InputStream?): String {
        if (stream == null) return ""
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    companion object {
        private val PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<d:propfind xmlns:d=\"DAV:\">" +
                "<d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop>" +
                "</d:propfind>"

        fun normalizeBase(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) throw SyncException("请填写 WebDAV 服务器地址")
            if (!value.contains("://")) value = "https://$value"
            // Canonicalize the path part: collapse duplicate slashes, drop
            // dot segments, and guarantee exactly one trailing slash so that
            // https://dav.jianguoyun.com/dav (no slash) and
            // https://dav.jianguoyun.com//dav/ both resolve identically.
            val schemeEnd = value.indexOf("://") + 3
            val head = value.substring(0, schemeEnd)
            var tail = value.substring(schemeEnd).replace(Regex("/{2,}"), "/")
            val segments = tail.split('/').filter { it.isNotEmpty() && it != "." }
            if (segments.any { it == ".." }) throw SyncException("服务器地址包含不支持的路径")
            tail = "/" + segments.joinToString("/")
            if (!tail.endsWith("/")) tail += "/"
            return head + tail
        }
    }
}
