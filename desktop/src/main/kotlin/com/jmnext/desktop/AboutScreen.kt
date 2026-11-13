package com.jmnext.desktop

import com.jmnext.desktop.i18n.t
import com.jmnext.desktop.i18n.tf

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
        val strings = com.jmnext.desktop.i18n.LocalStrings.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(t("about_title"), style = MaterialTheme.typography.titleLarge)

        Section(t("about_version")) {
            InfoRow(t("about_version_code"), DESKTOP_VERSION)
            InfoRow(t("dest_about_platform"), "${System.getProperty("os.name")} / ${System.getProperty("os.arch")}")
            InfoRow(t("dest_about_runtime"), "JVM " + System.getProperty("java.version"))
            InfoRow(t("dest_about_ui"), t("dest_about_ui_value"))
        }

        Section(t("about_check_update")) {
            Text(
                t("dest_about_update_note"),
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
                                tag == null -> strings["about_check_failed"] ?: ""
                                UpdateCheck.isNewer(tag, DESKTOP_VERSION) -> String.format(strings.getValue("about_check_new"), tag).also { updatable = tag }
                                else -> String.format(strings.getValue("about_check_latest"), tag)
                            }
                            System.err.println("[关于] $result")
                        }
                    },
                ) { Text(if (checking) t("about_checking") else t("about_check_update")) }

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
                                Text(if (i == 0) tf("dest_about_download_recommended", asset) else tf("dest_about_download_alt", asset))
                            }
                        }
                        Text(
                            text = t("dest_about_bundle_note"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { uri.openUri(UpdateCheck.releaseUrl(tag)) }) { Text(t("dest_about_open_release")) }
                    }
                }
            }
            result?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        Section(t("about_desc_title")) {
            Text(
                // 与手机端同一口径：说明这一栏讲项目本身，不讲实现细节与材质
                t("about_desc"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Section(t("about_links")) {
            TextButton(onClick = { uri.openUri("https://github.com/${UpdateCheck.REPO}") }) { Text("项目主页") }
            TextButton(onClick = { uri.openUri("https://github.com/${UpdateCheck.REPO}/releases") }) {
                Text(t("about_link_releases"))
            }
            TextButton(onClick = { uri.openUri("https://github.com/${UpdateCheck.REPO}/issues") }) { Text("问题反馈") }
        }

        Section(t("about_license_title")) {
            Text(
                t("about_license"),
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
