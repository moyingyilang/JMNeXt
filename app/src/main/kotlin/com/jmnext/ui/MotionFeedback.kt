package com.jmnext.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.jmnext.ui.theme.rememberJmMotion

/**
 * 按压反馈：按下时轻微缩小，抬起回弹。
 *
 * 为什么是"缩小 3%"而不是"变亮/换色"：卡片在网格里密集排列，颜色变化会被相邻卡片稀释，
 * 而**尺寸变化**在余光里也能被感知 —— 这是"我确实按到了"的即时确认（<100ms 才有触觉感，token 里 press=fast）。
 *
 * 两个刻意的限制：
 * 1. 只在 5% 以内缩放：超过就会像"按钮在躲手指"，反而显得廉价；
 * 2. **不自己建 InteractionSource**：默认用 `remember { MutableInteractionSource() }`，
 *    但调用方可以传入自己的，避免与 `clickable` 的 ripple 抢同一个 source 导致两者都失效。
 *
 * 系统"减少动画"开启时，`rememberJmMotion()` 会把 press 退化成 120ms，
 * 这里的缩放幅度也随之降到 0（见 [com.jmnext.ui.theme.reducedMotion] 的设计约定：
 * 减少动画时不该有位移/缩放，只保留必要的状态变化）。
 */
@Composable
fun rememberJmPressScale(interactionSource: MutableInteractionSource? = null): Float {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    // 不依赖 collectIsPressedAsState（不同 Compose 版本的可见性不同），直接收交互流
    val pressed by produceState(false, source) {
        source.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> value = true
                is PressInteraction.Release, is PressInteraction.Cancel -> value = false
            }
        }
    }
    val motion = rememberJmMotion()
    val spec = motion.press
    // 退化判定：press 被压到 120ms 且曲线回到 standard，说明系统要求减少动画
    val reduce = spec.durationMillis == 120
    val target = if (pressed && !reduce) 0.97f else 1f
    val scale by animateFloatAsState(target, spec.tween(), label = "jmPressScale")
    return scale
}

/** 便捷写法：`Modifier.jmPressScale()`。若不是可点击元素，请自行传入 interactionSource 并驱动它。 */
fun Modifier.jmPressScale(interactionSource: MutableInteractionSource? = null): Modifier = composed {
    val s = rememberJmPressScale(interactionSource)
    this.graphicsLayer {
        scaleX = s
        scaleY = s
    }
}
