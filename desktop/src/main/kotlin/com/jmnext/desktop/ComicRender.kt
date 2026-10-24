package com.jmnext.desktop

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.jmnext.data.prefs.ComicRenderMode
import com.jmnext.render.ComicRenderMath

/**
 * 漫画渲染模式（桌面端）：与 Android 共用 [ComicRenderMath] 的系数，这里只做 Compose 适配。
 *
 * - 彩色：返回 null（零开销）；
 * - 黑白：Rec.601 权重去色；
 * - 墨水屏：去色 + 提高对比度（桌面端同样有用：外接 e-ink 显示器，或只想让画面更"像纸"）。
 */
fun comicRenderFilter(mode: ComicRenderMode): ColorFilter? = when (mode) {
    ComicRenderMode.Color -> null
    ComicRenderMode.Grayscale -> ColorFilter.colorMatrix(ColorMatrix(ComicRenderMath.grayscaleMatrix()))
    ComicRenderMode.Ink -> ColorFilter.colorMatrix(ColorMatrix(ComicRenderMath.inkMatrix()))
}

/**
 * 渲染模式的持久化（与 `ReaderModePref` 同一套写法、同一套存储设施）。
 * 设置页只写，阅读页只读 —— 所以选完下次进阅读页生效。
 */
internal object ComicRenderPref {
    private val prefs = PreferencesKeyValueStore("jm_comic_render")

    var mode: ComicRenderMode
        get() = ComicRenderMode.fromName(prefs.getString("mode", null))
        set(value) { prefs.putString("mode", value.name) }
}
