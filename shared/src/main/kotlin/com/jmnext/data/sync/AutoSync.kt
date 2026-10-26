package com.jmnext.data.sync

/**
 * 自动同步的节流器。
 *
 * 为什么不在 shared 里直接依赖 Android 生命周期：shared 同时给桌面端编译，
 * 生命周期属于平台层。这里只提供一个纯判断 + 时间戳读写，由调用方在合适的时机触发。
 */
object AutoSync {
    /** 两次自动同步之间的最小间隔。太频繁会把网盘打爆，也太耗电。 */
    const val MIN_INTERVAL_MS = 10 * 60 * 1000L

    fun shouldRun(enabled: Boolean, usable: Boolean, lastAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        enabled && usable && (now - lastAt >= MIN_INTERVAL_MS || lastAt <= 0)
}
