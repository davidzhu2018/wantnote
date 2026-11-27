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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
    val currentAddress: String = "",
    val hint: String? = null,
)

/**
 * 0.18.1：位置与 POI 检索服务（高德地图 / 腾讯地图 + GPS/IP 定位 + 零 Key 兜底）。
 *
 * 关键增强：
 * - 自动将系统 WGS-84 坐标转换为国内火星坐标 GCJ-02，确保高德/腾讯周边 POI 精准定位；
 * - 室内 GPS 无信号或国产 ROM 缺少 GMS 网络定位时，自动调用高德 `/v3/ip` 或腾讯 `/ws/location/v1/ip`
 *   获取当前城市与中心坐标，不再出现「无法自动定位」；
 * - 高德/腾讯返回 Key 错误码（如 `USERKEY_PLAT_NOMATCH`）时，给出明确中文原因提示并自动回退开放地理编码。
 */
object LocationPoiService {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentCoordinates(context: Context): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val cached = providers.mapNotNull { p ->
            runCatching { manager.getLastKnownLocation(p) }.getOrNull()
        }.maxByOrNull { it.time }
        if (cached != null && (System.currentTimeMillis() - cached.time) < 15 * 60_000L) {
            return@withContext cached.latitude to cached.longitude
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (p in providers) {
                if (p == LocationManager.PASSIVE_PROVIDER) continue
                val enabled = runCatching { manager.isProviderEnabled(p) }.getOrDefault(false)
                if (!enabled) continue
                val fresh: Location? = withTimeoutOrNull(3_500L) {
                    suspendCancellableCoroutine { cont ->
                        runCatching {
                            manager.getCurrentLocation(p, null, context.mainExecutor) { loc ->
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
        var apiErrorHint: String? = null

        if (key.isNotEmpty()) {
            val gcjCoords = coords?.let { wgs84ToGcj02(it.first, it.second) }
            val result = when (provider) {
                MapProvider.AMAP -> queryAmap(key, q, gcjCoords)
                MapProvider.TENCENT -> queryTencent(key, q, gcjCoords)
            }
            if (result.errorHint == null) {
                return@withContext PoiSearchResult(
                    items = result.items,
                    sourceLabel = provider.label,
                    currentAddress = result.currentAddress,
                    hint = if (result.items.isEmpty()) "未找到相关地点，请换个关键词试试。" else null,
                )
            } else {
                apiErrorHint = result.errorHint
            }
        }

        // 未配置 Key 或高德/腾讯返回错误时：回退系统 Geocoder + Nominatim 开放地理编码
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
        val currentAddr = fallback.firstOrNull()?.displayLabel().orEmpty()
        val hint = apiErrorHint ?: if (key.isEmpty()) {
            "当前使用系统定位兜底；点右上角「地图配置」填入${provider.label}「Web服务」Key 可获得更精准中文 POI。"
        } else {
            "${provider.label}接口暂时不可达，已切换为系统定位兜底。"
        }
        PoiSearchResult(
            items = fallback,
            sourceLabel = if (key.isEmpty()) "系统定位" else "${provider.label}（兜底）",
            currentAddress = currentAddr,
            hint = hint,
        )
    }

    private data class ProviderQueryOutcome(
        val items: List<PoiItem>,
        val currentAddress: String = "",
        val errorHint: String? = null,
    )

    private fun queryAmap(key: String, keyword: String, gcjCoords: Pair<Double, Double>?): ProviderQueryOutcome {
        val encKey = URLEncoder.encode(key, "UTF-8")
        var activeCoords = gcjCoords
        var cityName = ""
        var currentAddr = ""

        // 1. 若手机未获取到 GPS/网络坐标，用高德 IP 定位自动获取当前城市与中心坐标
        if (activeCoords == null) {
            val ipBody = httpGet("https://restapi.amap.com/v3/ip?key=$encKey")
            if (ipBody != null) {
                val ipRoot = runCatching { JSONObject(ipBody) }.getOrNull()
                if (ipRoot?.optString("status") == "1") {
                    cityName = ipRoot.optString("city").takeIf { it.isNotBlank() && it != "[]" }
                        ?: ipRoot.optString("province").takeIf { it.isNotBlank() && it != "[]" }.orEmpty()
                    currentAddr = cityName
                    val rect = ipRoot.optString("rectangle")
                    activeCoords = parseAmapRectangleCenter(rect)
                } else {
                    val info = ipRoot?.optString("info").orEmpty()
                    val code = ipRoot?.optString("infocode").orEmpty()
                    if (info.isNotBlank()) {
                        return ProviderQueryOutcome(emptyList(), errorHint = formatAmapError(info, code))
                    }
                }
            }
        }

        // 2. 关键词为空时：先做高德逆地理编码拿「当前精确地址 + 周边 POI」，再补周边搜索
        if (keyword.isEmpty() && activeCoords != null) {
            val regeoUrl = "https://restapi.amap.com/v3/geocode/regeo?key=$encKey" +
                "&location=${activeCoords.second},${activeCoords.first}&radius=2000&extensions=all"
            val regeoBody = httpGet(regeoUrl)
            if (regeoBody != null) {
                val regeo = parseAmapRegeo(regeoBody, activeCoords.first, activeCoords.second)
                if (regeo.errorHint != null) return regeo
                if (regeo.items.isNotEmpty()) return regeo
                if (regeo.currentAddress.isNotBlank()) currentAddr = regeo.currentAddress
            }
            val aroundUrl = "https://restapi.amap.com/v3/place/around?key=$encKey" +
                "&location=${activeCoords.second},${activeCoords.first}&radius=3000&offset=20&page=1&extensions=base"
            val aroundBody = httpGet(aroundUrl)
                ?: return ProviderQueryOutcome(emptyList(), currentAddr, errorHint = "高德地图网络请求失败，请检查网络连接")
            return parseAmapPoiResponse(aroundBody, currentAddr)
        }

        // 3. 关键词搜索（优先带当前城市/坐标提升国内精准度）
        val encQ = URLEncoder.encode(keyword.ifBlank { cityName.ifBlank { "广场" } }, "UTF-8")
        val cityParam = if (cityName.isNotBlank()) "&city=${URLEncoder.encode(cityName, "UTF-8")}" else ""
        val locParam = if (activeCoords != null) "&location=${activeCoords.second},${activeCoords.first}" else ""
        val url = "https://restapi.amap.com/v3/place/text?key=$encKey&keywords=$encQ&offset=20&page=1$cityParam$locParam"
        val body = httpGet(url)
            ?: return ProviderQueryOutcome(emptyList(), currentAddr, errorHint = "高德地图网络请求失败，请检查网络连接")
        return parseAmapPoiResponse(body, currentAddr)
    }

    private fun queryTencent(key: String, keyword: String, gcjCoords: Pair<Double, Double>?): ProviderQueryOutcome {
        val encKey = URLEncoder.encode(key, "UTF-8")
        var activeCoords = gcjCoords
        var cityName = ""
        var currentAddr = ""

        if (activeCoords == null) {
            val ipBody = httpGet("https://apis.map.qq.com/ws/location/v1/ip?key=$encKey")
            if (ipBody != null) {
                val root = runCatching { JSONObject(ipBody) }.getOrNull()
                if (root?.optInt("status", -1) == 0) {
                    val res = root.optJSONObject("result")
                    val loc = res?.optJSONObject("location")
                    val lat = loc?.optDouble("lat", 0.0) ?: 0.0
                    val lng = loc?.optDouble("lng", 0.0) ?: 0.0
                    if (lat != 0.0 || lng != 0.0) activeCoords = lat to lng
                    cityName = res?.optJSONObject("ad_info")?.optString("city").orEmpty()
                    currentAddr = cityName
                } else {
                    val msg = root?.optString("message").orEmpty()
                    if (msg.isNotBlank()) {
                        return ProviderQueryOutcome(emptyList(), errorHint = "腾讯地图返回：$msg（请确认已勾选 WebServiceAPI）")
                    }
                }
            }
        }

        val url = if (keyword.isEmpty() && activeCoords != null) {
            "https://apis.map.qq.com/ws/geocoder/v1/?location=${activeCoords.first},${activeCoords.second}&get_poi=1&poi_options=radius=3000;page_size=20&key=$encKey"
        } else {
            val encQ = URLEncoder.encode(keyword.ifBlank { "广场" }, "UTF-8")
            val boundary = when {
                activeCoords != null -> "nearby(${activeCoords.first},${activeCoords.second},5000,1)"
                cityName.isNotBlank() -> "region($cityName,1)"
                else -> "region(全国,1)"
            }
            "https://apis.map.qq.com/ws/place/v1/search?keyword=$encQ&boundary=${URLEncoder.encode(boundary, "UTF-8")}&page_size=20&key=$encKey"
        }
        val body = httpGet(url)
            ?: return ProviderQueryOutcome(emptyList(), currentAddr, errorHint = "腾讯地图网络请求失败，请检查网络连接")
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return ProviderQueryOutcome(emptyList(), currentAddr, errorHint = "腾讯地图返回格式异常")
        if (root.optInt("status", -1) != 0) {
            val msg = root.optString("message").ifBlank { "status=${root.optInt("status")}" }
            return ProviderQueryOutcome(emptyList(), currentAddr, errorHint = "腾讯地图返回：$msg（请确认 Key 开启了 WebServiceAPI）")
        }
        val formattedAddr = root.optJSONObject("result")?.optString("address").orEmpty().ifBlank { currentAddr }
        return ProviderQueryOutcome(items = parseTencentPois(body), currentAddress = formattedAddr)
    }

    private fun parseAmapRegeo(json: String, lat: Double, lng: Double): ProviderQueryOutcome {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return ProviderQueryOutcome(emptyList(), errorHint = "高德地图返回格式异常")
        if (root.optString("status") != "1") {
            return ProviderQueryOutcome(
                emptyList(),
                errorHint = formatAmapError(root.optString("info"), root.optString("infocode")),
            )
        }
        val regeo = root.optJSONObject("regeocode") ?: return ProviderQueryOutcome(emptyList())
        val formatted = regeo.optString("formatted_address").trim()
        val comp = regeo.optJSONObject("addressComponent")
        val cityRaw = comp?.opt("city")
        val city = (if (cityRaw is String) cityRaw else "").trim().ifBlank { comp?.optString("province").orEmpty().trim() }
        val out = ArrayList<PoiItem>()
        if (formatted.isNotEmpty()) {
            out += PoiItem(
                id = "amap-current",
                name = formatted.removePrefix(city).ifBlank { formatted },
                address = formatted,
                city = city,
                lat = lat,
                lng = lng,
                distanceMeters = 0,
            )
        }
        val pois = regeo.optJSONArray("pois")
        if (pois != null) {
            for (i in 0 until pois.length()) {
                val obj = pois.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isEmpty()) continue
                val loc = obj.optString("location").split(",")
                val pLng = loc.getOrNull(0)?.toDoubleOrNull() ?: lng
                val pLat = loc.getOrNull(1)?.toDoubleOrNull() ?: lat
                val dist = obj.optString("distance").toDoubleOrNull()?.toInt()
                val addrRaw = obj.opt("address")
                val addr = if (addrRaw is String) addrRaw.trim() else ""
                out += PoiItem(
                    id = obj.optString("id").ifBlank { "amap-regeo-$i" },
                    name = name,
                    address = addr.ifBlank { formatted },
                    city = city,
                    lat = pLat,
                    lng = pLng,
                    distanceMeters = dist,
                )
            }
        }
        return ProviderQueryOutcome(items = out, currentAddress = formatted)
    }

    private fun parseAmapPoiResponse(json: String, currentAddr: String): ProviderQueryOutcome {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return ProviderQueryOutcome(emptyList(), currentAddr, errorHint = "高德地图返回格式异常")
        if (root.optString("status") != "1") {
            return ProviderQueryOutcome(
                emptyList(),
                currentAddr,
                errorHint = formatAmapError(root.optString("info"), root.optString("infocode")),
            )
        }
        return ProviderQueryOutcome(items = parseAmapPois(json), currentAddress = currentAddr)
    }

    internal fun formatAmapError(info: String, infocode: String): String {
        val detail = when (infocode) {
            "10001" -> "Key 无效或已过期"
            "10009" -> "Key 平台类型不匹配，请在高德控制台选择「Web服务」类型重新申请 Key"
            "10003" -> "今日调用次数已超限"
            "10010" -> "IP 白名单受限，请在控制台清空或放行 IP 白名单"
            else -> info.ifBlank { "错误码 $infocode" }
        }
        return "高德地图提示：$detail"
    }

    internal fun parseAmapRectangleCenter(rectangle: String): Pair<Double, Double>? {
        val parts = rectangle.split(";")
        if (parts.size != 2) return null
        val p1 = parts[0].split(",")
        val p2 = parts[1].split(",")
        val lng1 = p1.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lat1 = p1.getOrNull(1)?.toDoubleOrNull() ?: return null
        val lng2 = p2.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lat2 = p2.getOrNull(1)?.toDoubleOrNull() ?: return null
        return ((lat1 + lat2) / 2.0) to ((lng1 + lng2) / 2.0)
    }

    /** WGS-84 → GCJ-02（中国大陆火星坐标系，确保高德/腾讯定位不偏移）。 */
    internal fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        if (lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271) {
            return lat to lng
        }
        val a = 6378245.0
        val ee = 0.00669342162296594323
        val x = lng - 105.0
        val y = lat - 35.0
        var dLat = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        dLat += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        dLat += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        dLat += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        var dLng = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        dLng += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        dLng += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        dLng += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - ee * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (a / sqrtMagic * cos(radLat) * PI)
        return (lat + dLat) to (lng + dLng)
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
                setRequestProperty("User-Agent", "WantNote-Android/0.18.1")
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
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toInt()
    }
}
