package com.jmnext.data.sync

import com.jmnext.data.prefs.FavoriteEntry
import com.jmnext.data.prefs.HistoryEntry

/**
 * 纯函数合并：本地 + 远端 → 新载荷。
 *
 * 规则：
 * - 历史 / 收藏：按 id 去重，取较新的一方；墓碑晚于条目则删除，条目晚于墓碑则保留（复活）。
 * - 屏蔽名单：**并集**，但墓碑里的项要剔除；本机当前仍存在的项视为“用户刚加回来”，墓碑作废。
 * - 列表按时间倒序、上限 [limit]；墓碑按 TTL 清理。
 *
 * 关键：远端“没有这条”不等于删除 —— 只有墓碑才算。
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

    val histTombs = latestTombstones(local.deletedHistory + (remote?.deletedHistory.orEmpty()), now)
    val favTombs = latestTombstones(local.deletedFavorites + (remote?.deletedFavorites.orEmpty()), now)
    val blockTombs = latestTombstones(local.blocked.deleted + remoteBlocked.deleted, now)

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

    val delHistory = histTombs.filter { (id, at) -> historyMap[id]?.let { it.updatedAt <= at } ?: false }.keys
    val delFavorites = favTombs.filter { (id, at) -> favoriteMap[id]?.let { it.addedAt <= at } ?: false }.keys
    delHistory.forEach { historyMap.remove(it) }
    delFavorites.forEach { favoriteMap.remove(it) }

    // 屏蔽：并集 → 剔除墓碑 → 本机仍存在的项清墓碑（视为重新添加）
    val localBlockedAll = local.blocked.all().toSet()
    val unionWords = (local.blocked.words + remoteBlocked.words).distinct()
    val unionTags = (local.blocked.tags + remoteBlocked.tags).distinct()
    val unionCategories = (local.blocked.categories + remoteBlocked.categories).distinct()

    val liveBlockTombs = blockTombs.filterKeys { it !in localBlockedAll }
    val deletedValues = liveBlockTombs.keys

    val blocked = BlockedPayload(
        words = unionWords.filterNot { it in deletedValues }.sorted(),
        tags = unionTags.filterNot { it in deletedValues }.sorted(),
        categories = unionCategories.filterNot { it in deletedValues }.sorted(),
        deleted = liveBlockTombs.map { Tombstone(it.key, it.value) }.sortedByDescending { it.deletedAt },
    )

    val liveHistTombs = histTombs.filter { (id, at) -> historyMap[id]?.let { it.updatedAt < at } ?: true }
    val liveFavTombs = favTombs.filter { (id, at) -> favoriteMap[id]?.let { it.addedAt < at } ?: true }

    val merged = SyncPayload(
        updatedAt = now,
        device = local.device,
        history = historyMap.values.sortedByDescending { it.updatedAt }.take(limit),
        favorites = favoriteMap.values.sortedByDescending { it.addedAt }.take(limit),
        blocked = blocked,
        deletedHistory = liveHistTombs.map { Tombstone(it.key, it.value) }.sortedByDescending { it.deletedAt },
        deletedFavorites = liveFavTombs.map { Tombstone(it.key, it.value) }.sortedByDescending { it.deletedAt },
    )
    return MergeResult(
        payload = merged,
        historyConflicts = historyConflicts,
        favoriteConflicts = favoriteConflicts,
        appliedHistoryDeletions = delHistory.size,
        appliedFavoriteDeletions = delFavorites.size,
        appliedBlockedDeletions = deletedValues.size,
    )
}

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
