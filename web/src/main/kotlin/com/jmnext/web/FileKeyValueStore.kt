package com.jmnext.web

import com.jmnext.data.prefs.KeyValueStore
import java.io.File
import java.util.Properties

/**
 * 文件版键值存储（服务端用）。
 *
 * 为什么不用 `SharedPreferences`：那是 Android 的；桌面端与 Web 服务跑在纯 JVM 上。
 * 用 `Properties` + 整文件读写：条目很少（历史/收藏各最多 1000 条），
 * 每次写入都是"整表重写"，因此进程被杀也不会留半条记录 —— 与 Android 侧的取舍一致。
 */
class FileKeyValueStore(private val file: File) : KeyValueStore {
    private val lock = Any()

    private fun load(): Properties = Properties().also {
        if (file.isFile) runCatching { file.inputStream().use(it::load) }
    }

    private fun save(p: Properties): Unit = synchronized(lock) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { p.store(it, null) }
            tmp.renameTo(file)
        }
        Unit
    }

    override fun getString(key: String, def: String?): String? =
        synchronized(lock) { load().getProperty(key) ?: def }

    override fun putString(key: String, value: String?) = synchronized(lock) {
        val p = load()
        if (value == null) p.remove(key) else p.setProperty(key, value)
        save(p)
    }

    override fun getBoolean(key: String, def: Boolean): Boolean =
        getString(key)?.toBooleanStrictOrNull() ?: def

    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    override fun getInt(key: String, def: Int): Int = getString(key)?.toIntOrNull() ?: def
    override fun putInt(key: String, value: Int) = putString(key, value.toString())
    override fun getLong(key: String, def: Long): Long = getString(key)?.toLongOrNull() ?: def
    override fun putLong(key: String, value: Long) = putString(key, value.toString())

    override fun remove(key: String) {
        synchronized(lock) { val p = load(); p.remove(key); save(p) }
    }

    override fun clear() {
        synchronized(lock) { save(Properties()) }
    }
}
