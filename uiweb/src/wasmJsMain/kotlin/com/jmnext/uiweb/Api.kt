package com.jmnext.uiweb

import io.ktor.client.HttpClient
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
 * API 客户端。
 *
 * 基址留空 = 同源：页面由静态服务托管，静态服务把 /api、/img 代理到 Ktor。
 * 这样浏览器不涉及跨源，也不需要 CORS；换部署位置时只改代理目标，不改前端。
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

    /** 服务端字段名可能演进，按“能找到 id/name”这一最小契约取，不写死 DTO。 */
    private fun walkCards(e: JsonElement, out: MutableList<Album>) {
        when (e) {
            is JsonObject -> {
                val o = e
                val id = o["id"]?.jsonPrimitive?.contentOrNull
                if (id != null) {
                    val t = o["name"]?.jsonPrimitive?.contentOrNull
                        ?: o["title"]?.jsonPrimitive?.contentOrNull ?: id
                    out.add(Album(id, t)); return
                }
                o.values.forEach { walkCards(it, out) }
            }
            is JsonArray -> e.forEach { walkCards(it, out) }
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
        walkCards(parse(http.get("$BASE/api/home").bodyAsText()), out)
        out.distinctBy { it.id }
    }

    suspend fun search(q: String): Result<List<Album>> = runCatching {
        val out = mutableListOf<Album>()
        walkCards(parse(http.get("$BASE/api/search?q=" + enc(q)).bodyAsText()), out)
        out.distinctBy { it.id }
    }

    suspend fun album(id: String): Result<Detail> = runCatching {
        val root = parse(http.get("$BASE/api/album/$id").bodyAsText())
        val o = root as JsonObject
        val title = o["name"]?.jsonPrimitive?.contentOrNull ?: id
        val fav = o["favorited"]?.jsonPrimitive?.contentOrNull == "true"
        val chapters = mutableListOf<Chapter>()
        (o["series"] as? JsonArray)?.forEach { s ->
            val so = s as JsonObject
            val cid = so["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val cname = so["name"]?.jsonPrimitive?.contentOrNull ?: cid
            chapters.add(Chapter(cid, cname))
        }
        Detail(id, title, chapters, fav)
    }

    suspend fun favorites(): Result<List<Album>> = runCatching {
        val out = mutableListOf<Album>()
        walkCards(parse(http.get("$BASE/api/favorites").bodyAsText()), out)
        out.distinctBy { it.id }
    }

    suspend fun toggleFavorite(id: String): Result<Boolean> = runCatching {
        val r = parse(http.post("$BASE/api/favorite/$id").bodyAsText()) as JsonObject
        r["favorited"]?.jsonPrimitive?.contentOrNull == "true"
    }

    /** 图片同源走代理，浏览器不接触 JM 域名。 */
    fun imageUrl(chapterId: String, idx: Int) = "$BASE/img/$chapterId/$idx"

    private fun enc(s: String): String = s.replace(" ", "%20")
}
