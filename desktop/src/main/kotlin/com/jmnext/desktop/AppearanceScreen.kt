package com.jmnext.desktop
import com.jmnext.data.prefs.ComicRenderMode
import com.jmnext.data.prefs.ReaderMode
import com.jmnext.wallpaper.WallpaperMode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * 外观设置（桌面端，1.9.x）。
 *
 * 为什么先做这个页：观感（风格/壁纸/玻璃浓度）是主观的，让人点着看比"我改一次、
 * 跑一次、你再反馈"快得多。改动即时生效 —— Appearance 里的字段是 mutableState，
 * 界面会立刻重组。
 *
 * 本地图片壁纸用 AWT 的文件选择框（桌面端可用，且不需要额外依赖）。
 */
@Composable
fun AppearanceScreen() {
    var refresh by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("外观", style = MaterialTheme.typography.titleLarge)

        // 风格
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("界面风格", style = MaterialTheme.typography.titleMedium)
            Text(
                "换的不只是透明度 —— 圆角与壁纸是否模糊一起换。手机上的实时背景模糊在桌面上拿不到，这里用等效近似。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassStyle.entries.forEach { s ->
                    val active = Appearance.style == s
                    Button(
                        onClick = { Appearance.style = s; refresh++ },
                        modifier = Modifier.height(38.dp),
                    ) {
                        Text(
                            if (active) "· ${s.label}" else s.label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Text(
                "当前：${Appearance.style.label}（面板透明度 ${(Appearance.style.surfaceAlpha * 100).toInt()}%、圆角 ${Appearance.style.corner}dp" +
                    (if (Appearance.style.blurWallpaper) "、壁纸模糊" else "、壁纸不模糊") + "）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // 可调项：观感很主观，与其我猜，不如给你滑杆
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("壁纸模糊：${Appearance.wallpaperBlur}dp", style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.Slider(
                value = Appearance.wallpaperBlur.toFloat(),
                onValueChange = { Appearance.wallpaperBlur = it.toInt(); refresh++ },
                valueRange = 0f..256f,
                modifier = Modifier.width(420.dp),
            )
            Text("面板浓度：${(Appearance.effectiveAlpha * 100).toInt()}%（在所选风格基础上再调）",
                style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.Slider(
                value = Appearance.alphaScale,
                onValueChange = { Appearance.alphaScale = it; refresh++ },
                valueRange = 0f..1f,
                modifier = Modifier.width(420.dp),
            )
            Text("壁纸压暗：${Appearance.dim}%（0..100，到 100 壁纸等于纯黑）",
                style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.Slider(
                value = Appearance.dim.toFloat(),
                onValueChange = { Appearance.dim = it.toInt() },
                valueRange = 0f..100f,
                modifier = Modifier.width(420.dp),
            )
        }

        // 壁纸
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("壁纸", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallpaperPreset.entries.forEach { p ->
                    Column(
                        Modifier
                            .width(96.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                Appearance.preset = p
                                Appearance.wallpaperPath = null
                                // 选预设渐变时把在线壁纸关掉，否则两张图会叠着打架
                                RemoteWallpaper.setMode(WallpaperMode.Off)
                                refresh++
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(if (Appearance.dark) p.dark else p.light)),
                        ) {}
                        Text(
                            (if (Appearance.preset == p && Appearance.wallpaperPath == null) "· " else "") + p.label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }


            // 在线壁纸（模式/换一张/轮换/自定义地址/署名）：见 WallpaperRemoteSection.kt
            WallpaperRemoteSection(onChanged = { refresh++ })
            // 清除壁纸缓存：电脑端此前没有入口，攒够 CACHE_MAX 后池子会一直轮转同一批地址
            TextButton(onClick = {
                scope.launch {
                    RemoteWallpaper.clearCache()
                    refresh++
                }
            }) { Text("清除壁纸缓存") }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = {
                    val chosen = pickImageFile()
                    if (chosen != null) {
                        Appearance.wallpaperPath = chosen
                        refresh++
                        Log.line("外观", "选择本地壁纸：$chosen")
                    }
                }) { Text("选择本地图片…") }

                if (Appearance.wallpaperPath != null) {
                    Text(
                        File(Appearance.wallpaperPath!!).name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { Appearance.wallpaperPath = null; refresh++ }) { Text("清除") }
                }
            }
            Text(
                "本地图片壁纸这条路径**尚未验证**（当前实现借用了网络图片加载器读 file://，很可能不通）。" +
                    "如果选了没反应，就是这里的问题，我会改成用本地解码。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 深浅色
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("深浅色", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { Appearance.dark = false; refresh++ }) { Text("浅色") }
                TextButton(onClick = { Appearance.dark = true; refresh++ }) { Text("深色") }
            }
            Text(
                if (Appearance.dark) "当前：深色" else "当前：浅色",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 漫画渲染：与 Android 同一套语义（彩色/黑白/墨水屏），系数共用 shared 的 ComicRenderMath。
        // 设置页只写，阅读页读 —— 选完下次进阅读页生效。
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("漫画渲染", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComicRenderMode.entries.forEach { m ->
                    val label = m.label
                    Button(
                        onClick = { ComicRenderPref.mode = m; refresh++ },
                        modifier = Modifier.height(38.dp),
                    ) {
                        Text(
                            if (ComicRenderPref.mode == m) "· $label" else label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Text(
                "只作用于漫画画面，不影响界面配色。墨水屏设备选「墨水屏」：去色并提高对比度。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 阅读默认形态（照 Android 的 AppPrefs.readerMode 与「我的」页的 ReadingCard）。
        // 设置页只写，取值在阅读页：两边共用 ReaderModePref 这一个键，
        // 所以这里选完，下次进阅读页就是所选形态。
        // 连载更新提醒（照 Android 的 AppPrefs.serialNotify）：开关默认关，由用户主动打开。
        // 桌面端只能在程序运行时提醒 —— Android 靠 AlarmManager 可在后台唤醒，桌面端没有等价机制；
        // 系统托盘不可用时改为在窗口内提示（见 SerialReminder）。
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("连载更新提醒", style = MaterialTheme.typography.titleMedium)
            Text(
                "你追的连载有更新时提醒（默认关）。桌面端仅在程序运行时有效；系统托盘不能用时改为在窗口内提示。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true to "开启", false to "关闭").forEach { (on, label) ->
                    Button(
                        onClick = { SerialReminder.enabled = on; refresh++ },
                        modifier = Modifier.height(38.dp),
                    ) {
                        Text(if (SerialReminder.enabled == on) "· $label" else label)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("阅读形态", style = MaterialTheme.typography.titleMedium)
            Text(
                "进入阅读页时的默认形态：纵向连续滚动（与 Android 的默认值一致）或横向逐页。改完下次进阅读页生效。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderMode.entries.forEach { m ->
                    val label = if (m == ReaderMode.Scroll) "纵向滚动" else "横向翻页"
                    Button(
                        onClick = { ReaderModePref.mode = m; refresh++ },
                        modifier = Modifier.height(38.dp),
                    ) {
                        Text(
                            if (ReaderModePref.mode == m) "· $label" else label,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Text(
                "当前：${if (ReaderModePref.mode == ReaderMode.Scroll) "纵向滚动" else "横向翻页"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 用 AWT 的文件选择框挑一张图片（桌面端自带，无需额外依赖）。返回绝对路径。 */
private fun pickImageFile(): String? = runCatching {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "选择壁纸图片", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        val n = name.lowercase()
        n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp")
    }
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null
    File(dir, file).absolutePath
}.getOrNull()
