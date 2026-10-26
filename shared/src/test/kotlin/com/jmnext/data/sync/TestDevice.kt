package com.jmnext.data.sync

import com.jmnext.data.prefs.BlockStore
import com.jmnext.data.prefs.FavoriteStore
import com.jmnext.data.prefs.HistoryStore

/**
 * 一台“设备”：三份本地存储 + 同步配置 + SyncService。
 * 用独立的内存 KV 模拟不同设备的本地数据，使集成测试能验证多设备合并。
 */
class TestDevice(
    val name: String,
    baseUrl: String,
    user: String = "u",
    pass: String = "p",
    private val namespace: String = "ns",
) {
    private val prefs = InMemoryKeyValueStore()
    val history = HistoryStore(prefs)
    val favorites = FavoriteStore(prefs)
    val blocks = BlockStore(prefs)
    val settings = SyncSettings(prefs)

    init {
        settings.save(
            SyncConfig(url = baseUrl, user = user, pass = pass, path = "/JMNeXt/sync.json", enabled = true),
        )
    }

    fun service(): SyncService = SyncService(
        history = history,
        favorites = favorites,
        blocks = blocks,
        settings = settings,
        namespace = { namespace },
        device = name,
    )
}
