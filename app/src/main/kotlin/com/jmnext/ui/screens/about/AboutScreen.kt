package com.jmnext.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.jmnext.BuildConfig
import com.jmnext.LiteFeatures
import com.jmnext.data.UpdateCheck
import com.jmnext.ui.LocalRepository
import com.jmnext.ui.components.GlassLevel
import com.jmnext.ui.components.GlassSurface
import com.jmnext.ui.components.GlassTopBar
import com.jmnext.ui.theme.JmTheme
import com.jmnext.ui.theme.Radius
import com.jmnext.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * 「关于」页（1.8.0）。
 *
 * 参考 HyperCeiler 的关于页结构（版本信息 → 项目说明 → 链接 → 致谢），
 * 但内容按本项目的实际情况组织：这里最需要说清的是**变体**（full / lite、是否 debug），
 * 因为四个变体的版本号只差一个后缀，用户容易分不清自己装的是哪个。
 *
 * ## 检查更新为什么要自己发请求
 *
 * 应用的业务请求都走 `JmRepository` 的客户端（基址是 JM 的接口主机），
 * 而更新信息在 GitHub —— **不同主机**，所以这里直接用仓库那个 OkHttp
 * （复用连接池与超时设置，也不新建客户端）。
 *
 * 判定逻辑在 [UpdateCheck] 里（纯函数、有测试）；这里只负责取 `tag_name` 与呈现。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = JmTheme.colors
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var updatable by remember { mutableStateOf<String?>(null) }

    val variant = buildString {
        append(if (LiteFeatures.ENABLED) "lite" else "full")
        if (BuildConfig.DEBUG) append(" · debug")
    }

    Column(modifier.fillMaxSize()) {
        GlassTopBar(
            title = "关于",
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = c.accent)
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            InfoCard("版本") {
                InfoRow("版本号", BuildConfig.VERSION_NAME)
                InfoRow("变体", variant)
                if (LiteFeatures.ENABLED) {
                    Text(
                        text = "lite 面向低性能设备：只保留两种纯色界面、去掉壁纸与模糊，" +
                            "并关闭图片淡入、条目动画，阅读器预取也更保守。",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                    )
                }
            }

            InfoCard("检查更新") {
                // 只放一个独立按钮：说明文字去掉了，点下去才发请求、才出现结果。
                // 「要不要检查」由用户决定，卡片本身不再先讲一遍原理。
                Button(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        result = null
                        updatable = null
                        scope.launch {
                            val tag = runCatching { fetchLatestTag(repo.okHttp) }.getOrNull()
                            checking = false
                            result = when {
                                tag == null -> "没取到更新信息（网络不通，或 GitHub 暂时不可用）"
                                UpdateCheck.isNewer(tag, BuildConfig.VERSION_NAME) ->
                                    "有新版本：$tag".also { updatable = tag }
                                else -> "已是最新版本（远端 $tag）"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (checking) "检查中…" else "检查更新") }

                result?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                updatable?.let { tag ->
                    // 直接给「该变体的安装包」直链（issue #5 的症结）：
                    // 发布页上的附件名是 Android-full-<版本>.apk，**名字里没有架构**（一个包同时含
                    // arm64 与 x86_64 的 native 库），用户按"arm64 版"去找自然找不到。
                    val isLite = BuildConfig.APPLICATION_ID.endsWith(".lite")
                    val asset = UpdateCheck.androidAssetName(tag, lite = isLite)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        TextButton(onClick = { uri.openUri(UpdateCheck.assetUrl(tag, asset)) }) {
                            Text("下载更新包（$asset）")
                        }
                        Text(
                            text = "一个安装包同时支持 arm64 与 x86_64，无需区分架构。" +
                                "若直链打不开，可改用下面的发布页自行挑选。",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary,
                        )
                        TextButton(onClick = { uri.openUri(UpdateCheck.releaseUrl(tag)) }) { Text("打开发布页") }
                    }
                }
            }

            InfoCard("说明") {
                Text(
                    // 这一栏讲**项目本身**，不讲材质：材质属于「关于界面长什么样」，
                    // 用户点进来说明想看的是「这是个什么应用、能做什么」。
                    text = "JMNeXt 是禁漫（JMComic）的第三方客户端，用 Kotlin 与 Jetpack Compose 重写。" +
                        "不做插槽广告、不采集行为，登录凭证经 Keystore 加密落盘。" +
                        "阅读器支持条漫与双页、伪长图拼接无接缝，阅读进度与收藏可本地保存，" +
                        "也能通过 WebDAV 在你自己的网盘上跨设备同步（历史、收藏、屏蔽名单）。" +
                        "屏蔽规则按关键词 / 分类 / 标签生效，命中即从列表里消失。" +
                        "桌面端另有浏览器界面：起一个只监听本机的服务，手机与电脑都能用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }

            InfoCard("链接") {
                LinkRow("项目主页") { uri.openUri("https://github.com/${UpdateCheck.REPO}") }
                LinkRow("发布页（下载新版本）") { uri.openUri("https://github.com/${UpdateCheck.REPO}/releases") }
                LinkRow("问题反馈") { uri.openUri("https://github.com/${UpdateCheck.REPO}/issues") }
            }

            InfoCard("许可") {
                Text(
                    text = "本项目以 AGPL-3.0-only 授权。禁漫、JMComic 及相关内容的权利归其权利人所有；" +
                        "本应用与官方无关，仅提供客户端实现。",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                )
            }
        }
    }
}

/** 从 GitHub 的 `releases/latest` 里取 `tag_name`；失败返回 null。 */
private suspend fun fetchLatestTag(client: okhttp3.OkHttpClient): String? = withContext(Dispatchers.IO) {
    val req = Request.Builder()
        .url(UpdateCheck.LATEST_RELEASE_API)
        .header("Accept", "application/vnd.github+json")
        .build()
    runCatching {
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body.string()
            Json.parseToJsonElement(body).jsonObject["tag_name"]?.jsonPrimitive?.content
        }
    }.getOrNull()
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    val c = JmTheme.colors
    GlassSurface(level = GlassLevel.Card, shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.lg)) {
        Column(
            // 卡片内边距按规范 16dp（原来是 12dp）
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 卡片标题与设置页的 SettingCard 对齐（titleMedium），此前这里是 titleSmall
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = c.text)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        // 与设置页 InfoRow 同一套层级：标签 bodyMedium + 次级色，值 bodyMedium + 主色
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = JmTheme.colors.textSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = JmTheme.colors.text)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    // 触控高度 ≥48dp（设计规范）：关于页的三个链接此前只有文字高度
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text = label, modifier = Modifier.fillMaxWidth())
    }
}
