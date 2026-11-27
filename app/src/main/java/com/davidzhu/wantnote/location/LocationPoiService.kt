package com.davidzhu.wantnote.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.coroutines.resume

/** 支持的地图 POI 服务提供商。 */
enum class MapProvider(val label: String, val keyConsoleUrl: String) {
    AMAP("高德地图", "https://console.amap.com/dev/key/app"),
    TENCENT("腾讯地图", "https://lbs.qq.com/dev/console/application/mine"),
    ;

    companion object {
        fun fromStorage(value: String?): MapProvider {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AMAP
        }
    }
}

/** 单个可选 POI 地点条目。 */
data class PoiItem(
    val id: String,
    val name: String,
    val address: String,
    val city: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val distanceMeters: Int? = null,
) {
    /** 朋友圈与笔记详情的紧凑展示文案（如「上海市 · 陆家嘴中心」）。 */
    fun displayLabel(): String {
        val cleanCity = city.trim()
        val cleanName = name.trim()
        return if (cleanCity.isNotEmpty() && !cleanName.startsWith(cleanCity)) {
            "$cleanCity · $cleanName"
        } else {
            cleanName
        }
    }

    fun formattedDistance(): String {
        val d = distanceMeters ?: return ""
        return when {
            d < 0 -> ""
            d < 1000 -> "${d}m"
            else -> String.format(Locale.US, "%.1fkm", d / 1000.0)
        }
    }
}

data class PoiSearchResult(
    val items: List<PoiItem>,
    val sourceLabel: String,
    val hint: String? = null,
)

/**
 * 0.18.0：位置与 POI 检索服务。
 *
 * - 支持 **高德地图（AMap）** Web 服务 POI 周边/关键词搜索 API；
 * - 支持 **腾讯地图（Tencent Map）** 位置服务 POI 周边/关键词搜索 API；
 * - 未配置高德/腾讯 Key 时自动回退到系统 `Geocoder` + 开放地理编码服务（Nominatim），
 *   确保开箱即用，配置 Key 后即可获得高德/腾讯原厂精准中文 POI。
 */
object LocationPoiService {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentCoordinates(context: Context): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        // 1. 先尝试快速取最近有效定位
        val cached = providers.mapNotNull { p ->
            runCatching { manager.getLastKnownLocation(p) }.getOrNull()
        }.maxByOrNull { it.time }
        if (cached != null && (System.currentTimeMillis() - cached.time) < 10 * 60_000L) {
            return@withContext cached.latitude to cached.longitude
        }

