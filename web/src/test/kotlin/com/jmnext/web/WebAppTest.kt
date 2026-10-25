package com.jmnext.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebAppTest {

    @Test
    fun healthReturnsOk() = testApplication {
        application { module() }
        val r = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsTextSafe().contains("\"status\":\"ok\""), "健康检查应返回 status=ok")
    }

    @Test
    fun desktopUaGetsDesktopLayout() = testApplication {
        application { module() }
        val r = client.get("/") { header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) Firefox/130") }
        assertEquals(HttpStatusCode.OK, r.status)
        val html = r.bodyAsTextSafe()
        assertTrue(html.contains("data-layout=\"desktop\""), "桌面 UA 应拿到桌面布局")
    }

    @Test
    fun mobileUaGetsMobileLayout() = testApplication {
        application { module() }
        val r = client.get("/") { header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148") }
        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsTextSafe().contains("data-layout=\"mobile\""), "手机 UA 应拿到窄屏布局")
    }

    @Test
    fun tabletGoesDesktopLayout() = testApplication {
        application { module() }
        val r = client.get("/") { header("User-Agent", "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)") }
        assertTrue(r.bodyAsTextSafe().contains("data-layout=\"desktop\""), "平板应走桌面布局")
    }

    @Test
    fun uaDetectionRules() {
        assertTrue(isMobileUa("Mozilla/5.0 (Linux; Android 14; Pixel 8) Mobile"))
        assertTrue(isMobileUa("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)"))
        assertTrue(!isMobileUa("Mozilla/5.0 (iPad; CPU OS 17_0)"))
        assertTrue(!isMobileUa("Mozilla/5.0 (Linux; Android 14; SM-X710 Tablet)"))
        assertTrue(!isMobileUa(null))
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.bodyAsTextSafe(): String = bodyAsText()
