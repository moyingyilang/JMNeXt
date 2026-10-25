package com.jmnext.web

import com.jmnext.data.prefs.FavoriteEntry
import com.jmnext.data.prefs.FavoriteStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class SeriesOut(val id: String, val name: String? = null)

@Serializable
data class AlbumOut(
    val id: String,
    val name: String? = null,
    val author: List<String> = emptyList(),
    val series: List<SeriesOut> = emptyList(),
    val favorited: Boolean = false,
)

@Serializable
data class FavoriteOut(val id: String, val title: String? = null, val folderId: String = "")

@Serializable
data class SettingsBody(val theme: String? = null, val readerMode: String? = null)

/**
 * 详情 / 收藏 / 设置。
 *
 * 收藏沿用 2.2.1 的分工：**服务端只决定"该不该改"，本地存储负责"记住"**。
 * Toggle 返回的动作明确时（add/move/edit/remove）才落库，
 * 返回不明确时不写 —— 不把"不知道"当成"已收藏"。
 */
fun Route.detailRoutes(
    reg: SessionRegistry,
    favorites: FavoriteStore,
    settings: FileKeyValueStore,
) {
    route("/api") {
        get("/album/{id}") {
            val s = reg.get(cookieSession(call.request.headers["Cookie"]))
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrOut("未登录"))
            val id = call.parameters["id"].orEmpty()
            val a = runCatching { s.repo.album(id) }.getOrNull()
                ?: return@get call.respond(HttpStatusCode.BadGateway, ErrOut("取详情失败"))
            call.respond(
                AlbumOut(
                    id = id,
                    name = a.name,
                    series = a.series.map { SeriesOut(id = it.id, name = it.name) },
                    favorited = favorites.has(s.id, id),
                ),
            )
        }

        get("/favorites") {
            val s = reg.get(cookieSession(call.request.headers["Cookie"]))
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrOut("未登录"))
            call.respond(favorites.all(s.id).map { FavoriteOut(it.aid, it.title, it.folderId) })
        }

        post("/favorite/{id}") {
            val s = reg.get(cookieSession(call.request.headers["Cookie"]))
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrOut("未登录"))
            val id = call.parameters["id"].orEmpty()
            val r = runCatching { s.repo.toggleFavorite(id) }
            if (r.isFailure) return@post call.respond(HttpStatusCode.BadGateway, ErrOut("收藏失败"))
            val action = r.getOrNull()?.type
            when (action) {
                "add", "move", "edit" -> favorites.add(s.id, FavoriteEntry(aid = id))
                "remove" -> favorites.removeAid(s.id, id)
                else -> Unit
            }
            call.respond(mapOf("favorited" to favorites.has(s.id, id), "action" to action))
        }

        get("/settings") {
            call.respond(
                mapOf(
                    "theme" to (settings.getString("theme") ?: "system"),
                    "readerMode" to (settings.getString("readerMode") ?: "scroll"),
                ),
            )
        }

        post("/settings") {
            val body = call.receive<SettingsBody>()
            body.theme?.let { settings.putString("theme", it) }
            body.readerMode?.let { settings.putString("readerMode", it) }
            call.respond(mapOf("ok" to true))
        }
    }
}
