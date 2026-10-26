package com.jmnext.data.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * 本地 WebDAV 测试服务。
 *
 * 为什么自己写一个而不是连真实网盘：
 * 1. 真实网盘需要账号（123 盘的 VIP 已过期），CI 里也不该依赖外部服务；
 * 2. 同步逻辑真正会踩的坑是「PUT 覆盖 / GET 404 / MKCOL 已存在 / Basic 认证」，
 *    这四条用一个 200 行的服务就能覆盖，比端到端连网盘更可控、更快。
 *
 * 支持：GET（读文件，缺失 404）、PUT（写文件，父目录不存在返回 409）、
 * MKCOL（建目录，已存在 405）、Basic 认证。**故意不支持**账号密码错误以外的任何
 * DAV 特性 —— 我们只需要它当同步的落地端。
 */
class LocalWebDavServer(
    private val expectedUser: String = "u",
    private val expectedPass: String = "p",
) {
    private lateinit var server: HttpServer
    private lateinit var root: Path

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    fun start(): LocalWebDavServer {
        root = Files.createTempDirectory("dav")
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex -> handle(ex) }
        server.start()
        return this
    }

    fun stop() {
        server.stop(0)
        runCatching { deleteRecursively(root) }
    }

    private fun handle(ex: HttpExchange) {
        try {
            if (!authorized(ex)) {
                ex.responseHeaders.add("WWW-Authenticate", "Basic realm=\"dav\"")
                ex.sendResponseHeaders(401, -1); return
            }
            val rel = ex.requestURI.path.trim('/')
            val target = root.resolve(rel).normalize()
            if (!target.startsWith(root)) { ex.sendResponseHeaders(403, -1); return }

            when (ex.requestMethod.uppercase()) {
                "MKCOL" -> {
                    if (Files.exists(target)) {
                        ex.sendResponseHeaders(405, -1)
                    } else {
                        Files.createDirectories(target)
                        ex.sendResponseHeaders(201, -1)
                    }
                }
                "PUT" -> {
                    val parent = target.parent
                    if (parent != null && !Files.exists(parent)) {
                        ex.sendResponseHeaders(409, -1) // 父目录缺失，和真实服务一致
                    } else {
                        val body = ex.requestBody.readBytes()
                        Files.write(target, body)
                        ex.sendResponseHeaders(201, -1)
                    }
                }
                "GET" -> {
                    if (!Files.isRegularFile(target)) {
                        ex.sendResponseHeaders(404, -1)
                    } else {
                        val bytes = Files.readAllBytes(target)
                        ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
                        ex.sendResponseHeaders(200, bytes.size.toLong())
                        ex.responseBody.use { it.write(bytes) }
                    }
                }
                else -> ex.sendResponseHeaders(405, -1)
            }
        } catch (t: Throwable) {
            runCatching { ex.sendResponseHeaders(500, -1) }
        } finally {
            runCatching { ex.close() }
        }
    }

    private fun authorized(ex: HttpExchange): Boolean {
        val header = ex.requestHeaders.getFirst("Authorization") ?: return false
        if (!header.startsWith("Basic ")) return false
        val decoded = String(Base64.getDecoder().decode(header.removePrefix("Basic ").trim()))
        return decoded == "$expectedUser:$expectedPass"
    }

    private fun deleteRecursively(p: Path) {
        Files.walk(p).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}
