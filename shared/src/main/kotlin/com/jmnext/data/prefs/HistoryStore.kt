package com.jmnext.data.prefs

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 一条阅读历史。字段都带默认值，方便以后加字段时不破坏旧数据。 */
@Serializable
data class HistoryEntry(
    val aid: String,
    /** 最后读到的章节 id；空表示只知道读过这本、不知道读到哪。 */
    val chapterId: String = "",
    /** 该话内的页码（0 起）。 */
    val page: Int = 0,
    /** 标题与封面的**快照** —— 断网时也要能显示列表，不能依赖再请求一次。 */
    val title: String = "",
    val cover: String? = null,
    val updatedAt: Long = 0L,
)

/**
 * 阅读历史（**本地为主**）。
 *
 * 设计取舍：
 * - **不以服务端为准**：服务端的观看历史只在个别接口返回、且粒度到作品，断网就没有。
 *   用户要的是"我读过什么"永远可见，所以本地是唯一数据源，服务端只用于首次导入。
 * - **不承诺追更**：本地只记"读过/收藏了什么"，服务端没有稳定的增量更新能力，
 *   所以不做"作品更新提醒" —— 这一点用户已明确接受。
 * - **按账号隔离**：`namespace` 一般是 uid，未登录用 `local`。换号不会串数据。
 * - **上限 1000 条**，按 `updatedAt` 淘汰。宁可丢最早的，也不要让存储无限长。
 * - 写入是"整表重写"（条目量级是几百，JSON 序列化在毫秒级），
 *   这样进程被杀时要么旧表完整、要么新表完整，不会出现半条记录。
 */
class HistoryStore(
    private val kv: KeyValueStore,
    private val namespace: String = "local",
    private val limit: Int = 1000,
) {
    private val serializer = ListSerializer(HistoryEntry.serializer())

    private fun key() = "history_" + namespace

    fun all(): List<HistoryEntry> = runCatching {
        val raw = kv.getString(key()) ?: return emptyList()
        Json.decodeFromString(serializer, raw)
    }.getOrDefault(emptyList())

    /** 记一次阅读。同一作品只保留最新一条（读第 3 话会顶掉第 2 话）。 */
    fun record(entry: HistoryEntry) {
        if (entry.aid.isBlank()) return
        val now = entry.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val merged = (listOf(entry.copy(updatedAt = now)) + all().filterNot { it.aid == entry.aid })
            .sortedByDescending { it.updatedAt }
            .take(limit)
        save(merged)
    }

    fun remove(aid: String) = save(all().filterNot { it.aid == aid })

    fun clear() = kv.remove(key())

    private fun save(list: List<HistoryEntry>) = kv.putString(key(), Json.encodeToString(serializer, list))
}

/** 一条收藏。 */
@Serializable
data class FavoriteEntry(
    val aid: String,
    /** 服务端收藏夹 id；空表示"全部/默认夹"。 */
    val folderId: String = "",
    val title: String = "",
    val cover: String? = null,
    val addedAt: Long = 0L,
)

/**
 * 收藏（**本地为主**）。
 *
 * 与 [HistoryStore] 同一套取舍：本地是唯一数据源、按账号隔离、上限 1000 条、整表重写。
 * 额外一条：`toggle` 是幂等的 —— 同 aid 已存在时删掉，不存在时加上，
 * 这样即便界面重复触发也不会出现两份。
 */
class FavoriteStore(
    private val kv: KeyValueStore,
    private val namespace: String = "local",
    private val limit: Int = 1000,
) {
    private val serializer = ListSerializer(FavoriteEntry.serializer())

    private fun key() = "favorites_" + namespace

    fun all(): List<FavoriteEntry> = runCatching {
        val raw = kv.getString(key()) ?: return emptyList()
        Json.decodeFromString(serializer, raw)
    }.getOrDefault(emptyList())

    fun has(aid: String): Boolean = all().any { it.aid == aid }

    /** 幂等切换，返回切换后是否处于"已收藏"。 */
    fun toggle(entry: FavoriteEntry): Boolean {
        if (entry.aid.isBlank()) return false
        val current = all()
        val exists = current.any { it.aid == entry.aid }
        if (exists) {
            save(current.filterNot { it.aid == entry.aid })
            return false
        }
        val now = entry.addedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        save((listOf(entry.copy(addedAt = now)) + current).sortedByDescending { it.addedAt }.take(limit))
        return true
    }

    fun clear() = kv.remove(key())

    private fun save(list: List<FavoriteEntry>) = kv.putString(key(), Json.encodeToString(serializer, list))
}
