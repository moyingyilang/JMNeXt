package com.jmnext.render

/**
 * 漫画渲染模式的颜色矩阵**数学部分**（纯 FloatArray，4x5，值域 0..1）。
 *
 * 为什么放在 shared：Android 与桌面端都要用同一套系数，而 ColorMatrix/ColorFilter 是
 * Compose 的 UI 类型、两个模块各自都有；只有系数是真正的"共享语义"。这样两端不会各写一份、慢慢分叉。
 *
 * 亮度权重取 Rec.601（0.299/0.587/0.114）—— 与人眼感知一致，也是 Android
 * `ColorMatrix.setSaturation(0)` 的同一组常数。
 */
object ComicRenderMath {

    const val LUM_R = 0.299f
    const val LUM_G = 0.587f
    const val LUM_B = 0.114f

    /** 墨水屏对比度与偏移：实机试出来的折中（>1 提对比，<0 压暗部）。 */
    const val INK_CONTRAST = 1.45f
    const val INK_OFFSET = -0.18f

    /** 黑白（灰度）：把任意颜色映射成 R=G=B。 */
    fun grayscaleMatrix(): FloatArray = floatArrayOf(
        LUM_R, LUM_G, LUM_B, 0f, 0f,
        LUM_R, LUM_G, LUM_B, 0f, 0f,
        LUM_R, LUM_G, LUM_B, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** 墨水屏：先灰度，再 `out = a * gray + b`。 */
    fun inkMatrix(): FloatArray {
        val a = INK_CONTRAST
        val b = INK_OFFSET
        return floatArrayOf(
            a * LUM_R, a * LUM_G, a * LUM_B, 0f, b,
            a * LUM_R, a * LUM_G, a * LUM_B, 0f, b,
            a * LUM_R, a * LUM_G, a * LUM_B, 0f, b,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
