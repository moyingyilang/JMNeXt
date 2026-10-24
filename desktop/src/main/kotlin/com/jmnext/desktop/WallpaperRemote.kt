package com.jmnext.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jmnext.wallpaper.WallpaperMode
import com.jmnext.wallpaper.WallpaperSources
import com.jmnext.wallpaper.WallpaperState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate

/**
 * 桌面端的在线壁纸。照 Android 端 `app/.../data/wallpaper/WallpaperStore.kt` 移植。
 *
 * 为什么之前没有：桌面端只做了"预设渐变 + 本地图片 + 模糊压暗"，把**在线来源那一整套**
 * （模式选择、Bing 每日、二次元、自动轮换、署名、自定义地址）漏掉了。用户指出后补上。
 *
 * 与 Android 端一致的规则（语义写在 shared 的 WallpaperSources 里，两端共用）：
 *  - 默认 [WallpaperMode.Off]：阅读器默认不发任何第三方请求，想用的人自己去开；
 *  - 缓存轮换：攒够 4 到 8 张后就只在本地轮换，不再打接口；
 *  - Bing 每天只重新取一次（保住"每日"的意义）；手动换一张有 800ms 冷却；
 *  - 轮换时跳过当前这张；混合模式按游标奇偶决定来源；
 *  - 署名必须显示（Bing 回摄影者与地点，二次元固定写"二次元图源"）。
 *
 * 模糊与压暗不在这里：桌面端已有 Appearance.wallpaperBlur / Appearance.dim，
 * 避免同一件设置存两份。
 *
 * 网络用 HttpURLConnection（与 RemoteImage 同款）：零新依赖，且它默认跟随重定向、
 * 能拿到**重定向之后的最终地址** —— 正是二次元源需要的（记下最终地址，之后直接用，省掉跳转）。
 */
object RemoteWallpaper {

    private val prefs = PreferencesKeyValueStore("jm_wallpaper")
    private val json = Json { ignoreUnknownKeys = true }

    /** 手动连点的冷却，避免把第三方接口当图床刷。 */
    private var lastFetchAt = 0L
    private var fetchedCredit: String? = null

    /** 由界面告知当前窗口是不是竖长比例（Bing 的尺寸段据此改 1080x1920 或 1920x1080）。 */
    var portraitHint: Boolean = false

    var state by mutableStateOf(load())
        private set

    fun setMode(mode: WallpaperMode) = update { it.copy(mode = mode, error = null) }
    fun setInterval(minutes: Int) = update { it.copy(intervalMinutes = minutes.coerceAtLeast(0)) }
    fun setCustomUrl(url: String) = update { it.copy(customUrl = url.trim()) }

    /**
     * 换一张。[force] 为 true 表示用户手动点的：忽略冷却与"今天已取过"的限制，
     * 但缓存里还有没用过的地址时仍然优先用缓存，不额外打接口。
     */
    /** 清除壁纸缓存并立刻换一张（与 Android 的 clearCache 对齐）。 */
    suspend fun clearCache() {
        prefs.remove(KEY_BING_CACHE)
        prefs.remove(KEY_ANIME_CACHE)
        prefs.remove(KEY_BING_DAY)
        prefs.remove(KEY_CURSOR)
        prefs.putBoolean(KEY_WANT_REFRESH, true)
        update { it.copy(url = null, error = null) }
        next(force = true)
    }

