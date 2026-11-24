package com.davidzhu.wantnote.sync.dinox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.11.0 N11-N13：Dinox 卡片盒本地缓存两小时新鲜度策略单元测试（纯 JVM，不联网）。
 */
class DinoxBoxCachePolicyTest {
    private val twoHoursMs = DinoxBoxCachePolicy.REFRESH_INTERVAL_MS

    @Test
    fun refreshIntervalIsTwoHours() {
        assertEquals(2L * 60L * 60L * 1000L, twoHoursMs)
    }

    @Test
    fun neverFetchedEmptyCacheTriggersBlockingFetch() {
        val now = 1_760_000_000_000L
        assertTrue(DinoxBoxCachePolicy.isStale(lastFetchedAtMs = 0L, nowMs = now))
        assertEquals(
            DinoxBoxCachePolicy.CacheAction.FETCH_BLOCKING,
            DinoxBoxCachePolicy.decide(cachedNames = emptyList(), lastFetchedAtMs = 0L, nowMs = now),
        )
    }

    @Test
    fun freshCacheUnderTwoHoursUsesCacheOnlyWithoutNetwork() {
        val fetchedAt = 1_760_000_000_000L
        val justBeforeExpiry = fetchedAt + twoHoursMs - 1L
        assertFalse(DinoxBoxCachePolicy.isStale(fetchedAt, justBeforeExpiry))
        assertEquals(
            DinoxBoxCachePolicy.CacheAction.USE_CACHE_ONLY,
            DinoxBoxCachePolicy.decide(
                cachedNames = listOf("读书", "工作"),
                lastFetchedAtMs = fetchedAt,
                nowMs = justBeforeExpiry,
            ),
        )
    }

    @Test
    fun freshEmptyCacheThatPreviouslySucceededDoesNotRefetchBeforeTwoHours() {
        val fetchedAt = 1_760_000_000_000L
        val oneHourLater = fetchedAt + 60L * 60L * 1000L
        assertFalse(DinoxBoxCachePolicy.isStale(fetchedAt, oneHourLater))
        assertEquals(
            DinoxBoxCachePolicy.CacheAction.USE_CACHE_ONLY,
            DinoxBoxCachePolicy.decide(
                cachedNames = emptyList(),
                lastFetchedAtMs = fetchedAt,
                nowMs = oneHourLater,
            ),
        )
    }

    @Test
    fun cacheAtExactTwoHoursShowsCacheAndRefreshesInBackground() {
        val fetchedAt = 1_760_000_000_000L
        val exactTwoHoursLater = fetchedAt + twoHoursMs
        assertTrue(DinoxBoxCachePolicy.isStale(fetchedAt, exactTwoHoursLater))
        assertEquals(
            DinoxBoxCachePolicy.CacheAction.SHOW_CACHE_AND_REFRESH,
            DinoxBoxCachePolicy.decide(
                cachedNames = listOf("读书"),
                lastFetchedAtMs = fetchedAt,
                nowMs = exactTwoHoursLater,
            ),
        )
    }

    @Test
    fun cacheOverTwoHoursShowsCacheAndRefreshesInBackground() {
        val fetchedAt = 1_760_000_000_000L
        val threeHoursLater = fetchedAt + 3L * 60L * 60L * 1000L
        assertTrue(DinoxBoxCachePolicy.isStale(fetchedAt, threeHoursLater))
        assertEquals(
            DinoxBoxCachePolicy.CacheAction.SHOW_CACHE_AND_REFRESH,
            DinoxBoxCachePolicy.decide(
                cachedNames = listOf("项目", "灵感"),
                lastFetchedAtMs = fetchedAt,
                nowMs = threeHoursLater,
            ),
        )
    }

    @Test
    fun clockSkewBackwardsTreatsRecordedSuccessAsFresh() {
        val fetchedAt = 1_760_000_000_000L
        val earlier = fetchedAt - 60_000L
        assertFalse(DinoxBoxCachePolicy.isStale(fetchedAt, earlier))
        assertEquals(
            DinoxBoxCachePolicy.CacheAction.USE_CACHE_ONLY,
            DinoxBoxCachePolicy.decide(
                cachedNames = listOf("读书"),
                lastFetchedAtMs = fetchedAt,
                nowMs = earlier,
            ),
        )
    }
}
