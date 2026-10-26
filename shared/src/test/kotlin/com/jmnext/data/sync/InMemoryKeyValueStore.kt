package com.jmnext.data.sync

import com.jmnext.data.prefs.KeyValueStore

/** 测试用内存键值存储。模拟“一台设备的本地存储”。 */
class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, Any?>()

    override fun getString(key: String, def: String?): String? = map[key] as? String ?: def
    override fun putString(key: String, value: String?) { map[key] = value }
    override fun getBoolean(key: String, def: Boolean): Boolean = map[key] as? Boolean ?: def
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getInt(key: String, def: Int): Int = map[key] as? Int ?: def
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getLong(key: String, def: Long): Long = map[key] as? Long ?: def
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun clear() = map.clear()
}
