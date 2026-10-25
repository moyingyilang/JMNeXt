package com.jmnext.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import kotlin.random.Random

/**
 * 服务配置与局域网门禁。
 *
 * 默认**只监听 127.0.0.1**：局域网必须由用户显式开启，并且要再过一道口令。
 * 这不是"多一层保险"，而是唯一的安全边界 —— 一旦监听 0.0.0.0，同网段任何人都能访问，
 * 而我们的接口里有账号会话与图片代理。
 */
data class WebConfig(
    val lan: Boolean = false,
    val port: Int = 17800,
    /** 局域网模式下的一次性口令；`lan=false` 时为空。 */
    val passcode: String = if (false) "" else "",
) {
    val host: String get() = if (lan) "0.0.0.0" else "127.0.0.1"

    companion object {
        /** 生成 6 位数字口令。不用随机字符串：手输场景下数字最省事。 */
        fun newPasscode(rnd: Random = Random.Default): String =
            (1..6).joinToString("") { rnd.nextInt(0, 10).toString() }
    }
}

const val GATE_COOKIE = "jmgate"

/** 从请求里取"是否已通过门禁"：cookie 或 `?code=` 都算。 */
fun ApplicationCall.gatePassed(code: String): Boolean {
    if (code.isBlank()) return true
    val fromQuery = request.queryParameters["code"]
    if (fromQuery != null && fromQuery == code) return true
    val cookie = request.headers["Cookie"]
    val fromCookie = cookie?.split(';')?.map { it.trim() }
        ?.firstOrNull { it.startsWith("$GATE_COOKIE=") }?.substringAfter('=')
    return fromCookie == code
}

/** 局域网模式下拒绝未带口令的请求。 */
suspend fun ApplicationCall.rejectIfUnauthorized(code: String): Boolean {
    if (gatePassed(code)) return false
    respond(HttpStatusCode.Forbidden, ErrOut("需要访问口令（局域网模式）"))
    return true
}
