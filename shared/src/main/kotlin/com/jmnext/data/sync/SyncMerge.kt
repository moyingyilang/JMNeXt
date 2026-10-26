package com.jmnext.data.sync

import com.jmnext.data.prefs.FavoriteEntry
import com.jmnext.data.prefs.HistoryEntry

/**
 * 纯函数合并：本地 + 远端 → 新载荷。
 *
 * 规则（刻意保守，宁可少丢数据）：
 * - **历史**：按 aid 去重，取 `updatedAt` 较新的一方；同时间戳取本地。
 * - **收藏**：按 aid 去重，取 `addedAt` 较新的一方；远端存在、本地没有的保留（不从远端删除）。
 * - **屏蔽名单**：**并集**。屏蔽是「宁多勿少」的语义，任一端加过的词都保留。
 * - 列表按各自时间戳倒序，限制在 [limit] 条以内。
 *
 * 不做「远端删除穿到本地」：单靠时间戳无法区分「远端删了」和「远端还没同步过」，
 * 贸然同步会误删用户数据。要删除请走显式操作（后续可加墓碑表）。
 */
fun mergeSyncPayloads(
    local: SyncPayload,
    remote: SyncPayload?,
    limit: Int = 1000,
    now: Long = System.currentTimeMillis(),
): MergeResult {
    if (remote == null) {
        return MergeResult(
            local.copy(
                updatedAt = now,
                history = local.history.sortedByDescending { it.updatedAt }.take(limit),
                favorites = local.favorites.sortedByDescending { it.addedAt }.take(limit),
            ),
        )
    }

    var historyConflicts = 0
    val history = LinkedHashMap<String, HistoryEntry>()
    for (e in local.history) history[e.aid] = e
    for (r in remote.history) {
        val cur = history[r.aid]
        if (cur == null) {
            history[r.aid] = r
        } else if (r.updatedAt > cur.updatedAt) {
            history[r.aid] = r
            historyConflicts++
        }
    }

    var favoriteConflicts = 0
    val favorites = LinkedHashMap<String, FavoriteEntry>()
    for (e in local.favorites) favorites[e.aid] = e
    for (r in remote.favorites) {
        val cur = favorites[r.aid]
        if (cur == null) {
            favorites[r.aid] = r
        } else if (r.addedAt > cur.addedAt) {
            favorites[r.aid] = r
            favoriteConflicts++
        }
    }

    val blocked = BlockedPayload(
        words = (local.blocked.words + remote.blocked.words).distinct().sorted(),
        tags = (local.blocked.tags + remote.blocked.tags).distinct().sorted(),
        categories = (local.blocked.categories + remote.blocked.categories).distinct().sorted(),
    )

    val merged = SyncPayload(
        updatedAt = now,
        device = local.device,
        history = history.values.sortedByDescending { it.updatedAt }.take(limit),
        favorites = favorites.values.sortedByDescending { it.addedAt }.take(limit),
        blocked = blocked,
    )
    return MergeResult(merged, historyConflicts, favoriteConflicts)
}
