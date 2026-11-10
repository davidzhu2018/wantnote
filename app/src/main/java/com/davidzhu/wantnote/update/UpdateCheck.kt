package com.davidzhu.wantnote.update

import com.davidzhu.wantnote.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val htmlUrl: String,
    val apkUrl: String?,
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class OpenReleases(val reason: String, val url: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object UpdateCheck {
    const val OWNER = "davidzhu2018"
    const val REPO = "wantnote"
    const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=30"

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "WantNote/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = connection.responseCode
            if (code == 401 || code == 403 || code == 404 || code == 451) {
                return@withContext UpdateCheckResult.OpenReleases(
                    reason = when (code) {
                        404, 401 -> "仓库可能是私有的，无法在未登录时查询。已为你打开 GitHub Releases 页面。"
                        else -> "GitHub 暂时无法访问（HTTP $code）。已为你打开 Releases 页面。"
                    },
                    url = RELEASES_PAGE,
                )
            }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (code !in 200..299) {
                return@withContext UpdateCheckResult.Error("检查更新失败（HTTP $code）")
            }
            val arr = runCatching { JSONArray(body) }.getOrNull()
                ?: return@withContext UpdateCheckResult.Error("无法解析 GitHub 返回的版本列表")
            val newest = pickNewest(arr)
            if (newest == null) {
                return@withContext UpdateCheckResult.UpToDate
            }
            val cmp = compareVersions(newest.versionName, BuildConfig.VERSION_NAME)
            if (cmp > 0) {
                UpdateCheckResult.Available(newest)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (error: Exception) {
            UpdateCheckResult.OpenReleases(
                reason = "无法连接 GitHub（${error.javaClass.simpleName}）。已为你打开 Releases 页面。",
                url = RELEASES_PAGE,
            )
        } finally {
            connection.disconnect()
        }
    }

    internal fun pickNewest(arr: JSONArray): UpdateInfo? {
        val items = mutableListOf<UpdateInfo>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft")) continue
            val tag = obj.optString("tag_name")
            val version = versionFromTag(tag) ?: continue
            val assets = obj.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    val name = asset.optString("name").lowercase()
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".apk") && url.isNotBlank()) {
                        apk = url
                        break
                    }
                }
            }
            items += UpdateInfo(
                tagName = tag,
                versionName = version,
                releaseName = obj.optString("name").ifBlank { tag },
                htmlUrl = obj.optString("html_url").ifBlank { RELEASES_PAGE },
                apkUrl = apk,
            )
        }
        return items.maxWithOrNull { a, b -> compareVersions(a.versionName, b.versionName) }
    }

    internal fun versionFromTag(tag: String): String? {
        val trimmed = tag.trim().removePrefix("v").removePrefix("V")
        val android = Regex("^android-(\\d+(?:\\.\\d+)*)$", RegexOption.IGNORE_CASE)
        android.matchEntire(trimmed)?.let { return it.groupValues[1] }
        val plain = Regex("^(\\d+(?:\\.\\d+)*)$")
        return plain.matchEntire(trimmed)?.groupValues?.get(1)
    }

    internal fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val da = pa.getOrElse(i) { 0 }
            val db = pb.getOrElse(i) { 0 }
            if (da != db) return da.compareTo(db)
        }
        return 0
    }
}
