package com.jmnext.web

import com.jmnext.data.prefs.HistoryEntry
import com.jmnext.data.prefs.HistoryStore
import com.jmnext.data.prefs.ReadProgressStore
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
data class ReadOut(val chapter: String, val total: Int, val images: List<String>)

@Serializable
data class ProgressBody(val page: Int = 0)

/** 服务端侧的本地存储（历史 + 进度），按会话命名空间隔离。 */
class ReaderStores(kv: FileKeyValueStore) {
    val history = HistoryStore(kv)
    val progress = ReadProgressStore(kv)
}

fun Route.readerRoutes(reg: SessionRegistry, stores: ReaderStores) {
    route("/api") {
        /** 章节图片列表。**返回代理地址**，不是 JM 原始地址。 */
        get("/read/{chapter}") {
            val s = reg.get(cookieSession(call.request.headers["Cookie"]))
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrOut("未登录"))
            val chapter = call.parameters["chapter"].orEmpty()
            val p = runCatching { s.repo.read(chapter) }.getOrNull()
                ?: return@get call.respond(HttpStatusCode.BadGateway, ErrOut("取章节失败"))
            call.respond(
                ReadOut(
                    chapter = chapter,
                    total = p.images.size,
                    images = p.images.indices.map { "/img/$chapter/$it" },
                ),
            )
        }

        /** 记进度。同时写历史（2.2.1 的本地优先在 Web 端同样成立）。 */
        post("/progress/{chapter}") {
            val s = reg.get(cookieSession(call.request.headers["Cookie"]))
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrOut("未登录"))
            val chapter = call.parameters["chapter"].orEmpty()
            val body = call.receive<ProgressBody>()
            val ns = s.id
            stores.progress.record(chapter, chapter)
            stores.history.record(
                ns,
                HistoryEntry(aid = chapter, chapterId = chapter, page = body.page),
            )
            call.respond(mapOf("ok" to true))
        }

        get("/history") {
            val s = reg.get(cookieSession(call.request.headers["Cookie"]))
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrOut("未登录"))
            call.respond(stores.history.all(s.id))
        }
    }
}
