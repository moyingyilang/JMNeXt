package com.jmnext.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable

/**
 * WebUI 服务（2.2.2）。
 *
 * 设计要点（第一片只落地骨架，接口逐片补）：
 * - **只监听 127.0.0.1**：局域网访问必须是显式开启 + 口令，默认不开。
 * - **两套 UI 按访问头分流**：桌面宽屏与手机窄屏共用同一套 API，
 *   由 `isMobileUa()` 决定注入哪份页面。这样前端不需要在运行时做布局探测，
 *   服务端一次判断即可，也便于测试（同一 URL、不同 UA，断言不同结果）。
 * - 凭证永远不进浏览器：后续登录接口在服务端换会话 cookie。
 */
@Serializable
data class Health(val status: String, val version: String)

const val WEBUI_VERSION = "0.1.0"

/** 手机 UA 判定。只认特征串，不追求完备 —— 判错的后果只是布局不理想，不是功能不可用。 */
fun isMobileUa(ua: String?): Boolean {
    if (ua.isNullOrBlank()) return false
    val s = ua.lowercase()
    return listOf("iphone", "ipod", "android", "mobile", "windows phone", "harmonyos").any { it in s } &&
        // iPad 与 Android 平板走桌面布局（屏幕够宽）
        !("ipad" in s) && !("tablet" in s)
}

fun Application.module(
    config: WebConfig = WebConfig(),
    registry: SessionRegistry = SessionRegistry(),
    imageCache: ImageCache = ImageCache(java.io.File(System.getProperty("java.io.tmpdir"), "jmnext-web-img")),
    stores: ReaderStores = ReaderStores(FileKeyValueStore(java.io.File(System.getProperty("java.io.tmpdir"), "jmnext-web/kv.properties"))),
    favorites: com.jmnext.data.prefs.FavoriteStore = com.jmnext.data.prefs.FavoriteStore(FileKeyValueStore(java.io.File(System.getProperty("java.io.tmpdir"), "jmnext-web/fav.properties"))),
    settings: FileKeyValueStore = FileKeyValueStore(java.io.File(System.getProperty("java.io.tmpdir"), "jmnext-web/settings.properties")),
) {
    install(ContentNegotiation) {
        json()
    }

    // wasm 前端与 API 分端口部署（17802 / 17800），浏览器按跨源处理；
    // 只放行本机回环来源，局域网模式由口令把守。
    install(io.ktor.server.plugins.cors.routing.CORS) {
        allowHost("127.0.0.1:17800", schemes = listOf("http"))
        allowHost("127.0.0.1:17801", schemes = listOf("http"))
        allowHost("127.0.0.1:17802", schemes = listOf("http"))
        allowHost("localhost:17800", schemes = listOf("http"))
        allowHost("localhost:17802", schemes = listOf("http"))
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowCredentials = true
    }

    routing {
        gateRoutes(config)
        apiRoutes(registry)
        imageRoutes(registry, imageCache)
        readerRoutes(registry, stores)
        detailRoutes(registry, favorites, settings)

        get("/api/health") {
            call.respond(Health(status = "ok", version = WEBUI_VERSION))
        }

        // 入口页：按 UA 直接给出对应布局的页面（同一次请求内决定，不做前端跳转）
        get("/") {
            val mobile = isMobileUa(call.request.headers["User-Agent"])
            val page = if (mobile) "web/mobile.html" else "web/desktop.html"
            val html = this::class.java.classLoader.getResource(page)?.readText()
            if (html == null) {
                call.respondText("缺少静态资源：$page", ContentType.Text.Plain, HttpStatusCode.InternalServerError)
            } else {
                call.respondText(html, ContentType.Text.Html)
            }
        }

        // 静态资源（css/js/图标），与入口页同目录
        staticResources("/static", "web/static")
    }
}