        // 2. Android 11+ 请求一次新鲜定位（超时 5 秒兜底）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val activeProvider = providers.firstOrNull { p ->
                runCatching { manager.isProviderEnabled(p) }.getOrDefault(false)
            }
            if (activeProvider != null) {
                val fresh: Location? = withTimeoutOrNull(5_000L) {
                    suspendCancellableCoroutine { cont ->
                        runCatching {
                            manager.getCurrentLocation(activeProvider, null, context.mainExecutor) { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }
                        }.onFailure {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                if (fresh != null) return@withContext fresh.latitude to fresh.longitude
            }
        }
        cached?.let { it.latitude to it.longitude }
    }

    suspend fun searchPois(
        context: Context,
        provider: MapProvider,
        apiKey: String,
        keyword: String,
        coords: Pair<Double, Double>?,
    ): PoiSearchResult = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        val q = keyword.trim()

        if (key.isNotEmpty()) {
            val result = when (provider) {
                MapProvider.AMAP -> queryAmap(key, q, coords)
                MapProvider.TENCENT -> queryTencent(key, q, coords)
            }
            if (result != null) return@withContext result
        }

        // 未配置高德/腾讯 Key 或接口不可达时：自动使用系统 Geocoder + Nominatim 开放服务兜底
        val fallback = ArrayList<PoiItem>()
        if (coords != null) {
            fallback += reverseGeocodeAndroid(context, coords.first, coords.second)
        }
        val nominatim = if (q.isNotEmpty()) {
            queryNominatimSearch(q, coords)
        } else if (coords != null) {
            queryNominatimReverse(coords.first, coords.second)
        } else {
            emptyList()
        }
        nominatim.forEach { item ->
            if (fallback.none { it.name == item.name }) fallback += item
        }
        val hint = if (key.isEmpty()) {
            "当前使用系统定位兜底；点右上角「地图配置」填入${provider.label} Key 可获得更精准的中文 POI 列表。"
        } else {
            "${provider.label}接口暂时不可达，已切换为系统定位兜底。"
        }
        PoiSearchResult(
            items = fallback,
            sourceLabel = if (key.isEmpty()) "系统定位" else "${provider.label}（兜底）",
            hint = hint,
        )
    }

    private fun queryAmap(key: String, keyword: String, coords: Pair<Double, Double>?): PoiSearchResult? {
        val encKey = URLEncoder.encode(key, "UTF-8")
        val url = if (keyword.isEmpty() && coords != null) {
            // 高德周边 POI 搜索（location=lng,lat）
            "https://restapi.amap.com/v3/place/around?key=$encKey&location=${coords.second},${coords.first}&radius=3000&offset=20&page=1&extensions=base"
        } else {
            val encQ = URLEncoder.encode(keyword.ifBlank { "商圈" }, "UTF-8")
            val locParam = if (coords != null) "&location=${coords.second},${coords.first}" else ""
            "https://restapi.amap.com/v3/place/text?key=$encKey&keywords=$encQ&offset=20&page=1$locParam"
        }
        val body = httpGet(url) ?: return null
        val items = parseAmapPois(body)
        return PoiSearchResult(items = items, sourceLabel = "高德地图")
    }

    private fun queryTencent(key: String, keyword: String, coords: Pair<Double, Double>?): PoiSearchResult? {
        val encKey = URLEncoder.encode(key, "UTF-8")
        val url = if (keyword.isEmpty() && coords != null) {
            // 腾讯逆地理编码（含周边 POI 列表）
            "https://apis.map.qq.com/ws/geocoder/v1/?location=${coords.first},${coords.second}&get_poi=1&poi_options=radius=3000;page_size=20&key=$encKey"
        } else {
            val encQ = URLEncoder.encode(keyword.ifBlank { "广场" }, "UTF-8")
            val boundary = if (coords != null) {
                "nearby(${coords.first},${coords.second},5000,1)"
            } else {
                "region(全国,1)"
            }
            "https://apis.map.qq.com/ws/place/v1/search?keyword=$encQ&boundary=${URLEncoder.encode(boundary, "UTF-8")}&page_size=20&key=$encKey"
        }
        val body = httpGet(url) ?: return null
        val items = parseTencentPois(body)
        return PoiSearchResult(items = items, sourceLabel = "腾讯地图")
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeAndroid(context: Context, lat: Double, lng: Double): List<PoiItem> {
        return runCatching {
            val geocoder = Geocoder(context, Locale.CHINA)
            val list = geocoder.getFromLocation(lat, lng, 5).orEmpty()
            list.mapIndexedNotNull { idx, addr ->
                val city = addr.locality.orEmpty().ifBlank { addr.adminArea.orEmpty() }
                val name = addr.featureName?.takeIf { it.isNotBlank() && !it.all { ch -> ch.isDigit() } }
                    ?: addr.thoroughfare?.takeIf { it.isNotBlank() }
                    ?: addr.subLocality?.takeIf { it.isNotBlank() }
                    ?: addr.getAddressLine(0)
                    ?: return@mapIndexedNotNull null
                val full = addr.getAddressLine(0).orEmpty()
                PoiItem(
                    id = "geo-$idx",
                    name = name,
                    address = full,
                    city = city,
                    lat = addr.latitude,
                    lng = addr.longitude,
                    distanceMeters = 0,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun queryNominatimSearch(keyword: String, coords: Pair<Double, Double>?): List<PoiItem> {
        val enc = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$enc&format=json&addressdetails=1&limit=12&accept-language=zh-CN"
        val body = httpGet(url) ?: return emptyList()
        return parseNominatimArray(body, coords)
    }

    private fun queryNominatimReverse(lat: Double, lng: Double): List<PoiItem> {
        val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json&addressdetails=1&accept-language=zh-CN"
        val body = httpGet(url) ?: return emptyList()
        return parseNominatimSingle(body)
    }

    private fun httpGet(url: String): String? {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "WantNote-Android/0.18.0")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** 解析高德地图 `/v3/place/around` 与 `/v3/place/text` 返回的 JSON。 */
    internal fun parseAmapPois(json: String): List<PoiItem> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        if (root.optString("status") != "1") return emptyList()
        val pois = root.optJSONArray("pois") ?: return emptyList()
        val out = ArrayList<PoiItem>(pois.length())
        for (i in 0 until pois.length()) {
            val obj = pois.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isEmpty()) continue
            val addrRaw = obj.opt("address")
            val address = if (addrRaw is String) addrRaw.trim() else ""
            val city = obj.optString("cityname").trim()
            val loc = obj.optString("location").split(",")
            val lng = loc.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val lat = loc.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val dist = obj.optString("distance").toIntOrNull()
            out += PoiItem(
                id = obj.optString("id").ifBlank { "amap-$i" },
                name = name,
                address = address.ifBlank { obj.optString("adname") },
                city = city,
                lat = lat,
                lng = lng,
                distanceMeters = dist,
            )
        }
        return out
    }

    /** 解析腾讯地图 `/ws/place/v1/search` 与 `/ws/geocoder/v1/` 返回的 JSON。 */
    internal fun parseTencentPois(json: String): List<PoiItem> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        if (root.optInt("status", -1) != 0) return emptyList()
        val arr = root.optJSONArray("data")
            ?: root.optJSONObject("result")?.optJSONArray("pois")
            ?: return emptyList()
        val out = ArrayList<PoiItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("title").trim()
            if (name.isEmpty()) continue
            val address = obj.optString("address").trim()
            val adInfo = obj.optJSONObject("ad_info")
            val city = adInfo?.optString("city").orEmpty().trim()
            val loc = obj.optJSONObject("location")
            val lat = loc?.optDouble("lat", 0.0) ?: 0.0
            val lng = loc?.optDouble("lng", 0.0) ?: 0.0
            val dist = if (obj.has("_distance")) obj.optDouble("_distance", -1.0).toInt().takeIf { it >= 0 } else null
            out += PoiItem(
                id = obj.optString("id").ifBlank { "qq-$i" },
                name = name,
                address = address,
                city = city,
                lat = lat,
                lng = lng,
                distanceMeters = dist,
            )
        }
        return out
    }

    internal fun parseNominatimArray(json: String, origin: Pair<Double, Double>? = null): List<PoiItem> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val out = ArrayList<PoiItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val display = obj.optString("display_name").trim()
            val name = obj.optString("name").trim().ifBlank { display.substringBefore(",").trim() }
            if (name.isEmpty()) continue
            val addrObj = obj.optJSONObject("address")
            val city = addrObj?.optString("city").orEmpty()
                .ifBlank { addrObj?.optString("town").orEmpty() }
                .ifBlank { addrObj?.optString("state").orEmpty() }
            val lat = obj.optString("lat").toDoubleOrNull() ?: 0.0
            val lng = obj.optString("lon").toDoubleOrNull() ?: 0.0
            val dist = if (origin != null && lat != 0.0 && lng != 0.0) {
                haversineMeters(origin.first, origin.second, lat, lng)
            } else {
                null
            }
            out += PoiItem(
                id = obj.optString("place_id").ifBlank { "nom-$i" },
                name = name,
                address = display,
                city = city,
                lat = lat,
                lng = lng,
                distanceMeters = dist,
            )
        }
        return out
    }

    internal fun parseNominatimSingle(json: String): List<PoiItem> {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val display = obj.optString("display_name").trim()
        val name = obj.optString("name").trim().ifBlank { display.substringBefore(",").trim() }
        if (name.isEmpty()) return emptyList()
        val addrObj = obj.optJSONObject("address")
        val city = addrObj?.optString("city").orEmpty()
            .ifBlank { addrObj?.optString("town").orEmpty() }
            .ifBlank { addrObj?.optString("state").orEmpty() }
        val lat = obj.optString("lat").toDoubleOrNull() ?: 0.0
        val lng = obj.optString("lon").toDoubleOrNull() ?: 0.0
        return listOf(
            PoiItem(
                id = obj.optString("place_id").ifBlank { "nom-0" },
                name = name,
                address = display,
                city = city,
                lat = lat,
                lng = lng,
                distanceMeters = 0,
            ),
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (r * c).toInt()
    }
}
