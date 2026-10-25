package com.jmnext.web

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 真实 smoke：**真的在端口上起服务**再发 HTTP 请求。
 *
 * 为什么必须有：`testApplication` 是在进程内模拟调用，不经过 socket 绑定与真实 HTTP 栈。
 * 前面 30 条单测全部通过，但"服务能不能启动、端口能不能监听、响应能不能出去"从未被证明。
 * 这条用 `startWebServer`（与桌面端同一个入口）在回环上起真服务，再走 `HttpURLConnection` 请求。
 */
class WebSmokeTest {

    private fun http(url: String): Pair<Int, String> {
        val c = (java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64)")
        }
        return try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
            code to String(body)
        } finally {
            c.disconnect()
        }
    }

    @Test
    fun realServerAnswersOnLoopback() {
        val (port, server) = startWebServer(WebConfig(port = 17890))
        try {
            val (code, body) = http("http://127.0.0.1:$port/api/health")
            assertTrue(code == 200, "健康检查应 200，实际 $code")
            assertTrue(body.contains("\"status\":\"ok\""), "健康检查响应体不对：$body")

            val (pageCode, page) = http("http://127.0.0.1:$port/")
            assertTrue(pageCode == 200, "入口页应 200，实际 $pageCode")
            assertTrue(page.contains("data-layout=\"desktop\""), "桌面 UA 应拿到桌面布局")

            // 未登录访问受保护接口应 401
            val (guardCode, _) = http("http://127.0.0.1:$port/api/home")
            assertTrue(guardCode == 401, "未登录访问 /api/home 应 401，实际 $guardCode")
        } finally {
            server.stop(0, 0)
        }
    }
}
