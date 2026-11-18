package com.davidzhu.wantnote.sync

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class SyncException(message: String) : Exception(message)

/**
 * Minimal WebDAV client.
 *
 * 0.7.0 起改用 OkHttp 的 [Request.Builder.method] 发送请求。旧实现依赖
 * `HttpURLConnection.setRequestMethod("PROPFIND")` + `doOutput = true`：
 * Android 对非标准动词的方法白名单检查很严格，一旦打开输出流，
 * 请求就可能被底层改写成 POST，于是坚果云返回 501（POST 不被 WebDAV
 * 处理）。OkHttp 允许显式指定任意动词，并能正确携带 body。
 *
 * 认证只放在 Authorization 头里，不会写入日志或 UI。
 */
internal class WebDavClient(
    baseUrl: String,
    private val username: String,
    private val password: String,
) {
    private val root = normalizeBase(baseUrl)
    private val secrets = listOf(password, username).filter { it.isNotBlank() }
    private val authorization = Credentials.basic(
        username,
        password,
        StandardCharsets.UTF_8,
    )
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun test() {
        // Depth: 0 + 标准 PROPFIND body。207/2xx 都算成功（坚果云返回 207）。
        propfind("", depth = 0)
    }

    /**
     * 用户可见的失败原因。只给状态码 + 中文指引，绝不把服务器返回的 XML
     * 原文或密码展示出来。
     */
    private fun propfindFailure(code: Int): String = when (code) {
        401 -> "无法连接 WebDAV（HTTP 401）：用户名或密码不正确。" +
            "坚果云需要使用网页端「账户信息 → 安全选项」里生成的「第三方应用密码」，而不是登录密码。"
        403 -> "无法连接 WebDAV（HTTP 403）：服务器拒绝了请求。" +
            "请检查服务器地址是否包含 /dav/（坚果云应填 https://dav.jianguoyun.com/dav/），" +
            "并确认使用的是坚果云「第三方应用密码」而不是网页登录密码。"
        404 -> "无法连接 WebDAV（HTTP 404）：地址不存在。请确认服务器地址和路径是否正确。"
        405 -> "无法连接 WebDAV（HTTP 405）：服务器不允许 PROPFIND 请求，该地址可能不是 WebDAV 目录。" +
            "请确认地址以 /dav/ 结尾。"
        501 -> "无法连接 WebDAV（HTTP 501）：服务器没有识别 PROPFIND 方法（请求未按 WebDAV 发出）。" +
            "请确认地址以 /dav/ 结尾，且服务器支持 WebDAV。"
        in 500..599 -> "无法连接 WebDAV（HTTP $code）：服务器暂时出错，请稍后重试。"
        else -> "无法连接 WebDAV（HTTP $code）"
    }

    fun ensureFolder(relative: String) {
        val parts = relative.trim('/').split('/').filter { it.isNotEmpty() }
        var acc = ""
        for (part in parts) {
            acc = if (acc.isEmpty()) part else "$acc/$part"
            val code = execute(
                method = "MKCOL",
                url = join(acc),
                body = null,
                extraHeaders = emptyMap(),
            ).code
            // 200/201/204 新建成功；405/409 在多数服务器上表示目录已存在，
            // 顺序创建父目录后继续即可。
            if (code !in 200..299 && code != 405 && code != 409) {
                throw SyncException(
                    when (code) {
                        401 -> "无法创建远程目录（HTTP 401）：用户名或密码不正确。"
                        403 -> "无法创建远程目录（HTTP 403）：服务器拒绝写入，请检查账号权限。"
                        else -> "无法创建远程目录（HTTP $code）"
                    }
                )
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
        val mediaType = contentType.toMediaType()
        val body = object : RequestBody() {
            override fun contentType() = mediaType

            override fun contentLength(): Long = length

            override fun writeTo(sink: BufferedSink) {
                input.source().use { source -> sink.writeAll(source) }
            }
        }
        val response = execute(
            method = "PUT",
            url = join(relative),
            body = body,
            extraHeaders = emptyMap(),
        )
        if (response.code !in 200..299) {
            throw SyncException("上传失败（HTTP ${response.code}）${suffix(response.body)}")
        }
    }

    fun getToFile(relative: String, dest: File) {
        dest.parentFile?.mkdirs()
        val request = requestBuilder("GET", join(relative), body = null).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code !in 200..299) {
                    val err = response.body?.string().orEmpty()
                    throw SyncException("下载失败（HTTP ${response.code}）${suffix(err)}")
                }
                val stream = response.body?.byteStream()
                    ?: throw SyncException("下载失败：服务器没有返回内容")
                stream.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (error: SyncException) {
            throw error
        } catch (error: Exception) {
            throw SyncException(Redacting.message(error, secrets))
        }
    }

    fun getText(relative: String): String {
        val response = execute(
            method = "GET",
            url = join(relative),
            body = null,
            extraHeaders = emptyMap(),
        )
        if (response.code !in 200..299) {
            throw SyncException("下载失败（HTTP ${response.code}）${suffix(response.body)}")
        }
        return response.body
    }

    fun exists(relative: String): Boolean {
        return propfindCode(relative, depth = 0) in 200..299
    }

    private fun propfind(relative: String, depth: Int) {
        val code = propfindCode(relative, depth)
        if (code !in 200..299) {
            throw SyncException(propfindFailure(code))
        }
    }

    /**
     * 真正用 PROPFIND 动词发送请求。OkHttp 的 method() 不会改写动词，
     * body 会按 Content-Length 正常写出。
     */
    private fun propfindCode(relative: String, depth: Int): Int {
        val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
        val response = execute(
            method = "PROPFIND",
            url = join(relative),
            body = body,
            extraHeaders = mapOf("Depth" to depth.toString()),
        )
        return response.code
    }

    private data class Response(val code: Int, val body: String)

    private fun execute(
        method: String,
        url: String,
        body: RequestBody?,
        extraHeaders: Map<String, String>,
    ): Response {
        val request = requestBuilder(method, url, body).apply {
            extraHeaders.forEach { (key, value) -> header(key, value) }
        }.build()
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                Response(response.code, text)
            }
        } catch (error: SyncException) {
            throw error
        } catch (error: Exception) {
            throw SyncException(Redacting.message(error, secrets))
        }
    }

    private fun requestBuilder(method: String, url: String, body: RequestBody?): Request.Builder {
        return Request.Builder()
            .url(url)
            .method(method, body)
            .header("Authorization", authorization)
            .header("Accept", "*/*")
            .header("User-Agent", "WantNote-Android")
    }

    private fun join(relative: String): String {
        val rel = relative.trim().trimStart('/')
        val cleaned = rel.split('/')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")
        return if (cleaned.isEmpty()) root else "$root$cleaned"
    }

    private fun suffix(body: String): String {
        val trimmed = body.trim()
        // 不把原始 XML 错误文档展示给用户。
        if (trimmed.isEmpty() || trimmed.startsWith("<") || trimmed.startsWith("<?xml")) return ""
        val short = Redacting.message(
            Exception(trimmed.replace("\\s+".toRegex(), " ").take(160)),
            secrets,
        )
        return if (short.isBlank()) "" else "：$short"
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
            // 规范化路径：折叠重复斜杠、去掉点段、保证恰好一个结尾斜杠，
            // 让 https://dav.jianguoyun.com/dav 与 …/dav/ 等价。
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
