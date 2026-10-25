package com.jmnext.web

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.request.receive
import io.ktor.server.application.call
import io.ktor.server.routing.routing
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable

@Serializable
data class GateBody(val code: String = "")

/**
 * 启动服务。返回实际使用的端口（端口被占时顺延），供调用方打印真实地址。
 *
 * 这里刻意不把端口写死死磕：17800 被占用时静默换到 17801…
 * 比"启动失败让用户自己猜"更实用。
 */
fun startWebServer(config: WebConfig = WebConfig(), maxPortTries: Int = 10): Pair<Int, io.ktor.server.engine.EmbeddedServer<*, *>> {
    var lastError: Throwable? = null
    for (offset in 0 until maxPortTries) {
        val port = config.port + offset
        try {
            val server = embeddedServer(CIO, port = port, host = config.host) {
                module()
                routing {
                    // 口令校验入口：局域网模式下前端拿不到其它接口
                    post("/api/gate") {
                        val body = call.receive<GateBody>()
                        if (config.passcode.isNotBlank() && body.code != config.passcode) {
                            call.respond(io.ktor.http.HttpStatusCode.Forbidden, ErrOut("口令不对"))
                            return@post
                        }
                        call.response.headers.append(
                            "Set-Cookie",
                            "$GATE_COOKIE=${config.passcode}; HttpOnly; SameSite=Strict; Path=/",
                        )
                        call.respond(mapOf("ok" to true))
                    }
                }
            }
            server.start(wait = false)
            return port to server
        } catch (t: Throwable) {
            lastError = t
        }
    }
    throw IllegalStateException("端口 ${config.port}..${config.port + maxPortTries - 1} 都不可用", lastError)
}
