package com.davidzhu.wantnote.sync.dinox

/**
 * 0.11.0 Dinox 卡片盒本地缓存的新鲜度策略（覆盖 0.10.0 第 8 条每次实时刷新）。
 *
 * - 最多每两小时刷新一次；
 * - 有新鲜缓存（距上次成功不足两小时）：直接展示本地缓存，不发请求；
 * - 缓存超过两小时且已有上次成功数据：先立即展示缓存，同时后台刷新；
 * - 从未成功且缓存为空：立即拉取一次并展示短加载态。
 */
object DinoxBoxCachePolicy {
    /** 两小时（毫秒）。 */
    const val REFRESH_INTERVAL_MS: Long = 2L * 60L * 60L * 1000L

    enum class CacheAction {
        /** 缓存新鲜（未满两小时）：只展示本地缓存，不发网络请求。 */
        USE_CACHE_ONLY,
        /** 缓存已超过两小时：先展示已有缓存，并在后台静默刷新。 */
        SHOW_CACHE_AND_REFRESH,
        /** 从未成功拉取（缓存为空）：立即拉取一次并显示短加载态。 */
        FETCH_BLOCKING,
    }

    /** 判断上次成功拉取时间在 [nowMs] 时刻是否已过期（达到或超过两小时）。 */
    fun isStale(lastFetchedAtMs: Long, nowMs: Long): Boolean {
        if (lastFetchedAtMs <= 0L) return true
        val elapsed = nowMs - lastFetchedAtMs
        if (elapsed < 0L) return false
        return elapsed >= REFRESH_INTERVAL_MS
    }

    /** 根据本地缓存内容与上次成功时间，决定打开选择器时的动作。 */
    fun decide(cachedNames: List<String>, lastFetchedAtMs: Long, nowMs: Long): CacheAction {
        val hasSucceeded = lastFetchedAtMs > 0L
        if (!hasSucceeded && cachedNames.isEmpty()) {
            return CacheAction.FETCH_BLOCKING
        }
        return if (isStale(lastFetchedAtMs, nowMs)) {
            CacheAction.SHOW_CACHE_AND_REFRESH
        } else {
            CacheAction.USE_CACHE_ONLY
        }
    }
}
