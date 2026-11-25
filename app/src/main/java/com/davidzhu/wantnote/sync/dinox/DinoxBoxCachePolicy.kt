package com.davidzhu.wantnote.sync.dinox

/**
 * 0.11.0 / 0.12.0 Dinox 卡片盒本地缓存的新鲜度与失败退避策略。
 *
 * - 最多每两小时通过轻量 `GET /api/openapi/zettelboxes` 刷新一次；
 * - 有新鲜缓存（距上次成功不足两小时）：直接展示本地缓存，不发请求；
 * - 缓存超过两小时且已有上次成功数据：先立即展示缓存，同时后台轻量刷新；
 * - 从未成功且缓存为空、且近期未失败：发起一次轻量拉取（可展示短加载态）；
 * - 最近一次拉取刚失败且处于冷却期内：直接展示缓存或短错误+「重试」，
 *   避免每次重开选择器都自动重入加载循环；用户主动点击「重试」时立即刷新。
 */
object DinoxBoxCachePolicy {
    /** 两小时（毫秒）。 */
    const val REFRESH_INTERVAL_MS: Long = 2L * 60L * 60L * 1000L

    /** 拉取失败后的自动重试冷却期（5 分钟），避免重开选择器反复自动卡加载。 */
    const val FAILURE_COOLDOWN_MS: Long = 5L * 60L * 1000L

    enum class CacheAction {
        /** 缓存新鲜（未满两小时）：只展示本地缓存，不发网络请求。 */
        USE_CACHE_ONLY,
        /** 缓存已超过两小时：先展示已有缓存，并在后台静默走轻量接口刷新。 */
        SHOW_CACHE_AND_REFRESH,
        /** 从未成功且缓存为空、近期未失败：发起一次轻量列盒请求并显示短加载态。 */
        FETCH_LIGHTWEIGHT,
        /** 近期刚失败且处于冷却期：不自动重拉，直接展示已有缓存或错误+重试。 */
        WAIT_FOR_RETRY,
    }

    /** 判断上次成功拉取时间在 [nowMs] 时刻是否已过期（达到或超过两小时）。 */
    fun isStale(lastFetchedAtMs: Long, nowMs: Long): Boolean {
        if (lastFetchedAtMs <= 0L) return true
        val elapsed = nowMs - lastFetchedAtMs
        if (elapsed < 0L) return false
        return elapsed >= REFRESH_INTERVAL_MS
    }

    /** 判断最近一次失败在 [nowMs] 时刻是否仍处于自动重拉冷却期内。 */
    fun isInFailureCooldown(lastFailedAtMs: Long, nowMs: Long): Boolean {
        if (lastFailedAtMs <= 0L) return false
        val elapsed = nowMs - lastFailedAtMs
        if (elapsed < 0L) return false
        return elapsed < FAILURE_COOLDOWN_MS
    }

    /** 根据本地缓存内容、上次成功时间与最近失败时间，决定打开选择器时的动作。 */
    fun decide(
        cachedNames: List<String>,
        lastFetchedAtMs: Long,
        nowMs: Long,
        lastFailedAtMs: Long = 0L,
    ): CacheAction {
        val hasSucceeded = lastFetchedAtMs > 0L
        if (!hasSucceeded && cachedNames.isEmpty()) {
            return if (isInFailureCooldown(lastFailedAtMs, nowMs)) {
                CacheAction.WAIT_FOR_RETRY
            } else {
                CacheAction.FETCH_LIGHTWEIGHT
            }
        }
        if (!isStale(lastFetchedAtMs, nowMs)) {
            return CacheAction.USE_CACHE_ONLY
        }
        if (isInFailureCooldown(lastFailedAtMs, nowMs)) {
            return CacheAction.WAIT_FOR_RETRY
        }
        return CacheAction.SHOW_CACHE_AND_REFRESH
    }
}
