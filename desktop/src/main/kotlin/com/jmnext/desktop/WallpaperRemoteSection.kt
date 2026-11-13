package com.jmnext.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.jmnext.desktop.i18n.I18n
import com.jmnext.desktop.i18n.tl
import com.jmnext.wallpaper.WallpaperMode
import kotlinx.coroutines.launch

/**
 * 外观页里的"在线壁纸"一节（模式 / 换一张 / 自动轮换 / 自定义地址 / 署名）。
 *
 * 单独成文件是为了不去动已有的外观页结构 —— 那个页面还管风格、玻璃浓度、模糊与压暗，
 * 一次改太多容易碰坏。
 *
 * 与 Android 端一致的地方：署名必须显示出来（用了别人的图，署名是最低要求）；
 * 取图失败要显示原因，而不是静默什么都不发生。
 */
@Composable
fun WallpaperRemoteSection(onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = RemoteWallpaper.state
    var customDraft by remember(state.mode) { mutableStateOf(state.customUrl) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tl("在线壁纸"), style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallpaperMode.entries.forEach { m ->
                TextButton(onClick = {
                    RemoteWallpaper.setMode(m)
                    // 选了在线来源就不要再叠本地图片，否则两张图会打架
                    Appearance.wallpaperPath = null
                    onChanged()
                    scope.launch { RemoteWallpaper.next(force = true) }
                }) {
                    Text((if (state.mode == m) "· " else "") + m.label)
                }
            }
        }
        Text(
            state.mode.desc,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = {
                scope.launch { RemoteWallpaper.next(force = true) }
                onChanged()
            }) { Text(tl("换一张")) }

            if (state.loading) {
                Text(tl("取图中…"), style = MaterialTheme.typography.labelSmall)
            }
            val credit = state.credit
            if (!credit.isNullOrBlank()) {
                Text(I18n.trf("署名：%1\$s", credit), style = MaterialTheme.typography.labelSmall)
            }
        }

        val err = state.error
        if (!err.isNullOrBlank()) {
            Text(
                I18n.trf("取图失败：%1\$s", err),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(tl("自动轮换："), style = MaterialTheme.typography.labelMedium)
            listOf(0, 15, 30, 60).forEach { minutes ->
                TextButton(onClick = {
                    RemoteWallpaper.setInterval(minutes)
                    onChanged()
                }) {
                    Text((if (state.intervalMinutes == minutes) "· " else "") + if (minutes == 0) tl("只手动") else I18n.trf("%1\$d分", minutes))
                }
            }
        }

        if (state.mode == WallpaperMode.Custom) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = customDraft,
                    onValueChange = { customDraft = it },
                    label = { Text(tl("图片直链")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.75f),
                )
                Button(onClick = {
                    RemoteWallpaper.setCustomUrl(customDraft)
                    onChanged()
                    scope.launch { RemoteWallpaper.next(force = true) }
                }) { Text(tl("应用")) }
            }
        }

        Text(
            tl(
                "在线壁纸默认关闭：阅读器默认不发任何第三方请求。取到的地址会在本地缓存轮换，" +
                    "攒够几张后就不再打接口；Bing 每天只重取一次。",
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
