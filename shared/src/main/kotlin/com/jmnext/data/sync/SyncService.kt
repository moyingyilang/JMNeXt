package com.jmnext.data.sync

import com.jmnext.data.prefs.BlockStoreApi
import com.jmnext.data.prefs.FavoriteStore
import com.jmnext.data.prefs.HistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一次同步的结果，够界面直接展示。 */
data class SyncReport(
    val historyCount: Int,
    val favoriteCount: Int,
    val blockedCount: Int,
    val historyConflicts: Int,
    val favoriteConflicts: Int,
    val remoteExisted: Boolean,
    /** 从远端墓碑学到、并在本机执行的删除数。 */
    val deletedHistory: Int = 0,
    val deletedFavorites: Int = 0,
)

/**
 * 同步编排：本地 → 识别本机删除（与上次载荷做差集）→ 合并远端（含墓碑）→ 写回本地 → 上传。
 *
 * 删除为什么用“与上次载荷做差集”而不是让界面逐个登记：
 * 界面有删除入口的地方不止一处（详情页取消收藏、收藏列表移除、历史上限淘汰…），
 * 逐个登记必然漏。差集能自动覆盖所有路径，代价是要求我们把**上次同步的载荷**保存下来。
 *
 * 顺序仍然是“先合并再上传”：远端任何时刻都是两端并集，不存在半份数据的窗口。
 */
class SyncService(
    private val history: HistoryStore,
    private val favorites: FavoriteStore,
    private val blocks: BlockStoreApi,
    private val settings: SyncSettings,
    private val namespace: () -> String,
    private val device: String,
    private val clientFactory: (SyncConfig) -> WebDavClient = { WebDavClient(it.url, it.user, it.pass) },
) {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun localPayload(): SyncPayload {
        val rules = blocks.snapshot()
        return SyncPayload(
            updatedAt = 0L,
            device = device,
            history = history.all(namespace()),
            favorites = favorites.all(namespace()),
            blocked = BlockedPayload(
                words = rules.words.toList(),
                tags = rules.tags.toList(),
                categories = rules.categories.toList(),
            ),
        )
    }

    /** 把合并后的载荷写回本地：新增/更新，并执行墓碑带来的删除。 */
    fun applyLocally(payload: SyncPayload, baseline: SyncPayload?) {
        val ns = namespace()
        for (h in payload.history) history.record(ns, h)
        for (f in payload.favorites) favorites.add(ns, f)
        for (w in payload.blocked.words) blocks.addWord(w)
        for (t in payload.blocked.tags) blocks.addTag(t)
        for (c in payload.blocked.categories) blocks.addCategory(c)

        // 墓碑生效：本地有、但合并结果里没有、且墓碑时间晚于条目时间 → 删除
        val keepHistory = payload.history.map { it.aid }.toSet()
        val histTomb = payload.deletedHistory.associate { it.id to it.deletedAt }
        for (h in history.all(ns)) {
            if (h.aid in keepHistory) continue
            val at = histTomb[h.aid] ?: continue
            if (h.updatedAt <= at) history.remove(ns, h.aid)
        }
        val keepFav = payload.favorites.map { it.aid }.toSet()
        val favTomb = payload.deletedFavorites.associate { it.id to it.deletedAt }
        for (f in favorites.all(ns)) {
            if (f.aid in keepFav) continue
            val at = favTomb[f.aid] ?: continue
            if (f.addedAt <= at) favorites.removeAid(ns, f.aid)
        }
    }

    suspend fun syncNow(): Result<SyncReport> = withContext(Dispatchers.IO) {
        val cfg = settings.load()
        if (!cfg.isUsable) return@withContext Result.failure(IllegalStateException("未配置 WebDAV 地址"))

        val client = clientFactory(cfg)
        val local = localPayload().copy(device = device)
        val baseline = settings.lastPayload?.let { runCatching { decode(it) }.getOrNull() }

        // 1) 本机删除 = 上次载荷里有、现在没有的条目 → 生成墓碑
        val now = System.currentTimeMillis()
        val localWithTombs = local.copy(
            deletedHistory = local.deletedHistory + deletedSince(baseline?.history?.map { it.aid }.orEmpty(), local.history.map { it.aid }, now),
            deletedFavorites = local.deletedFavorites + deletedSince(baseline?.favorites?.map { it.aid }.orEmpty(), local.favorites.map { it.aid }, now),
        )

        // 2) 拉远端（不存在 → null）
        val remoteText = client.download(cfg.path).getOrElse { return@withContext Result.failure(it) }
        val remote = remoteText?.let { text ->
            runCatching { decode(text) }
                .getOrElse { return@withContext Result.failure(IllegalStateException("远端文件不是合法载荷：${it.message}", it)) }
        }

        // 3) 合并 + 写回本地
        val merged = mergeSyncPayloads(localWithTombs, remote, now = now)
        applyLocally(merged.payload, baseline)

        // 4) 上传合并结果
        val text = json.encodeToString(SyncPayload.serializer(), merged.payload)
        client.upload(cfg.path, text).getOrElse { return@withContext Result.failure(it) }

        // 5) 记住本次载荷，供下次算删除差集
        settings.lastPayload = text

        Result.success(
            SyncReport(
                historyCount = merged.payload.history.size,
                favoriteCount = merged.payload.favorites.size,
                blockedCount = merged.payload.blocked.words.size + merged.payload.blocked.tags.size + merged.payload.blocked.categories.size,
                historyConflicts = merged.historyConflicts,
                favoriteConflicts = merged.favoriteConflicts,
                remoteExisted = remote != null,
                deletedHistory = merged.appliedHistoryDeletions,
                deletedFavorites = merged.appliedFavoriteDeletions,
            ),
        )
    }

    /** 生成墓碑：上次有、这次没有的 id。 */
    private fun deletedSince(previousIds: List<String>, currentIds: List<String>, now: Long): List<Tombstone> {
        if (previousIds.isEmpty()) return emptyList()
        val cur = currentIds.toSet()
        return previousIds.filterNot { it in cur }.distinct().map { Tombstone(it, now) }
    }

    private fun decode(text: String): SyncPayload = json.decodeFromString(SyncPayload.serializer(), text)
}
