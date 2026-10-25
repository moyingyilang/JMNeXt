package com.jmnext.web

import java.io.File

/**
 * 图片磁盘缓存。
 *
 * 为什么必须有：反切片是 CPU 活（一页 1131×1600 要逐条带搬运），
 * 同一页在滚动时会被请求多次；没有缓存等于每次都重算一遍。
 *
 * 淘汰策略：文件数上限 + 按最旧 mtime 删除。不引入数据库 —— 条目天然幂等（同 key 同内容）。
 */
class ImageCache(private val root: File, private val maxFiles: Int = 500) {

    init { root.mkdirs() }

    /** key 会做一次安全化，避免 `../` 之类的路径穿越。 */
    private fun fileFor(key: String): File {
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(root, safe)
    }

    fun get(key: String): ByteArray? {
        val f = fileFor(key)
        if (!f.isFile) return null
        return runCatching { f.readBytes() }.getOrNull()
    }

    fun put(key: String, bytes: ByteArray) {
        val f = fileFor(key)
        runCatching { f.writeBytes(bytes) }
        trim()
    }

    fun size(): Int = root.listFiles()?.count { it.isFile } ?: 0

    private fun trim() {
        val files = root.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }
            .take(files.size - maxFiles)
            .forEach { it.delete() }
    }
}

/**
 * 极简取图。故意不用 OkHttp：`:web` 只需要 GET 一次拿字节，
 * 引一个 HTTP 客户端进来会让桌面端的依赖图更重。
 */
object ImageFetcher {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    fun get(url: String, referer: String?): ByteArray? = runCatching {
        val c = (java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "image/*,*/*;q=0.8")
            if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        try {
            if (c.responseCode !in 200..299) return null
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
