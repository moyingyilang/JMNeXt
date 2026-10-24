package com.jmnext.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jmnext.ui.LocalWallpaper
import com.jmnext.ui.theme.JmTheme

/**
 * 阅读页的背景：**跟随当前壁纸**（淡化处理），没有壁纸时退回主题纯色。
 *
 * 为什么不再用 [com.jmnext.ui.components.ambientBase] 那套渐变：
 * 用户反馈漫画页四周的渐变看起来像"一层花壁纸"，干扰看画。这里改成
 * 「真实壁纸 + 轻模糊 + 压暗」——既延续了"跟随壁纸"的意图，又不会在页边出现花纹。
 *
 * 两条硬约束（都是踩过坑的）：
 * 1. **必须不透明**：壁纸若从页间透出来，伪长图的接缝会重新可见（1.1.5 修过的问题）。
 *    所以底层永远先铺一层 [JmTheme.colors].surface 实色；
 * 2. **模糊在 API 31 以下自动忽略**（RenderEffect），那时壁纸是清晰的 —— 比整块糊掉好。
 */
@Composable
fun ReaderBackdrop(modifier: Modifier = Modifier) {
    val c = JmTheme.colors
    val wall = LocalWallpaper.current

    Box(modifier.fillMaxSize()) {
        // 底：不透明实色（无壁纸时这就是最终背景；有壁纸时它保证页间接缝不可见）
        Box(Modifier.fillMaxSize().background(c.surface))

        if (wall.showsImage && !wall.url.isNullOrBlank()) {
            AsyncImage(
                model = wall.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(14.dp),
            )
            // 压暗层：把壁纸压到"能认出是壁纸但不抢眼"的程度
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.surface.copy(alpha = (wall.dim + 0.42f).coerceIn(0f, 0.9f))),
            )
        }
    }
}
