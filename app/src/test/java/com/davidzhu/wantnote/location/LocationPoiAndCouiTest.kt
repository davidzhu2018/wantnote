package com.davidzhu.wantnote.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.18.0：验证高德地图与腾讯地图 POI JSON 解析及位置展示文案格式化。
 */
class LocationPoiAndCouiTest {

    @Test
    fun parseAmapPoisExtractsNameAddressCityCoordsAndDistance() {
        val sample = """
            {
              "status": "1",
              "pois": [
                {
                  "id": "B001",
                  "name": "陆家嘴中心",
                  "address": "浦东南路899号",
                  "cityname": "上海市",
                  "location": "121.5091,31.2317",
                  "distance": "180"
                }
              ]
            }
        """.trimIndent()
        val pois = LocationPoiService.parseAmapPois(sample)
        assertEquals(1, pois.size)
        val first = pois.first()
        assertEquals("陆家嘴中心", first.name)
        assertEquals("上海市 · 陆家嘴中心", first.displayLabel())
        assertEquals("180m", first.formattedDistance())
        assertEquals(31.2317, first.lat, 0.0001)
        assertEquals(121.5091, first.lng, 0.0001)
    }

    @Test
    fun parseTencentPoisExtractsTitleAddressCityCoordsAndDistance() {
        val sample = """
            {
              "status": 0,
              "data": [
                {
                  "id": "T001",
                  "title": "朝阳公园",
                  "address": "朝阳公园南路1号",
                  "ad_info": { "city": "北京市" },
                  "location": { "lat": 39.9412, "lng": 116.4823 },
                  "_distance": 1450.2
                }
              ]
            }
        """.trimIndent()
        val pois = LocationPoiService.parseTencentPois(sample)
        assertEquals(1, pois.size)
        val first = pois.first()
        assertEquals("朝阳公园", first.name)
        assertEquals("北京市 · 朝阳公园", first.displayLabel())
        assertTrue(first.formattedDistance().endsWith("km"))
    }

    @Test
    fun azureThemeExistsAndIsNamedWeiLan() {
        assertEquals(com.davidzhu.wantnote.data.ThemePalette.AZURE, com.davidzhu.wantnote.data.ThemePalette.fromStorage("AZURE"))
        assertEquals("蔚蓝", com.davidzhu.wantnote.data.ThemePalette.AZURE.label)
    }

    @Test
    fun amapRectangleCenterAndErrorHintsAndGcj02Work() {
        val center = LocationPoiService.parseAmapRectangleCenter("116.0119343,39.66127144;116.7829835,40.2164962")
        assertTrue(center != null)
        assertEquals(39.9388, center!!.first, 0.01)
        assertEquals(116.3974, center.second, 0.01)

        val err = LocationPoiService.formatAmapError("USERKEY_PLAT_NOMATCH", "10009")
        assertTrue(err.contains("Web服务"))

        val (gLat, gLng) = LocationPoiService.wgs84ToGcj02(31.2304, 121.4737)
        assertTrue(gLat != 31.2304 && gLng != 121.4737)
    }
}
