package com.jmnext.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 真·玻璃（haze 2.1）。
 *
 * 与旧 [GlassSurface] 的根本区别：玻璃必须**实时采样它背后已绘制的内容**再模糊，
 * 而不是把壁纸整屏模糊一次、再贴半透明色块。旧做法下，顶栏/底栏下面滚动时玻璃里的画面是死的。
 *
 * 用法（两处必须配对）：
 * - 内容容器挂 [Modifier.glassSource]：告诉 haze「这部分像素可以被采样」；
 * - 玻璃表面挂 [Modifier.glassSurface]：实时采样 + 模糊 + 着色。
 *
 * 参数取自 haze 2.1 的 Blur Style 真实 API（blurRadius / noiseFactor / tint / backgroundColor），
 * 不是猜的。
 */
@Composable
fun rememberGlassState(): HazeState = rememberHazeState()

/** 标记可被采样内容。挂在页面内容容器上（通常与玻璃表面同级、更靠内的层）。 */
fun Modifier.glassSource(state: HazeState): Modifier = hazeSource(state)

/**
 * 玻璃表面。
 *
 * @param blurRadius 模糊半径：12–24dp 是玻璃常见区间，再大只是更糊。
 * @param tint 薄色：深色模式用白 6–10%，浅色模式用白 30–40% 或黑 4%。
 * @param noise 颗粒：0.04–0.08，负责让表面有“材质”而不是一片纯色。
 */
fun Modifier.glassSurface(
    state: HazeState,
    shape: Shape = RoundedCornerShape(16.dp),
    blurRadius: Dp = 20.dp,
    tint: Color = Color.White.copy(alpha = 0.08f),
    noise: Float = 0.06f,
): Modifier = this
    .clip(shape)
    .hazeBlur(
        input = HazeInput.Sources(state),
        style = HazeBlurStyle {
            blurRadius(blurRadius)
            // vibrancy()：KernelSU 用 colorControls(saturation = 1.5f) 提鲜，
            // 模糊会把颜色平均掉，不提饱和就是灰蒙蒙一片。
            colorEffects(
                listOf(
                    dev.chrisbanes.haze.blur.HazeColorEffect.colorFilter(
                        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(1.5f) },
                        ),
                    ),
                ),
            )
            noiseFactor(noise)
        },
    )
    // 薄色画在模糊层之上、内容之下：haze 2.1 的 tint 在 colorEffects 子作用域里，
    // 与其嵌套一层，不如直接用背景色叠 —— 效果一样，代码更直白。
    .background(tint)

/** 便捷容器：一块玻璃 + 内容。 */
@Composable
fun GlassBox(
    state: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.glassSurface(state, shape = shape, tint = tint), content = content)
}

/** 全局玻璃状态。由导航根提供，顶栏/底栏等表面读取；没有时退回旧的 [GlassSurface]。 */
object GlassHost {
    /**
     * 当前可用的玻璃状态。由导航根在组合时写入；为空时各玻璃表面退回半透明填充。
     *
     * **必须是 Compose 状态**：`GlassSurface` 在组合期读它。普通 `var` 读不到变化，
     * 首帧拿到 null 之后即使后面写入了值也不会重组 —— 那样玻璃永远走退回分支，
     * 表现就是“看着像半透明贴片，根本没模糊”。
     */
    private val holder = androidx.compose.runtime.mutableStateOf<HazeState?>(null)

    var state: HazeState?
        get() = holder.value
        set(value) { holder.value = value }
}

/**
 * 玻璃质量档位。
 *
 * 为什么需要这个开关：真实 backdrop blur 是**每帧重新采样并模糊背后的像素**，
 * 比原来的半透明填充贵得多。低端机或大列表滚动时可能掉帧，
 * 所以给用户三档：高（好看）/ 中（默认，平衡）/ 关（退回填充，零采样）。
 *
 * [blurRadius] 为 null 表示这一档**不做** backdrop 采样。
 */
enum class GlassQuality(
    val label: String,
    val desc: String,
    val blurRadius: Dp?,
) {
    High("高", "真实模糊，半径 12dp —— 最好看，低端机可能掉帧", 12.dp),
    Medium("中", "真实模糊，半径 8dp —— 默认，观感与性能折中", 8.dp),
    Off("关", "不做背景采样，退回半透明填充 —— 最省电，适合低端机", null);

    val usesBackdrop: Boolean get() = blurRadius != null
}
