package com.jmnext.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmnext.data.prefs.ThemeMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * 主题模式图标：**自己画的**，不用 emoji。
 *
 * - 浅色 → 小太阳（圆 + 八道光芒）；
 * - 深色 → 小月亮（两个圆做差集，留出弯月）；
 * - 跟随系统 → 滴管（斜向管身 + 尖端 + 末端环）。
 *
 * 用 Canvas 直接画路径而不是内置 Material 图标：内置里没有“滴管”，
 * 而太阳/月亮的内置版本线条粗细与字号不匹配（放大后像贴纸）。这里统一线宽，
 * 并且按 [size] 等比缩放，跟文字基线对齐。
 */
@Composable
fun ThemeModeIcon(
    mode: ThemeMode,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    strokeWidth: Dp = 1.6.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val r = minOf(w, h) / 2f
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)

        when (mode) {
            ThemeMode.Light -> {
                // 太阳：内圆 + 八道短光线（按 45° 分布）
                val sunR = r * 0.45f
                drawCircle(color = tint, radius = sunR, center = c, style = stroke)
                repeat(8) { i ->
                    val a = Math.toRadians((i * 45).toDouble())
                    val dx = cos(a).toFloat()
                    val dy = sin(a).toFloat()
                    drawLine(
                        color = tint,
                        start = Offset(c.x + dx * r * 0.72f, c.y + dy * r * 0.72f),
                        end = Offset(c.x + dx * r * 0.98f, c.y + dy * r * 0.98f),
                        strokeWidth = strokeWidth.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            ThemeMode.Dark -> {
                // 月亮：大圆减去偏右上的小圆，得到弯月
                val bigR = r * 0.92f
                val big = Path().apply {
                    addOval(Rect(center = c, radius = bigR))
                }
                val cut = Path().apply {
                    addOval(
                        Rect(
                            center = Offset(c.x + bigR * 0.42f, c.y - bigR * 0.32f),
                            radius = bigR * 0.86f,
                        ),
                    )
                }
                val moon = Path().apply {
                    op(big, cut, PathOperation.Difference)
                }
                drawPath(path = moon, color = tint)
            }

            ThemeMode.System -> {
                // 滴管：45° 斜管 + 尖端三角 + 尾部横环，用几个线段/路径拼出来
                val d = Offset(-0.55f * w, 0.55f * h)   // 左下尖端方向
                val tip = Offset(w * 0.5f + d.x * 0.18f, h * 0.5f + d.y * 0.18f)
                val bodyStart = Offset(w * 0.50f, h * 0.50f)
                val bodyEnd = Offset(w * 0.80f, h * 0.20f)

                // 管身（粗线）
                drawLine(
                    color = tint,
                    start = bodyStart,
                    end = bodyEnd,
                    strokeWidth = strokeWidth.toPx() * 2.2f,
                    cap = StrokeCap.Round,
                )
                // 尖端（细线延伸到左下）
                drawLine(
                    color = tint,
                    start = bodyStart,
                    end = tip,
                    strokeWidth = strokeWidth.toPx(),
                    cap = StrokeCap.Round,
                )
                // 尾部横环（垂直于管身）
                val perp = Offset(-(bodyEnd.y - bodyStart.y), bodyEnd.x - bodyStart.x)
                val pl = kotlin.math.hypot(perp.x, perp.y).coerceAtLeast(1f)
                val ux = perp.x / pl
                val uy = perp.y / pl
                drawLine(
                    color = tint,
                    start = Offset(bodyEnd.x - ux * w * 0.13f, bodyEnd.y - uy * h * 0.13f),
                    end = Offset(bodyEnd.x + ux * w * 0.13f, bodyEnd.y + uy * h * 0.13f),
                    strokeWidth = strokeWidth.toPx(),
                    cap = StrokeCap.Round,
                )
                // 尾部小方块（抓握处）
                drawRect(
                    color = tint,
                    topLeft = Offset(bodyEnd.x - w * 0.06f, bodyEnd.y - h * 0.06f),
                    size = Size(w * 0.12f, h * 0.12f),
                    style = Stroke(width = strokeWidth.toPx()),
                )
            }
        }
    }
}
