package com.jmnext.data

import android.content.Context
import com.jmnext.BuildConfig
import com.jmnext.JmApp
import com.jmnext.ui.Notices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * 启动时检查一次更新（很久以前提过、2.2.2 补上）。
 *
 * 规则：
 * - 一进应用就跑一次，但**每天最多一次**（结果时间记在独立 prefs 里）；
 * - 只在**真的有新版本**时提示；已最新 / 网络不通 / GitHub 挂了都静默；
 * - 同一版本只提示一次，避免每天开机都弹同一条；
 * - 只发一个 GET（`releases/latest`），不带任何本机信息；
 * - 用顶部提示条（[Notices]），不弹窗、不打断。
 */
object StartupUpdateCheck {

    private const val PREFS = "jm_update_check"
    private const val KEY_LAST_AT = "last_check_at"
    private const val KEY_LAST_TAG = "last_notified_tag"
    private const val MIN_INTERVAL_MS = 24L * 60 * 60 * 1000

    fun shouldCheck(context: Context, now: Long = System.currentTimeMillis(), force: Boolean = false): Boolean {
        if (force) return true
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return now - sp.getLong(KEY_LAST_AT, 0L) >= MIN_INTERVAL_MS
    }

    /** 执行检查（协程内调用）。有新版本返回 tag，否则 null。 */
    suspend fun run(app: JmApp, force: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!shouldCheck(app, force = force)) return@withContext null
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val tag = fetchLatestTag(app)
        // 成功失败都记时间：失败时也不该频繁重试，避免离线用户被反复打网络
        sp.edit().putLong(KEY_LAST_AT, System.currentTimeMillis()).apply()
        if (tag == null) return@withContext null
        if (!UpdateCheck.isNewer(tag, BuildConfig.VERSION_NAME)) return@withContext null
        if (sp.getString(KEY_LAST_TAG, null) == tag) return@withContext null
        sp.edit().putString(KEY_LAST_TAG, tag).apply()

        withContext(Dispatchers.Main) {
            Notices.show("有新版本 $tag（当前 ${BuildConfig.VERSION_NAME}），可在「我的 → 关于」查看")
        }
        tag
    }

    private fun fetchLatestTag(app: JmApp): String? = runCatching {
        val req = Request.Builder()
            .url(UpdateCheck.LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .build()
        app.repository.okHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            Json.parseToJsonElement(resp.body.string()).jsonObject["tag_name"]?.jsonPrimitive?.content
        }
    }.getOrNull()
}
