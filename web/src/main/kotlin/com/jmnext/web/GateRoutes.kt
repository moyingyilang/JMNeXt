package com.jmnext.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/**
 * 局域网口令入口。
 *
 * 默认（`passcode` 为空）不过口令 —— 此时服务只听 `127.0.0.1`，外面根本连不上。
 * 一旦用户开启局域网，`passcode` 必须非空，浏览器要先 `POST /api/gate` 换一个
 * `HttpOnly` cookie 才能用其它接口。
 */
fun Route.gateRoutes(config: WebConfig) {
    post("/api/gate") {
        val body = call.receive<GateBody>()
        if (config.passcode.isNotBlank() && body.code != config.passcode) {
            call.respond(HttpStatusCode.Forbidden, ErrOut("口令不对"))
            return@post
        }
        call.response.headers.append(
            "Set-Cookie",
            "$GATE_COOKIE=${config.passcode}; HttpOnly; SameSite=Strict; Path=/",
        )
        call.respond(mapOf("ok" to true))
    }
}
