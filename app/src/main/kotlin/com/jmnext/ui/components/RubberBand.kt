package com.jmnext.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 越界回弹（橡皮筋）。
 *
 * 为什么自己做：`Modifier.overscroll(rememberOverscrollEffect())` 给的是**系统默认**效果
 * （Android 12+ 是平台拉伸、更早是边缘发光），幅度与手感都不受控，也没有“松手弹回”的过冲。
 * 这里用 `nestedScroll` 自己接管「滚动已经到头、但手指还在推」的那部分位移：
 *
 * - 位移量按 `sqrt` 阻尼（越推越沉，不会无限跟手）；
 * - 最大位移受 [maxOffsetPx] 限制；
 * - 松手后用 spring 弹回 0，带轻微过冲 —— 这就是“灵动”的来源。
 *
 * 接入方式：在整个导航内容外面套一层 `Modifier.rubberBand(state)` 即可，
 * 内部列表本身的滚动不受影响（只在它们消费不掉的多余位移上生效）。
 */
@Stable
class RubberBandState internal constructor() {
    /** 当前视觉位移（px，正=向下拉、负=向上推）。 */
    var offset by mutableFloatStateOf(0f)
        internal set

    internal var maxOffsetPx = 180f
    internal var damping = 0.55f
}

@Composable
fun rememberRubberBandState(): RubberBandState {
    val density = LocalDensity.current
    return remember {
        RubberBandState().also { it.maxOffsetPx = with(density) { 120.dp.toPx() } }
    }
}

/**
 * 把橡皮筋接进内容。
 *
 * 注意：**只接管“没人消费”的位移**（`postScroll` 的 leftover），所以列表正常滚动、
 * 到边界后继续推才会出现回弹。
 */
@Composable
fun Modifier.rubberBand(state: RubberBandState): Modifier {
    // lite：越界回弹整段不装（nestedScroll + graphicsLayer 都是每帧成本）
    if (com.jmnext.LiteFeatures.ENABLED) return this
    val scope = rememberCoroutineScope()
    val anim = remember { Animatable(0f) }

    // Animatable 的值回写到 state，供 graphicsLayer 读取
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                if (dy == 0f) return Offset.Zero
                val cur = state.offset
                // 方向不一致时先归零（例如下拉到一半改成上推）
                val sameDirection = cur == 0f || sign(cur) == sign(dy)
                val base = if (sameDirection) cur else 0f
                val next = base + dy * state.damping
                // sqrt 阻尼：越接近上限越推不动
                val limit = state.maxOffsetPx
                val clamped = sign(next) * (limit * kotlin.math.sqrt(abs(next) / limit))
                state.offset = clamped.coerceIn(-limit, limit)
                return Offset(0f, dy) // 声明“这部分我消费了”，避免系统再叠一层
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (state.offset != 0f) {
                    // 松手：spring 弹回 0，dampingRatio < 1 才有过冲
                    anim.snapTo(state.offset)
                    anim.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.55f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { state.offset = value }
                }
                return Velocity.Zero
            }
        }
    }

    return this
        .nestedScroll(connection)
        .graphicsLayer {
            // 整体平移 + 轻微缩放，像整页被拉了一下
            translationY = state.offset
            val scaleBoost = 1f - (abs(state.offset) / (state.maxOffsetPx * 6f))
            scaleX = scaleBoost.coerceIn(0.97f, 1f)
            scaleY = scaleBoost.coerceIn(0.97f, 1f)
        }
}
