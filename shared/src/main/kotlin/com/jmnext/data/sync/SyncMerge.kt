package com.jmnext.data.sync

import com.jmnext.data.prefs.FavoriteEntry
import com.jmnext.data.prefs.HistoryEntry

/**
 * 纯函数合并：本地 + 远端 → 新载荷。
 *
 * 规则：
 * - **历史 / 收藏**：按 id 去重，取条目时间较新的一方；
 * - **墓碑**：按 id 取较晚的 `deletedAt`；若条目的时间**晚于**墓碑，说明用户在删除之后
 *   又重新添加过，此时以条目为准（墓碑失效）；
 * - **屏蔽名单**：取并集（屏蔽是“宁多勿少”的语义）；
 * - 列表按时间倒序、限制在 [limit] 条；墓碑按 TTL 清理。
 *
 * 关键正确性：远端“没有这条”**不等于**删除 —— 只有墓碑才算。
 */
fun mergeSyncPayloads(
    local: SyncPayload,
    remote: SyncPayload?,
    limit: Int = 1000,
    now: Long = System.currentTimeMillis(),
): MergeResult {
    val remoteHistory = remote?.history.orEmpty()
    val remoteFavorites = remote?.favorites.orEmpty()
    val remoteBlocked = remote?.blocked ?: BlockedPayload()
    val tombstonesHistory = latestTombstones(
        local.deletedHistory + (remote?.deletedHistory.orEmpty()), now,
    )
    val tombstonesFavorites = latestTombstones(
        local.deletedFavorites + (remote?.deletedFavorites.orEmpty()), now,
    )

    var historyConflicts = 0
    val historyMap = LinkedHashMap<String, HistoryEntry>()
    for (e in local.history) historyMap[e.aid] = e
    for (r in remoteHistory) {
        val cur = historyMap[r.aid]
        if (cur == null) historyMap[r.aid] = r
        else if (r.updatedAt > cur.updatedAt) { historyMap[r.aid] = r; historyConflicts++ }
    }

    var favoriteConflicts = 0
    val favoriteMap = LinkedHashMap<String, FavoriteEntry>()
    for (e in local.favorites) favoriteMap[e.aid] = e
    for (r in remoteFavorites) {
        val cur = favoriteMap[r.aid]
        if (cur == null) favoriteMap[r.aid] = r
        else if (r.addedAt > cur.addedAt) { favoriteMap[r.aid] = r; favoriteConflicts++ }
    }

    // 墓碑生效：条目的时间早于删除时间 → 删除
    val delHistory = tombstonesHistory.filter { (id, at) ->
        val e = historyMap[id]
        e != null && e.updatedAt <= at
    }.keys
    val delFavorites = tombstonesFavorites.filter { (id, at) ->
        val e = favoriteMap[id]
        e != null && e.addedAt <= at
    }.keys

    delHistory.forEach { historyMap.remove(it) }
    delFavorites.forEach { favoriteMap.remove(it) }

    // 条目在删除之后又被添加过 → 墓碑作废，避免“删了又加还一直被删”
    val liveHistoryTombstones = tombstonesHistory.filter { (id, at) ->
        val e = historyMap[id]
        e == null || e.updatedAt < at
    }
    val liveFavoriteTombstones = tombstonesFavorites.filter { (id, at) ->
        val e = favoriteMap[id]
        e == null || e.addedAt < at
    }

    val blocked = BlockedPayload(
        words = (local.blocked.words + remoteBlocked.words).distinct().sorted(),
        tags = (local.blocked.tags + remoteBlocked.tags).distinct().sorted(),
        categories = (local.blocked.categories + remoteBlocked.categories).distinct().sorted(),
    )

    val merged = SyncPayload(
        updatedAt = now,
        device = local.device,
        history = historyMap.values.sortedByDescending { it.updatedAt }.take(limit),
        favorites = favoriteMap.values.sortedByDescending { it.addedAt }.take(limit),
        blocked = blocked,
        deletedHistory = liveHistoryTombstones.map { Tombstone(it.key, it.value) }.sortedByDescending { it.deletedAt },
        deletedFavorites = liveFavoriteTombstones.map { Tombstone(it.key, it.value) }.sortedByDescending { it.deletedAt },
    )
    return MergeResult(
        payload = merged,
        historyConflicts = historyConflicts,
        favoriteConflicts = favoriteConflicts,
        appliedHistoryDeletions = delHistory.size,
        appliedFavoriteDeletions = delFavorites.size,
    )
}

/** 同 id 取最晚的删除时间；顺带按 TTL 丢掉过期墓碑。 */
private fun latestTombstones(list: List<Tombstone>, now: Long): Map<String, Long> {
    val m = HashMap<String, Long>()
    for (t in list) {
        if (t.deletedAt <= 0) continue
        if (now - t.deletedAt > SyncPayload.TOMBSTONE_TTL_MS) continue
        val cur = m[t.id]
        if (cur == null || t.deletedAt > cur) m[t.id] = t.deletedAt
    }
    return m
}
