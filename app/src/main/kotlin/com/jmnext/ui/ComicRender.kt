package com.jmnext.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.jmnext.data.prefs.ComicRenderMode

/**
 * 当前漫画渲染模式。
 *
 * 用 CompositionLocal 而不是层层传参：页图绘制点有三处
 * （`ReaderImage`、`ZoomableReaderImage`、`PagedReader` 的调用），
 * 传参要改五个签名，而读取方只关心"现在是什么模式"。
 *
 * 默认 [ComicRenderMode.Color] —— 未提供的地方（预览、测试）保持原样，不会莫名变色。
 */
val LocalComicRenderMode = compositionLocalOf { ComicRenderMode.Color }

/**
 * 各模式的颜色矩阵（4x5，作用于 0..1 的颜色分量）。
 *
 * - 彩色：返回 null，不做任何额外处理；
 * - 黑白：标准亮度权重去色（0.299/0.587/0.114，与人眼感知一致）；
 * - 墨水屏：先去色，再拉对比度 `out = a * gray + b`（a > 1 提对比，b < 0 压暗部）。
 *   e-ink 的灰阶响应偏弱，低对比内容会糊成一片，所以这一步对墨水屏是必要的。
 */
fun comicRenderMatrix(mode: ComicRenderMode): ColorMatrix? = when (mode) {
    ComicRenderMode.Color -> null
    ComicRenderMode.Grayscale -> ColorMatrix().apply { setToSaturation(0f) }
    ComicRenderMode.Ink -> ColorMatrix(inkMatrix())
}

/** 给 `Image(colorFilter = …)` 用的滤镜；彩色模式返回 null，避免无谓开销。 */
fun comicRenderFilter(mode: ComicRenderMode): ColorFilter? =
    comicRenderMatrix(mode)?.let { ColorFilter.colorMatrix(it) }

/** 亮度权重取自 Rec.601；墨水屏对比度 1.45、偏移 -0.18 是实机试出来的折中值。 */
private const val LUM_R = 0.299f
private const val LUM_G = 0.587f
private const val LUM_B = 0.114f
private const val INK_CONTRAST = 1.45f
private const val INK_OFFSET = -0.18f

private fun inkMatrix(): FloatArray {
    val a = INK_CONTRAST
    val b = INK_OFFSET
    return floatArrayOf(
        a * LUM_R, a * LUM_G, a * LUM_B, 0f, b,
        a * LUM_R, a * LUM_G, a * LUM_B, 0f, b,
        a * LUM_R, a * LUM_G, a * LUM_B, 0f, b,
        0f, 0f, 0f, 1f, 0f,
    )
}
