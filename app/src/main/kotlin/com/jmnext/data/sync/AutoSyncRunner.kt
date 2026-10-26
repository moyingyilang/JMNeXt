package com.jmnext.data.sync

import com.jmnext.JmApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 平台层的自动同步入口（Android）。
 *
 * 职责刻意很小：判断是否该跑（节流 + 开关）→ 跑 [SyncService] → 记录时间戳。
 * 失败静默：自动同步不该弹错误打断阅读，用户可在设置页手动同步看详细结果。
 */
object AutoSyncRunner {
    suspend fun maybeRun(app: JmApp) = withContext(Dispatchers.IO) {
        val settings = app.syncSettings
        val cfg = settings.load()
        if (!AutoSync.shouldRun(cfg.enabled, cfg.isUsable, settings.lastSyncAt)) return@withContext
        runCatching {
            app.syncService().syncNow().getOrThrow()
            settings.lastSyncAt = System.currentTimeMillis()
        }
    }
}
