package com.jmnext.ui.screens.about

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import com.jmnext.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
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
    val ctx = LocalContext.current
    val c = JmTheme.colors
    // 文字是浅色就说明当前是深色主题，用它挑字标和徽章的版本
    val dark = c.text.luminance() > 0.5f
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var updatable by remember { mutableStateOf<String?>(null) }
    // 最新版的更新说明（新版才取得到），用于“这次更新了什么”
    var notes by remember { mutableStateOf("") }

    val variant = buildString {
        append(if (LiteFeatures.ENABLED) "lite" else "full")
        if (BuildConfig.DEBUG) append(" · debug")
    }

    Column(modifier.fillMaxSize()) {
        GlassTopBar(
            title = stringResource(R.string.about_title),
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.about_back), tint = c.accent)
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
              // 自上而下：字标、两枚徽章、版本行、一句介绍
              Column(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
              ) {
                  Image(
                      painter = painterResource(
                          if (dark) R.drawable.about_title_dark else R.drawable.about_title_light,
                      ),
                      contentDescription = "JMNeXt",
                      modifier = Modifier
                          .width(170.dp)
                          .aspectRatio(1280f / 300f),
                  )
                  Spacer(Modifier.height(Spacing.xxs))
                  // 这两枚是图，不是文字：内容分别是 Kotlin + Compose 和作者署名
                  Column(
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                  ) {
                      Image(
                          painter = painterResource(
                              if (dark) R.drawable.kotlin_compose_dark else R.drawable.kotlin_compose_light,
                          ),
                          contentDescription = "Powered by Kotlin and Compose",
                          modifier = Modifier.fillMaxWidth(0.9f).aspectRatio(1280f / 160f),
                      )
                      Image(
                          painter = painterResource(
                              if (dark) R.drawable.credit_dark else R.drawable.credit_light,
                          ),
                          contentDescription = "Developed by Lyau_Chisen6173 & DeepSeek V4.1 Flash",
                          modifier = Modifier.fillMaxWidth(0.9f).aspectRatio(1280f / 160f),
                      )
                  }
                  Spacer(Modifier.height(Spacing.xxs))
                  Text(
                      text = stringResource(R.string.about_version_line, BuildConfig.VERSION_NAME, variant),
                      style = MaterialTheme.typography.labelMedium,
                      color = c.textTertiary,
                  )
                  Text(
                      text = stringResource(R.string.about_tagline),
                      style = MaterialTheme.typography.labelSmall,
                      color = c.textTertiary,
                      textAlign = TextAlign.Center,
                  )
              }

            InfoCard(stringResource(R.string.about_version)) {
                InfoRow(stringResource(R.string.about_version_code), BuildConfig.VERSION_NAME)
                InfoRow(stringResource(R.string.about_variant), variant)
                if (LiteFeatures.ENABLED) {
                    Text(
                        text = stringResource(R.string.about_lite_note) + "" +
                            "",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                    )
                }
            }

            InfoCard(stringResource(R.string.about_check_update)) {
                  // 不给说明文字，点了才发请求
                Button(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        result = null
                        updatable = null
                        scope.launch {
                            val latest = runCatching { com.jmnext.data.UpdateFetcher.latest(repo.okHttp) }.getOrNull()
                            val tag = latest?.tag
                            checking = false
                            result = when {
                                tag == null -> ctx.getString(R.string.about_check_failed)
                                UpdateCheck.isNewer(tag, BuildConfig.VERSION_NAME) ->
                                    ctx.getString(R.string.about_check_new, tag).also {
                                        updatable = tag
                                        notes = latest?.notes.orEmpty()
                                    }
                                else -> ctx.getString(R.string.about_check_latest, tag)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (checking) stringResource(R.string.about_checking) else stringResource(R.string.about_check_update)) }

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
                            Text(ctx.getString(R.string.about_download_pkg, asset))
                        }
                        Text(
                            text = ctx.getString(R.string.about_asset_note) + "" +
                                "",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary,
                        )
                        // 这次更新了什么（release 说明摘要，经代理抓取）
                        val brief = com.jmnext.data.UpdateFetcher.brief(notes)
                        if (brief.isNotEmpty()) {
                            Text(
                                text = ctx.getString(R.string.about_update_notes, brief),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                }
            }

            InfoCard(stringResource(R.string.about_desc_title)) {
                Text(
                    text = stringResource(R.string.about_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }

            InfoCard(stringResource(R.string.about_links)) {
                LinkRow(stringResource(R.string.about_link_home)) { uri.openUri("https://github.com/${UpdateCheck.REPO}") }
                LinkRow(stringResource(R.string.about_link_releases)) { uri.openUri("https://github.com/${UpdateCheck.REPO}/releases") }
                LinkRow(stringResource(R.string.about_link_issues)) { uri.openUri("https://github.com/${UpdateCheck.REPO}/issues") }
            }


            InfoCard(stringResource(R.string.about_license_title)) {
                Text(
                    text = stringResource(R.string.about_license),
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
    val c = JmTheme.colors
    // 行式链接（参考 HyperCeiler 的列表项）：文字在左、箭头在右，整行可点，高度 ≥48dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.text,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = MaterialTheme.typography.bodyLarge, color = c.textTertiary)
    }
}



