package com.jmnext.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.data.UpdateCheck
import com.jmnext.data.UpdateCheck.fetchLatestTag
import kotlinx.coroutines.launch

/**
 * 关于页（2.0.0 桌面端）。
 *
 * 内容与 Android 端同一套：版本与变体、检查更新、项目说明、链接、许可。
 * **变体那一行是刻意的** —— 桌面端将来会有 aarch64/x86_64、Windows/Linux 多个产物，
 * 只报一个版本号用户分不清自己装的是哪个。
 *
 * 检查更新复用共享层的判断逻辑（UpdateCheck.isNewer）：它会忽略版本后缀、
 * 且在拿不到 tag 时绝不报"有更新"。
 */
@Composable
fun AboutScreen(repository: JmRepository) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var updatable by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("关于", style = MaterialTheme.typography.titleLarge)

        Section("版本") {
            InfoRow("版本号", DESKTOP_VERSION)
            InfoRow("平台", "${System.getProperty("os.name")} / ${System.getProperty("os.arch")}")
            InfoRow("运行时", "JVM ${System.getProperty("java.version")}")
            InfoRow("界面", "Compose Desktop（主题取自 moyingyilang.github.io 的设计令牌）")
        }

        Section("检查更新") {
            Text(
                "从 GitHub Releases 读取最新版本号与本机比较，只发一个请求、不上传任何信息。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        result = null
                        updatable = null
                        scope.launch {
                            val tag = runCatching { fetchLatestTag(repository.okHttp) }.getOrNull()
                            checking = false
                            result = when {
                                tag == null -> "没取到更新信息（网络不通，或 GitHub 暂时不可用）"
                                UpdateCheck.isNewer(tag, DESKTOP_VERSION) -> "有新版本：$tag".also { updatable = tag }
                                else -> "已是最新版本（远端 $tag）"
                            }
                            System.err.println("[关于] $result")
                        }
                    },
                ) { Text(if (checking) "检查中…" else "检查更新") }

                updatable?.let { tag ->
                    // 按本机系统与架构给直链（issue #5 的桌面端对应问题：发布页附件多，用户不知道该下哪个）。
                    // 统一包排在第一个：内含两套运行时，不用分辨架构。
                    val names = UpdateCheck.desktopAssetNames(
                        tag,
                        System.getProperty("os.name"),
                        System.getProperty("os.arch"),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        names.forEachIndexed { i, asset ->
                            TextButton(onClick = { uri.openUri(UpdateCheck.assetUrl(tag, asset)) }) {
                                Text(if (i == 0) "下载更新包（$asset，推荐）" else "或下载 $asset")
                            }
                        }
                        Text(
                            text = "推荐包内含两套运行时，会自动按架构选择，无需分辨。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { uri.openUri(UpdateCheck.releaseUrl(tag)) }) { Text("打开发布页") }
                    }
                }
            }
            result?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        Section("说明") {
            Text(
                // 与手机端同一口径：说明这一栏讲项目本身，不讲实现细节与材质
                "JMNeXt 是禁漫（JMComic）的第三方客户端，用 Kotlin 与 Compose 重写。" +
                    "不做插槽广告、不采集行为，登录凭证经系统密钥库加密保存。" +
                    "阅读支持条漫与双页、伪长图拼接无接缝；阅读进度与收藏本地保存，" +
                    "也能通过 WebDAV 在你自己的网盘上跨设备同步（历史、收藏、屏蔽名单）。" +
                    "屏蔽规则按关键词 / 分类 / 标签生效，命中即从列表里消失。" +
                    "桌面端与 Android 端共用同一份跨平台数据层（:shared），" +
                    "差异只在平台实现（键值存储、密钥来源、图像解码）与界面。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Section("链接") {
            TextButton(onClick = { uri.openUri("https://github.com/${UpdateCheck.REPO}") }) { Text("项目主页") }
            TextButton(onClick = { uri.openUri("https://github.com/${UpdateCheck.REPO}/releases") }) {
                Text("发布页（下载新版本）")
            }
            TextButton(onClick = { uri.openUri("https://github.com/${UpdateCheck.REPO}/issues") }) { Text("问题反馈") }
        }

        Section("许可") {
            Text(
                "本项目以 AGPL-3.0-only 授权。禁漫、JMComic 及相关内容的权利归其权利人所有；" +
                    "本应用与官方无关，仅提供客户端实现。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
