package com.davidzhu.wantnote.sync

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class S3Client(
    endpoint: String,
    private val bucket: String,
    region: String,
    private val accessKey: String,
    private val secretKey: String,
    prefix: String,
) {
    private val region = region.trim().ifBlank { "us-east-1" }
    private val prefix = prefix.trim().trim('/')
    private val endpointUrl = normalizeEndpoint(endpoint)
    private val virtualHosted = isAwsHost(endpointUrl.host)
    private val secrets = listOf(accessKey, secretKey).filter { it.isNotBlank() }

    fun objectKey(relative: String): String {
        val name = relative.trim().trimStart('/')
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    fun test() {
        // List a single key under the configured prefix; an empty bucket is still success.
        val response = signedRequest(
            method = "GET",
            key = "",
            query = listOf("list-type" to "2", "max-keys" to "1", "prefix" to (if (prefix.isEmpty()) "" else "$prefix/")),
            body = null,
            contentType = null,
        )
        if (response.code !in 200..299) {
            throw SyncException("无法连接 S3（HTTP ${response.code}）${suffix(response.body)}")
        }
    }

    fun putFile(relative: String, file: File) {
        file.inputStream().use { input ->
            put(relative, input, file.length(), "application/octet-stream")
        }
    }

    fun putText(relative: String, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        put(relative, bytes.inputStream(), bytes.size.toLong(), "application/json; charset=utf-8")
    }

    fun put(relative: String, input: InputStream, length: Long, contentType: String) {
        val payloadHash = "UNSIGNED-PAYLOAD"
        val response = signedRequest(
            method = "PUT",
            key = objectKey(relative),
            query = emptyList(),
            body = input,
            contentType = contentType,
            contentLength = length,
            payloadHash = payloadHash,
        )
        if (response.code !in 200..299) {
            throw SyncException("上传失败（HTTP ${response.code}）${suffix(response.body)}")
        }
    }

    fun getToFile(relative: String, dest: File) {
        dest.parentFile?.mkdirs()
        val built = buildConnection(
            method = "GET",
            key = objectKey(relative),
            query = emptyList(),
            contentType = null,
            contentLength = null,
            payloadHash = emptySha256(),
        )
        try {
            val code = built.responseCode
            if (code !in 200..299) {
                val err = readStream(built.errorStream)
                built.disconnect()
                throw SyncException("下载失败（HTTP $code）${suffix(err)}")
            }
            built.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            built.disconnect()
        }
    }

    fun getText(relative: String): String {
        val response = signedRequest(
            method = "GET",
            key = objectKey(relative),
            query = emptyList(),
            body = null,
            contentType = null,
        )
        if (response.code !in 200..299) {
            throw SyncException("下载失败（HTTP ${response.code}）${suffix(response.body)}")
        }
        return response.body
    }

    private data class Response(val code: Int, val body: String)

    private fun signedRequest(
        method: String,
        key: String,
        query: List<Pair<String, String>>,
        body: InputStream?,
        contentType: String?,
        contentLength: Long? = null,
        payloadHash: String = emptySha256(),
    ): Response {
        val connection = buildConnection(method, key, query, contentType, contentLength, payloadHash)
        try {
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { output -> body.copyTo(output) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return Response(code, readStream(stream))
        } catch (error: SyncException) {
            throw error
        } catch (error: Exception) {
            throw SyncException(Redacting.message(error, secrets))
        } finally {
            connection.disconnect()
        }
    }

    private fun buildConnection(
        method: String,
        key: String,
        query: List<Pair<String, String>>,
        contentType: String?,
        contentLength: Long?,
        payloadHash: String,
    ): HttpURLConnection {
        val now = Date()
        val amzDate = amzDateFormat.format(now)
        val dateStamp = dateStampFormat.format(now)
        val url = objectUrl(key, query)
        val host = URL(url).host
        val canonicalUri = canonicalUri(key)
        val canonicalQuery = canonicalQuery(query)
        val signedHeaders = if (contentType != null) "content-type;host;x-amz-content-sha256;x-amz-date" else "host;x-amz-content-sha256;x-amz-date"
        val canonicalHeaders = buildString {
            if (contentType != null) append("content-type:").append(contentType.trim()).append('\n')
            append("host:").append(host).append('\n')
            append("x-amz-content-sha256:").append(payloadHash).append('\n')
            append("x-amz-date:").append(amzDate).append('\n')
        }
        val canonicalRequest = listOf(
            method,
            canonicalUri,
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            payloadHash,
        ).joinToString("\n")
        val scope = "$dateStamp/$region/s3/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            scope,
            sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8)),
        ).joinToString("\n")
        val signingKey = signingKey(secretKey, dateStamp, region, "s3")
        val signature = bytesToHex(hmacSha256(signingKey, stringToSign))
        val authorization = "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, SignedHeaders=$signedHeaders, Signature=$signature"

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("Authorization", authorization)
            setRequestProperty("x-amz-date", amzDate)
            setRequestProperty("x-amz-content-sha256", payloadHash)
            setRequestProperty("Host", host)
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (contentLength != null && contentLength >= 0 && method == "PUT") {
                setFixedLengthStreamingMode(contentLength)
            }
        }
        return connection
    }

    private fun objectUrl(key: String, query: List<Pair<String, String>>): String {
        val path = if (virtualHosted) {
            if (key.isEmpty()) "/" else "/${key.trimStart('/')}"
        } else {
            val suffix = if (key.isEmpty()) "" else "/${key.trimStart('/')}"
            "/$bucket$suffix"
        }
        val base = if (virtualHosted) {
            "${endpointUrl.protocol}://$bucket.${endpointUrl.host}${portSuffix(endpointUrl)}$path"
        } else {
            "${endpointUrl.protocol}://${endpointUrl.host}${portSuffix(endpointUrl)}$path"
        }
        val q = canonicalQuery(query)
        return if (q.isEmpty()) base else "$base?$q"
    }

    private fun canonicalUri(key: String): String {
        val raw = if (virtualHosted) {
            if (key.isEmpty()) "/" else "/${key.trimStart('/')}"
        } else {
            val suffix = if (key.isEmpty()) "" else "/${key.trimStart('/')}"
            "/$bucket$suffix"
        }
        return raw.split('/').joinToString("/") { part ->
            if (part.isEmpty()) "" else uriEncode(part, encodeSlash = true)
        }
    }

    private fun canonicalQuery(query: List<Pair<String, String>>): String {
        return query
            .filterNot { it.first.isBlank() && it.second.isBlank() }
            .sortedBy { it.first }
            .joinToString("&") { (k, v) -> "${uriEncode(k, true)}=${uriEncode(v, true)}" }
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
        private val amzDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val dateStampFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun normalizeEndpoint(raw: String): URL {
            var value = raw.trim()
            if (value.isEmpty()) throw SyncException("请填写 S3 Endpoint")
            if (!value.contains("://")) value = "https://$value"
            return URL(value)
        }

        fun isAwsHost(host: String): Boolean {
            val h = host.lowercase(Locale.US)
            return h == "amazonaws.com" || h.endsWith(".amazonaws.com") || h.endsWith(".amazonaws.com.cn")
        }

        fun portSuffix(url: URL): String {
            return if (url.port == -1 || url.port == url.defaultPort) "" else ":${url.port}"
        }

        fun emptySha256(): String = sha256Hex(ByteArray(0))

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return bytesToHex(digest)
        }

        fun hmacSha256(key: ByteArray, data: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        }

        fun signingKey(secret: String, dateStamp: String, region: String, service: String): ByteArray {
            val kDate = hmacSha256("AWS4$secret".toByteArray(StandardCharsets.UTF_8), dateStamp)
            val kRegion = hmacSha256(kDate, region)
            val kService = hmacSha256(kRegion, service)
            return hmacSha256(kService, "aws4_request")
        }

        fun uriEncode(value: String, encodeSlash: Boolean): String {
            val encoded = URLEncoder.encode(value, "UTF-8")
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~")
            return if (encodeSlash) encoded else encoded.replace("%2F", "/")
        }

    }
}

private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
