package com.jmnext.data.sync

import com.jmnext.data.prefs.FavoriteEntry
import com.jmnext.data.prefs.HistoryEntry
import kotlinx.serialization.Serializable

/**
 * WebDAV 同步载荷。
 *
 * 为什么自成一个 schema 而不是直接传仓库对象的 JSON：
 * 1. 服务端（网盘）里存的是**跨版本长期存在**的文件，字段只能增不能改语义；
 * 2. 需要 `updatedAt` 做「谁更新谁赢」的合并，而单个条目的时间戳不足以表达整表；
 * 3. 需要留出 `revision` 便于以后做多设备冲突提示。
 */
@Serializable
data class SyncPayload(
    val schema: Int = SCHEMA,
    /** 整个载荷的最后修改时间（毫秒）。合并时用于比较。 */
    val updatedAt: Long = 0L,
    /** 设备标识，仅用于排查「哪台机器写坏了」。 */
    val device: String = "",
    val history: List<HistoryEntry> = emptyList(),
    val favorites: List<FavoriteEntry> = emptyList(),
    val blocked: BlockedPayload = BlockedPayload(),
) {
    companion object {
        const val SCHEMA = 1
    }
}

/** 屏蔽名单：三类规则分开存，和 BlockRules 一一对应。 */
@Serializable
data class BlockedPayload(
    val words: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
)

/**
 * 合并结果。`conflicts` 记录同一 aid 在两端都变过、取较新一方的情况数量，
 * 便于界面提示「有 N 条被远端覆盖」。
 */
data class MergeResult(
    val payload: SyncPayload,
    val historyConflicts: Int = 0,
    val favoriteConflicts: Int = 0,
)
