package com.jmnext.uiweb

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * API 客户端。基址留空 = 同源：静态服务把 /api、/img 代理到 Ktor，
 * 于是浏览器不涉及跨源，也不需要 CORS；换部署位置只改代理目标。
 *
 * 解析一律按“能找到 id / name / 含 /img/ 的字符串”这一最小契约做，
 * 不写死 DTO —— 服务端字段演进时前端不会立刻碎掉。
 */
object Api {
    const val BASE = ""

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = HttpClient { install(ContentNegotiation) { json(json) } }

    data class Album(val id: String, val title: String)
    data class Chapter(val id: String, val title: String)
    data class Detail(val id: String, val title: String, val chapters: List<Chapter>, val favorited: Boolean)

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun parse(text: String): JsonElement = json.parseToJsonElement(text)

    private fun walkAlbums(e: JsonElement, out: MutableList<Album>) {
        when (e) {
            is JsonObject -> {
                val id = e["id"]?.jsonPrimitive?.contentOrNull
                if (id != null) {
                    val t = e["name"]?.jsonPrimitive?.contentOrNull
                        ?: e["title"]?.jsonPrimitive?.contentOrNull ?: id
                    out.add(Album(id, t)); return
                }
                e.values.forEach { walkAlbums(it, out) }
            }
            is JsonArray -> e.forEach { walkAlbums(it, out) }
            else -> Unit
        }
    }

    suspend fun login(user: String, pass: String): Result<Unit> = runCatching {
        val r = http.post("$BASE/api/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"${esc(user)}","password":"${esc(pass)}"}""")
        }
        check(r.status.value in 200..299) { "HTTP ${r.status.value}" }
    }

    suspend fun home(): Result<List<Album>> = runCatching {
        val out = mutableListOf<Album>()
        walkAlbums(parse(http.get("$BASE/api/home").bodyAsText()), out)
        out.distinctBy { it.id }
    }

    suspend fun search(q: String): Result<List<Album>> = runCatching {
        val out = mutableListOf<Album>()
        val enc = q.replace(" ", "%20")
        walkAlbums(parse(http.get("$BASE/api/search?q=$enc").bodyAsText()), out)
        out.distinctBy { it.id }
    }

    suspend fun album(id: String): Result<Detail> = runCatching {
        val o = parse(http.get("$BASE/api/album/$id").bodyAsText()) as JsonObject
        val title = o["name"]?.jsonPrimitive?.contentOrNull ?: id
        val fav = o["favorited"]?.jsonPrimitive?.contentOrNull == "true"
        val chapters = mutableListOf<Chapter>()
        (o["series"] as? JsonArray)?.forEach { s ->
            val so = s as JsonObject
            val cid = so["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            chapters.add(Chapter(cid, so["name"]?.jsonPrimitive?.contentOrNull ?: cid))
        }
        Detail(id, title, chapters, fav)
    }

    suspend fun favorites(): Result<List<Album>> = runCatching {
        val out = mutableListOf<Album>()
        walkAlbums(parse(http.get("$BASE/api/favorites").bodyAsText()), out)
        out.distinctBy { it.id }
    }

    suspend fun toggleFavorite(id: String): Result<Boolean> = runCatching {
        val o = parse(http.post("$BASE/api/favorite/$id").bodyAsText()) as JsonObject
        o["favorited"]?.jsonPrimitive?.contentOrNull == "true"
    }

    /** 阅读：服务端返回已代理好的图片地址，这里只保留 /img/... 这部分。 */
    suspend fun readChapter(chapterId: String): Result<List<String>> = runCatching {
        val out = mutableListOf<String>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> e.values.forEach { walk(it) }
                is JsonArray -> e.forEach { walk(it) }
                else -> {
                    val s = e.jsonPrimitive.contentOrNull ?: return
                    if (s.contains("/img/")) out.add("/img/" + s.substringAfter("/img/"))
                }
            }
        }
        walk(parse(http.get("$BASE/api/read/$chapterId").bodyAsText()))
        out.distinct()
    }

    /** 取图并解码。服务端已完成反切片，浏览器拿到的是正常画面。 */
    suspend fun loadImage(path: String): ImageBitmap =
        http.get(BASE + path).body<ByteArray>().decodeToImageBitmap()

    suspend fun settings(): Result<Pair<String, String>> = runCatching {
        val o = parse(http.get("$BASE/api/settings").bodyAsText()) as JsonObject
        val t = o["theme"]?.jsonPrimitive?.contentOrNull ?: "dark"
        val m = o["readerMode"]?.jsonPrimitive?.contentOrNull ?: "scroll"
        t to m
    }

    suspend fun saveSettings(theme: String, mode: String): Result<Unit> = runCatching {
        val r = http.post("$BASE/api/settings") {
            contentType(ContentType.Application.Json)
            setBody("""{"theme":"$theme","readerMode":"$mode"}""")
        }
        check(r.status.value in 200..299) { "HTTP ${r.status.value}" }
    }
}
