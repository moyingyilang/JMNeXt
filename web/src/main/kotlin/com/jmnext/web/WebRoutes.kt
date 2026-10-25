package com.jmnext.web
import io.ktor.server.response.respondBytes
import io.ktor.http.ContentType

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
data class LoginBody(val username: String = "", val password: String = "")

@Serializable
data class MeOut(val loggedIn: Boolean, val username: String? = null, val level: String? = null)

@Serializable
data class ErrOut(val error: String)

/** 从 `Cookie` 头里取会话 id。 */
fun cookieSession(cookieHeader: String?): String? =
    cookieHeader?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("$SESSION_COOKIE=") }
        ?.substringAfter('=')?.takeIf { it.isNotBlank() }

fun Route.apiRoutes(reg: SessionRegistry) {
    route("/api") {
        post("/login") {
            val body = call.receive<LoginBody>()
            if (body.username.isBlank() || body.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrOut("账号或密码为空"))
                return@post
            }
            val s = reg.create()
            val ok = runCatching { s.repo.login(body.username, body.password) }
            if (ok.isFailure) {
                reg.drop(s.id)
                call.respond(HttpStatusCode.Unauthorized, ErrOut("登录失败：${ok.exceptionOrNull()?.message ?: "未知错误"}"))
                return@post
            }
            call.response.headers.append(
                "Set-Cookie",
                "$SESSION_COOKIE=${s.id}; HttpOnly; SameSite=Strict; Path=/",
            )
            val m = ok.getOrNull()
            call.respond(MeOut(loggedIn = true, username = m?.username, level = null))
        }

        post("/logout") {
            reg.drop(cookieSession(call.request.headers["Cookie"]))
            call.response.headers.append("Set-Cookie", "$SESSION_COOKIE=; HttpOnly; Path=/; Max-Age=0")
            call.respond(mapOf("ok" to true))
        }

        get("/me") {
            val s = reg.get(cookieSession(call.request.headers["Cookie"]))
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrOut("未登录"))
            call.respond(MeOut(loggedIn = s.repo.auth.isLoggedIn, username = s.repo.auth.member?.username))
        }

        // 以下都需要会话：凭证明明在服务端，没会话就没有 JWT
        get("/home") {
            val s = reg.get(cookieSession(call.request.headers["Cookie"]))
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrOut("未登录"))
            val r = runCatching { s.repo.promote() }
            r.onSuccess { call.respond(it) }
                .onFailure { call.respond(HttpStatusCode.BadGateway, ErrOut("取首页失败：${it.message}")) }
        }

        get("/search") {
            val s = reg.get(cookieSession(call.request.headers["Cookie"]))
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrOut("未登录"))
            val q = call.request.queryParameters["q"].orEmpty()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            if (q.isBlank()) return@get call.respond(HttpStatusCode.BadRequest, ErrOut("缺少 q"))
            val r = runCatching { s.repo.search(query = q, page = page) }
            r.onSuccess { call.respond(it) }
                .onFailure { call.respond(HttpStatusCode.BadGateway, ErrOut("搜索失败：${it.message}")) }
        }
    }
}

/**
 * 图片代理：`GET /img/{chapter}/{idx}`。
 *
 * **前端永远拿不到 JM 的原始图片地址** —— 防盗链、反切片、缓存都在这里一处解决。
 * 命中缓存直接返回；未命中则取原图 → 需要时反切片 → 落盘 → 返回。
 */
fun Route.imageRoutes(reg: SessionRegistry, cache: ImageCache) {
    get("/img/{chapter}/{idx}") {
        val s = reg.get(cookieSession(call.request.headers["Cookie"]))
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrOut("未登录"))
        val chapter = call.parameters["chapter"].orEmpty()
        val idx = call.parameters["idx"]?.toIntOrNull() ?: -1
        if (chapter.isBlank() || idx < 0) return@get call.respond(HttpStatusCode.BadRequest, ErrOut("参数不对"))

        val key = "img_${chapter}_$idx"
        cache.get(key)?.let {
            call.respondBytes(it, ContentType.parse(JvmUnscramble.formatOf(it)?.let { f -> "image/$f" } ?: "application/octet-stream"))
            return@get
        }
        val payload = runCatching { s.repo.read(chapter) }.getOrNull()
            ?: return@get call.respond(HttpStatusCode.BadGateway, ErrOut("取章节失败"))
        val img = payload.images.getOrNull(idx)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrOut("页码越界"))
        // Referer 用**图片自身的 host**：线路不同域名也不同，硬编码会 403
        val host = runCatching { java.net.URI(img.image).host }.getOrNull()
        val raw = ImageFetcher.get(img.image, referer = host?.let { "https://$it/" })
            ?: return@get call.respond(HttpStatusCode.BadGateway, ErrOut("取图失败"))
        val slices = com.jmnext.data.crypto.JmCrypto.sliceCount(payload.id, img.page.toString())
        val out = JvmUnscramble.unscramble(raw, slices)
        cache.put(key, out)
        call.respondBytes(out, ContentType.parse(JvmUnscramble.formatOf(out)?.let { f -> "image/$f" } ?: "application/octet-stream"))
    }
}
