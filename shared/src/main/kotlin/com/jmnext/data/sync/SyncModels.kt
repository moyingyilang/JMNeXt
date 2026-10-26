package com.jmnext.data.sync

import com.jmnext.data.prefs.FavoriteEntry
import com.jmnext.data.prefs.HistoryEntry
import kotlinx.serialization.Serializable

/**
 * WebDAV 同步载荷。
 *
 * 为什么自成一个 schema 而不是直接传仓库对象的 JSON：
 * 1. 网盘里的文件**跨版本长期存在**，字段只能增、不能改语义；
 * 2. 需要 `updatedAt` 表达整表时间，单个条目的时间戳不足以决定整表；
 * 3. 需要墓碑（见 [Tombstone]）让“删除”成为有证据的操作。
 */
@Serializable
data class SyncPayload(
    val schema: Int = SCHEMA,
    /** 整个载荷的最后修改时间（毫秒）。 */
    val updatedAt: Long = 0L,
    /** 设备标识，仅用于排查“哪台机器写坏了”。 */
    val device: String = "",
    val history: List<HistoryEntry> = emptyList(),
    val favorites: List<FavoriteEntry> = emptyList(),
    val blocked: BlockedPayload = BlockedPayload(),
    /** 历史墓碑：记录“某作品在某时刻被本机删除”。 */
    val deletedHistory: List<Tombstone> = emptyList(),
    /** 收藏墓碑。 */
    val deletedFavorites: List<Tombstone> = emptyList(),
) {
    companion object {
        const val SCHEMA = 2
        /** 墓碑保留期：过了就清掉，避免无限增长。90 天足够所有设备同步过一轮。 */
        const val TOMBSTONE_TTL_MS = 90L * 24 * 60 * 60 * 1000
    }
}

/**
 * 墓碑。
 *
 * 没有墓碑时，“远端没有这条记录”无法区分两种情况：
 *   a) 用户在别的设备删除了它；
 *   b) 这台设备还没同步过它。
 * 于是只能选择“永不传播删除”——代价是用户取消收藏后，下次同步又被拉回来。
 *
 * 有墓碑后，删除成为显式事实：`deletedAt` 与条目的 `updatedAt`/`addedAt` 比大小即可判定。
 */
@Serializable
data class Tombstone(
    val id: String,
    val deletedAt: Long,
)

/** 屏蔽名单：三类规则分开存，和 BlockRules 一一对应。 */
@Serializable
data class BlockedPayload(
    val words: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
)

/**
 * 合并结果。`appliedDeletions*` 表示“从远端墓碑里学到并需要在本地执行删除”的条数，
 * 界面可以据此提示“有 N 条在别的设备被删除”。
 */
data class MergeResult(
    val payload: SyncPayload,
    val historyConflicts: Int = 0,
    val favoriteConflicts: Int = 0,
    val appliedHistoryDeletions: Int = 0,
    val appliedFavoriteDeletions: Int = 0,
)
