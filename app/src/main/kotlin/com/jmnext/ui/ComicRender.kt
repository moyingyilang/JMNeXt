package com.jmnext.ui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.jmnext.data.prefs.ComicRenderMode
import com.jmnext.render.ComicRenderMath

/**
 * 漫画渲染模式的 Compose 适配层（系数在 [ComicRenderMath]，Android 与桌面端共用）。
 *
 * - 彩色：返回 null，不做任何额外处理（零开销）；
 * - 黑白：Rec.601 权重去色；
 * - 墨水屏：去色 + 拉对比度（系数见 [ComicRenderMath]）。
 */
fun comicRenderMatrix(mode: ComicRenderMode): ColorMatrix? = when (mode) {
    ComicRenderMode.Color -> null
    ComicRenderMode.Grayscale -> ColorMatrix(ComicRenderMath.grayscaleMatrix())
    ComicRenderMode.Ink -> ColorMatrix(ComicRenderMath.inkMatrix())
}

/** 给 `Image(colorFilter = …)` 用的滤镜；彩色模式返回 null，避免无谓开销。 */
fun comicRenderFilter(mode: ComicRenderMode): ColorFilter? =
    comicRenderMatrix(mode)?.let { ColorFilter.colorMatrix(it) }
