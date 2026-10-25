package com.jmnext.web

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
