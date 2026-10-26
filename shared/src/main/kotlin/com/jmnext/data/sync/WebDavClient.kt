package com.jmnext.data.sync

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 极简 WebDAV 客户端：只做同步需要的 PUT / GET / MKCOL / PROPFIND(存在性)。
 *
 * 为什么不用第三方 webdav 库：我们要的只是「往网盘放一个 JSON 文件」，
 * 引入完整客户端会带进 PROPFIND 解析、锁、多协议兼容等一堆用不到的东西，
 * 也增加在 Android 上的依赖面。
 *
 * 认证用 HTTP Basic（WebDAV 的通行做法）。密码建议用应用专用密码，
 * 因为 Basic 认证在网络上可被中间人读取 —— 这条必须写进界面提示。
 */
class WebDavClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val client: OkHttpClient = defaultClient(),
) {
    private val base = baseUrl.trimEnd('/')

    /** 远端文件地址；[path] 形如 `/JMNeXt/sync.json`。 */
    private fun urlOf(path: String): String {
        val p = if (path.startsWith("/")) path else "/$path"
        return base + p
    }

    private fun auth() = if (username.isEmpty()) null else Credentials.basic(username, password)

    private fun newRequest(url: String): Request.Builder {
        val b = Request.Builder().url(url)
        auth()?.let { b.header("Authorization", it) }
        return b
    }

    /** 读取远端文件；不存在（404）返回 null。 */
    fun download(path: String): Result<String?> = runCatching {
        val resp = client.newCall(newRequest(urlOf(path)).get().build()).execute()
        resp.use {
            when (it.code) {
                200 -> it.body?.string()
                404 -> null
                else -> error("下载失败：HTTP ${it.code}")
            }
        }
    }

    /** 写入远端文件（覆盖）。父目录不存在时先尝试逐级 MKCOL。 */
    fun upload(path: String, content: String): Result<Unit> = runCatching {
        ensureParentDirs(path)
        val body = content.toRequestBody(JSON_MEDIA)
        val resp = client.newCall(newRequest(urlOf(path)).put(body).build()).execute()
        resp.use {
            if (it.code !in 200..299) error("上传失败：HTTP ${it.code}")
        }
    }

    /** 逐级创建父目录；已存在（405/301/409 常见）时忽略。 */
    private fun ensureParentDirs(path: String) {
        val segs = path.trim('/').split('/').dropLast(1)
        var acc = ""
        for (s in segs) {
            acc += "/$s"
            val resp = client.newCall(newRequest(urlOf(acc)).method("MKCOL", null).build()).execute()
            resp.use { /* 201 创建成功；405 已存在；409 父目录缺失（顺序保证不会） */ }
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
