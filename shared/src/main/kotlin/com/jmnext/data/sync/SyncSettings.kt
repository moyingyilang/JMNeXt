package com.jmnext.data.sync

import com.jmnext.data.prefs.KeyValueStore

/**
 * WebDAV 同步配置。
 *
 * 密码保存在 [KeyValueStore] 里（明文）。这是**已知取舍**：
 * 真正的加密需要 Android Keystore，而 shared 模块要同时服务桌面端，
 * 引入平台相关加密会把这里切成两套。界面上必须提示「用应用专用密码」。
 */
data class SyncConfig(
    val url: String = "",
    val user: String = "",
    val pass: String = "",
    val path: String = "/JMNeXt/sync.json",
    val enabled: Boolean = false,
) {
    val isUsable: Boolean get() = url.isNotBlank() && path.isNotBlank()
}

class SyncSettings(private val kv: KeyValueStore) {
    fun load(): SyncConfig = SyncConfig(
        url = kv.getString(K_URL, null) ?: "",
        user = kv.getString(K_USER, null) ?: "",
        pass = kv.getString(K_PASS, null) ?: "",
        path = kv.getString(K_PATH, null) ?: "/JMNeXt/sync.json",
        enabled = kv.getString(K_ENABLED, null) == "1",
    )

    /** 上次成功同步的时间戳（毫秒），供自动同步节流。 */
    var lastSyncAt: Long
        get() = kv.getString(K_LAST, null)?.toLongOrNull() ?: 0L
        set(value) { kv.putString(K_LAST, value.toString()) }

    /** 上次成功同步时上传的载荷原文（JSON）。用于下次算“本机删除了什么”。 */
    var lastPayload: String?
        get() = kv.getString(K_PAYLOAD, null)
        set(value) { kv.putString(K_PAYLOAD, value) }

    fun save(cfg: SyncConfig) {
        kv.putString(K_URL, cfg.url)
        kv.putString(K_USER, cfg.user)
        kv.putString(K_PASS, cfg.pass)
        kv.putString(K_PATH, cfg.path)
        kv.putString(K_ENABLED, if (cfg.enabled) "1" else "0")
    }

    private companion object {
        const val K_URL = "sync_webdav_url"
        const val K_USER = "sync_webdav_user"
        const val K_PASS = "sync_webdav_pass"
        const val K_PATH = "sync_webdav_path"
        const val K_ENABLED = "sync_webdav_enabled"
        const val K_LAST = "sync_webdav_last_at"
        const val K_PAYLOAD = "sync_webdav_last_payload"
    }
}
