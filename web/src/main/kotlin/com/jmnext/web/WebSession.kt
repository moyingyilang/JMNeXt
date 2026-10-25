package com.jmnext.web

import com.jmnext.data.JmRepository
import com.jmnext.data.auth.AuthState
import com.jmnext.data.auth.AuthStoreApi
import com.jmnext.data.remote.dto.MemberInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/**
 * 内存版凭证存储。
 *
 * WebUI 的 JWT **只活在服务端内存里**：不写磁盘、不进浏览器。
 * 进程退出即失效，浏览器只剩下一个不透明会话 id。
 * Android 端用的是 Keystore 加密的 `AuthStore`，这里刻意不共用 ——
 * 网页端没有 Keystore，写盘反而扩大泄露面。
 */
class MemoryAuthStore : AuthStoreApi {
    private val _state = MutableStateFlow(AuthState())
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    /** JWT 只保存在这里（内存），[AuthState] 只带登录态与会员信息。 */
    private var jwt: String? = null
    override val token: String? get() = jwt
    override val member: MemberInfo? get() = _state.value.member
    override val isLoggedIn: Boolean get() = !jwt.isNullOrBlank()

    override fun save(token: String, member: MemberInfo?) {
        jwt = token
        _state.value = AuthState(loggedIn = token.isNotBlank(), member = member)
    }

    override fun updateMember(member: MemberInfo?) {
        _state.value = _state.value.copy(member = member)
    }

    override fun clear() {
        jwt = null
        _state.value = AuthState()
    }
}

/** 一个浏览器会话：自己的仓库实例 + 自己的凭证，互相隔离。 */
class WebSession(val id: String) {
    val authStore = MemoryAuthStore()
    val repo: JmRepository = JmRepository.create(authStore = authStore, debug = false)
}

/**
 * 会话表（内存）。用 `SecureRandom` 生成 id；会话不持久化 ——
 * 服务重启后需重新登录，桌面端服务生命周期很短，这个取舍可接受。
 */
class SessionRegistry {
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, WebSession>()
    private val rnd = SecureRandom()

    fun create(): WebSession {
        val bytes = ByteArray(32).also(rnd::nextBytes)
        val id = bytes.joinToString("") { "%02x".format(it) }
        return WebSession(id).also { sessions[id] = it }
    }

    fun get(id: String?): WebSession? = id?.let { sessions[it] }
    fun drop(id: String?) { id?.let { sessions.remove(it) } }
    fun size(): Int = sessions.size
}

const val SESSION_COOKIE = "jmsid"
