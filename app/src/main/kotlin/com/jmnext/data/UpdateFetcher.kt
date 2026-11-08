package com.jmnext.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 拉取「最新 Release」的信息（tag + 更新说明）。
 *
 * 为什么带代理：`api.github.com` 在部分网络下不可达（DNS 污染 / 超时），
 * 而 GitHub Release 的更新是**低频**操作，走公共镜像完全够用。
 * 依次尝试，第一个成功的胜出；全部失败返回 null（调用方按“没取到”处理，不打扰用户）。
 */
object UpdateFetcher {

    /** 结果：版本号 + 更新说明（release body）。 */
    data class Latest(val tag: String, val notes: String)

    /**
     * GitHub API 的镜像前缀。顺序 = 优先级：
     * 先官方，再几个常用公共代理（它们在不可达时会快速失败，不会拖太久）。
     */
    private val API_CANDIDATES = listOf(
        "https://api.github.com/repos/${UpdateCheck.REPO}/releases/latest",
        "https://gh-proxy.com/https://api.github.com/repos/${UpdateCheck.REPO}/releases/latest",
        "https://ghproxy.net/https://api.github.com/repos/${UpdateCheck.REPO}/releases/latest",
        "https://mirror.ghproxy.com/https://api.github.com/repos/${UpdateCheck.REPO}/releases/latest",
        "https://ghfast.top/https://api.github.com/repos/${UpdateCheck.REPO}/releases/latest",
    )

    suspend fun latest(client: OkHttpClient): Latest? = withContext(Dispatchers.IO) {
        for (url in API_CANDIDATES) {
            val got = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val obj = Json.parseToJsonElement(resp.body.string()).jsonObject
                    val tag = obj["tag_name"]?.jsonPrimitive?.content ?: return@use null
                    val body = obj["body"]?.jsonPrimitive?.content.orEmpty()
                    Latest(tag = tag, notes = body)
                }
            }.getOrNull()
            if (got != null) return@withContext got
        }
        null
    }

    /** 把 release 说明压成一段适合塞进提示条的短文本（去掉 Markdown 标题与多余空行）。 */
    fun brief(notes: String, maxChars: Int = 160): String {
        val lines = notes.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("|") && !it.startsWith("---") }
            .toList()
        val text = lines.joinToString(" ")
            .replace("**", "")
            .replace("`", "")
        return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "…"
    }
}
