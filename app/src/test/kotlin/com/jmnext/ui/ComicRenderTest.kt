package com.jmnext.ui

import androidx.compose.ui.graphics.ColorMatrix
import com.jmnext.data.prefs.ComicRenderMode
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 漫画渲染模式的颜色矩阵单测。
 *
 * 为什么值得测：矩阵是手写的 4x5 常量，错一个数就是"整页偏色"或"墨水屏反而更糊"，
 * 而这种错误在真机上很难一眼看出（图本来就花）。用两个可判定的性质钉住它：
 * 1. 黑白：任何颜色映射后 R=G=B；
 * 2. 墨水屏：暗部更暗、亮部更亮、中灰基本不动（避免整体偏亮）。
 */
class ComicRenderTest {

    private fun apply(matrix: ColorMatrix, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val v = matrix.values
        fun ch(i: Int) = v[i * 5] * r + v[i * 5 + 1] * g + v[i * 5 + 2] * b + v[i * 5 + 3] + v[i * 5 + 4]
        return Triple(ch(0), ch(1), ch(2))
    }

    private fun near(a: Float, b: Float) = abs(a - b) < 1e-4f

    @Test
    fun colorMode_hasNoMatrixNorFilter() {
        assertNull(comicRenderMatrix(ComicRenderMode.Color))
        assertNull(comicRenderFilter(ComicRenderMode.Color))
    }

    @Test
    fun grayscale_mapsAnyColorToEqualChannels() {
        val m = comicRenderMatrix(ComicRenderMode.Grayscale)
        assertNotNull(m)
        val (r, g, b) = apply(m!!, 0.9f, 0.2f, 0.4f)
        assertTrue("R 与 G 应相等：$r / $g", near(r, g))
        assertTrue("G 与 B 应相等：$g / $b", near(g, b))
    }

    @Test
    fun ink_increasesContrastAroundMidGray() {
        val m = comicRenderMatrix(ComicRenderMode.Ink)
        assertNotNull(m)
        val dark = apply(m!!, 0.20f, 0.20f, 0.20f).first
        val mid = apply(m, 0.50f, 0.50f, 0.50f).first
        val light = apply(m, 0.80f, 0.80f, 0.80f).first
        assertTrue("暗部应更暗，实际 $dark", dark < 0.20f)
        assertTrue("亮部应更亮，实际 $light", light > 0.80f)
        assertTrue("中灰应接近原值，实际 $mid", mid in 0.35f..0.65f)
    }
}
