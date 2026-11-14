package com.jmnext.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 桌面端的表面与按压反馈，参数取自 [LocalJmSpec]。
 *
 * Android 的观感有三层：填充（半透明玻璃）、发丝描边、按下时轻微缩放。
 * 之前桌面端只做了第一层，所以「像但不像」。
 */

/** 按下缩放：读 [SurfaceSpec.pressScale]，风格不同手感不同（MIUI 0.96、Windows 0.985）。 */
@Composable
fun Modifier.jmPressable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val spec = LocalJmSpec.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) spec.surface.pressScale else 1f,
        animationSpec = androidx.compose.animation.core.tween(JmMotion.fast, easing = Motion.Standard),
        label = "jmPress",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
        }
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
}

/**
 * 玻璃/卡片表面：[level] 对应 [SurfaceSpec.shadows] 的三档（Card / Raised / Flyout）。
 *
 * - 填充：`surface` 已经带风格透明度，这里只按 `fillAlphaScale` 再乘一次
 * - 发丝描边：`hairline > 0` 时画，颜色用 outline
 * - 阴影：玻璃不投影（表里是 0），实心风格才有
 */
@Composable
fun JmSurface(
    modifier: Modifier = Modifier,
    level: Int = 0,
    shape: RoundedCornerShape = RoundedCornerShape(Radius.md),
    fillAlpha: Float? = null,
    content: @Composable () -> Unit,
) {
    val spec = LocalJmSpec.current
    val shadow: Dp = spec.surface.shadows.getOrElse(level) { 0.dp }
    val base = MaterialTheme.colorScheme.surface
    val fill = base.copy(
        alpha = (fillAlpha ?: (base.alpha * spec.surface.fillAlphaScale)).coerceIn(0f, 1f),
    )
    var m = modifier.clip(shape).background(fill)
    if (shadow > 0.dp) {
        m = m.shadow(shadow, shape, clip = false)
    }
    if (spec.surface.hairline > 0.dp) {
        m = m.border(BorderStroke(spec.surface.hairline, MaterialTheme.colorScheme.outline), shape)
    }
    Box(m) { content() }
}
