package com.jmnext.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 桌面端的统一点击、悬停与**焦点**反馈。
 *
 * 为什么要它：桌面端此前几乎没有任何交互反馈（全项目只有一处 indication）。鼠标移上去、
 * 按下去都没有变化；更糟的是焦点也看不见 —— 这一点是本文件自己造成的：为了去掉涟漪传了
 * `indication = null`，同时也把默认的焦点指示去掉了，于是 Tab 走到封面或按钮上看不出在哪。
 *
 * 行为（数值都走 [Motion]）：
 *  - 悬停：轻微放大 + 一层淡背景；
 *  - 按下：回缩一点；
 *  - **键盘焦点：描一圈边框**，且不改变尺寸（避免布局跳动）。
 * Android 侧不需要这套：Material3 的 `clickable` 自带涟漪与焦点处理。
 */
@Composable
fun Modifier.jmClickable(
    enabled: Boolean = true,
    hoverScale: Float = 1.015f,
    pressScale: Float = 0.985f,
    focusRing: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1f
            pressed -> pressScale
            hovered -> hoverScale
            else -> 1f
        },
        animationSpec = tween(durationMillis = Motion.QUICK_MS, easing = Motion.Standard),
        label = "hoverScale",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (focused && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.QUICK_MS, easing = Motion.Standard),
        label = "focusRing",
    )
    var m = this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(Radius.lg))
        .background(if (hovered && enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.Transparent)
        .hoverable(interactionSource = interaction, enabled = enabled)
        .onFocusChanged { focused = it.isFocused }
    if (focusRing) {
        m = m.border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f * ringAlpha),
            shape = RoundedCornerShape(Radius.lg),
        )
    }
    return m.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}
