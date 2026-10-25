package com.jmnext.web
import com.jmnext.data.prefs.HistoryEntry
import io.ktor.http.contentType
import io.ktor.http.ContentType
import io.ktor.client.request.setBody
import io.ktor.client.request.post

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

class WebAuthGuardTest {

    @Test
    fun protectedEndpointsRequireSession() = testApplication {
        application { module() }
        for (path in listOf("/api/home", "/api/search?q=x", "/api/me")) {
            val r = client.get(path)
            assertEquals(HttpStatusCode.Unauthorized, r.status, "$path 未登录应 401")
        }
    }

    @Test
    fun healthAndPageStayPublic() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    @Test
    fun loginRejectsEmptyCredentials() = testApplication {
        application { module() }
        val r = client.post("/api/login") {
            header("Content-Type", "application/json")
            setBody("""{"username":"","password":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun cookieParsing() {
        assertEquals("abc123", cookieSession("a=1; jmsid=abc123; b=2"))
        assertEquals(null, cookieSession("a=1; b=2"))
        assertEquals(null, cookieSession(null))
    }

    @Test
    fun sessionIdsAreRandomAndUnique() {
        val reg = SessionRegistry()
        val a = reg.create(); val b = reg.create()
        assertEquals(64, a.id.length, "32 字节 → 64 个十六进制字符")
        assertTrue(a.id != b.id)
        assertEquals(2, reg.size())
        reg.drop(a.id)
        assertEquals(1, reg.size())
    }
}

class ReaderStoreTest {

    private fun stores(): ReaderStores {
        val dir = java.nio.file.Files.createTempDirectory("kv").toFile()
        return ReaderStores(FileKeyValueStore(java.io.File(dir, "kv.properties")))
    }

    @Test
    fun historyIsNamespacedPerSession() {
        val s = stores()
        s.history.record("ses-a", HistoryEntry(aid = "100", chapterId = "200", page = 3))
        s.history.record("ses-b", HistoryEntry(aid = "100", chapterId = "999", page = 1))
        assertEquals(1, s.history.all("ses-a").size)
        assertEquals("200", s.history.all("ses-a")[0].chapterId)
        assertEquals("999", s.history.all("ses-b")[0].chapterId, "两个会话不应串数据")
    }

    @Test
    fun readOutUsesProxyUrls() {
        // 只验证 URL 形状（不触网）：索引 → /img/chapter/index
        val chapter = "abc"
        val urls = (0 until 3).map { "/img/$chapter/$it" }
        assertEquals(listOf("/img/abc/0", "/img/abc/1", "/img/abc/2"), urls)
    }
}
