package com.jmnext.data.sync

import com.jmnext.data.prefs.BlockStoreApi
import com.jmnext.data.prefs.FavoriteEntry
import com.jmnext.data.prefs.FavoriteStore
import com.jmnext.data.prefs.HistoryEntry
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
)

/**
 * 同步编排：本地 → 合并（远端）→ 写回本地 → 上传。
 *
 * 顺序刻意是「先合并再上传」：如果先传后合，远端会短暂存着一份缺少另一端数据的载荷，
 * 期间另一台设备同步就会把缺失当成事实。合并后再上传，远端任何时刻都是两端并集。
 *
 * 命名空间与 2.2.1 的本地存储一致（按账号区分），避免换号后把别人的历史同步走。
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

    /** 把合并后的载荷写回本地三处存储。 */
    fun applyLocally(payload: SyncPayload) {
        val ns = namespace()
        for (h in payload.history) history.record(ns, h)
        for (f in payload.favorites) favorites.add(ns, f)
        for (w in payload.blocked.words) blocks.addWord(w)
        for (t in payload.blocked.tags) blocks.addTag(t)
        for (c in payload.blocked.categories) blocks.addCategory(c)
    }

    suspend fun syncNow(): Result<SyncReport> = withContext(Dispatchers.IO) {
        val cfg = settings.load()
        if (!cfg.isUsable) return@withContext Result.failure(IllegalStateException("未配置 WebDAV 地址"))

        val client = clientFactory(cfg)
        val local = localPayload()

        val remoteText = client.download(cfg.path).getOrElse { return@withContext Result.failure(it) }
        val remote = remoteText?.let { text ->
            runCatching { kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(SyncPayload.serializer(), text) }
                .getOrElse { return@withContext Result.failure(IllegalStateException("远端文件不是合法载荷：${it.message}", it)) }
        }

        val merged = mergeSyncPayloads(local, remote)
        applyLocally(merged.payload)

        val text = kotlinx.serialization.json.Json.encodeToString(SyncPayload.serializer(), merged.payload)
        client.upload(cfg.path, text).getOrElse { return@withContext Result.failure(it) }

        Result.success(
            SyncReport(
                historyCount = merged.payload.history.size,
                favoriteCount = merged.payload.favorites.size,
                blockedCount = merged.payload.blocked.words.size + merged.payload.blocked.tags.size + merged.payload.blocked.categories.size,
                historyConflicts = merged.historyConflicts,
                favoriteConflicts = merged.favoriteConflicts,
                remoteExisted = remote != null,
            ),
        )
    }
}