    suspend fun next(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchAt < WallpaperSources.COOLDOWN_MS) return
        lastFetchAt = now
        update { it.copy(loading = true, error = null) }
        val result = runCatching { pick(force) }
        update { prev ->
            result.fold(
                onSuccess = { it.copy(loading = false) },
                onFailure = { prev.copy(loading = false, error = it.message ?: "取图失败") },
            )
        }
    }

    private suspend fun pick(force: Boolean): WallpaperState = withContext(Dispatchers.IO) {
        val s = state
        when (s.mode) {
            WallpaperMode.Off -> return@withContext s.copy(url = null, credit = null, error = null)
            WallpaperMode.Custom -> return@withContext if (s.customUrl.isBlank()) {
                s.copy(url = null, credit = null, error = "还没有填地址")
            } else {
                s.copy(url = s.customUrl, credit = null, error = null)
            }
            else -> Unit
        }

        val cursor = prefs.getString(KEY_CURSOR, "0")?.toIntOrNull() ?: 0
        val wantBing = WallpaperSources.wantBingFor(s.mode, cursor)
        val cacheKey = if (wantBing) KEY_BING_CACHE else KEY_ANIME_CACHE
        var cache = readCache(cacheKey)
        val today = LocalDate.now().toString()
        val staleBing = wantBing && prefs.getString(KEY_BING_DAY, null) != today

        if (WallpaperSources.needFetch(
            cache.size, wantBing, prefs.getString(KEY_BING_DAY, null), today,
            forceRefresh = prefs.getBoolean(KEY_WANT_REFRESH, false),
        )) {
            val fetched = if (wantBing) fetchBing(portraitHint) else fetchAnime()
            cache = (cache + fetched).distinct().take(WallpaperSources.CACHE_MAX)
            writeCache(cacheKey, cache)
            prefs.putBoolean(KEY_WANT_REFRESH, false)
            if (wantBing) {
                prefs.putString(KEY_BING_DAY, today)
                prefs.putString(KEY_CREDIT, fetchedCredit)
            }
        }
        if (cache.isEmpty()) throw IllegalStateException("图源没有返回可用地址")

        val (url, newCursor) = WallpaperSources.nextFromPool(cache, cursor, s.url)
        prefs.putString(KEY_CURSOR, newCursor.toString())
        // 回绕到 0 表示这一轮看完了：下次取图前先补一批新地址
        if (newCursor == 0) prefs.putBoolean(KEY_WANT_REFRESH, true)
        s.copy(
            url = url,
            credit = if (wantBing) prefs.getString(KEY_CREDIT, null) else WallpaperSources.ANIME_CREDIT,
            error = null,
        )
    }

    /** Bing 每日：接口回 JSON（url 与 copyright），尺寸段按窗口比例改写。 */
    private fun fetchBing(portrait: Boolean): List<String> {
        val body = httpGet(WallpaperSources.BING_API, "application/json")
            ?: throw IllegalStateException("Bing 接口无响应")
        val parsed = runCatching { json.decodeFromString(BingResponse.serializer(), body) }.getOrNull()
            ?: throw IllegalStateException("Bing 接口返回无法解析")
        fetchedCredit = parsed.copyright
        val raw = parsed.url?.takeIf { it.isNotBlank() } ?: return emptyList()
        return listOf(WallpaperSources.bingSizedUrl(raw, portrait))
    }

    /**
     * 二次元源：这些地址**直接返回图片字节**，所以可用性只能靠真发一次请求判断；
     * 成功时记下重定向之后的最终地址。三个源依次尝试，全挂才算失败。
     */
    private fun fetchAnime(): List<String> {
        val failures = mutableListOf<String>()
        for (api in WallpaperSources.ANIME_APIS) {
            val got = runCatching { probeFinalUrl(api) }
            got.onSuccess { return listOf(it) }
            got.onFailure { failures += "$api: ${it.message}" }
        }
        throw IllegalStateException("二次元图源都不可用（${failures.joinToString("；")}）")
    }

    private fun httpGet(url: String, accept: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", accept)
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** 只读一小段就够判断可用性，不必把整张图拉下来（绘制时由 RemoteImage 自己取）。 */
    private fun probeFinalUrl(url: String): String {
        var conn: HttpURLConnection? = null
        return try {
            conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "image/*")
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val finalUrl = conn.url.toString()
            conn.inputStream.use { it.read(ByteArray(512)) }
            finalUrl
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun readCache(key: String): List<String> =
        prefs.getString(key, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    private fun writeCache(key: String, list: List<String>) = prefs.putString(key, list.joinToString("\n"))

    private fun update(transform: (WallpaperState) -> WallpaperState) {
        val next = transform(state)
        prefs.putString(KEY_MODE, next.mode.name)
        prefs.putString(KEY_URL, next.url)
        prefs.putString(KEY_CREDIT_UI, next.credit)
        prefs.putString(KEY_INTERVAL, next.intervalMinutes.toString())
        prefs.putString(KEY_CUSTOM, next.customUrl)
        state = next
    }

    private fun load(): WallpaperState = WallpaperState(
        mode = WallpaperMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) }
            ?: WallpaperMode.Off,
        url = prefs.getString(KEY_URL, null),
        credit = prefs.getString(KEY_CREDIT_UI, null),
        dim = Appearance.dim / 100f,
        intervalMinutes = prefs.getString(KEY_INTERVAL, null)?.toIntOrNull() ?: 0,
        customUrl = prefs.getString(KEY_CUSTOM, null).orEmpty(),
    )

    @Serializable
    private data class BingResponse(val url: String? = null, val copyright: String? = null)

    private const val USER_AGENT = "Mozilla/5.0 (jmnext-desktop)"
    private const val KEY_MODE = "mode"
    private const val KEY_URL = "url"
    private const val KEY_CREDIT = "credit"          // Bing 接口回的署名（缓存用）
    private const val KEY_CREDIT_UI = "creditShown"  // 界面正在显示的署名
    private const val KEY_INTERVAL = "interval"
    private const val KEY_CUSTOM = "customUrl"
    private const val KEY_CURSOR = "cursor"
    private const val KEY_WANT_REFRESH = "want_refresh"
    private const val KEY_BING_DAY = "bingDay"
    private const val KEY_BING_CACHE = "bingCache"
    private const val KEY_ANIME_CACHE = "animeCache"
}
