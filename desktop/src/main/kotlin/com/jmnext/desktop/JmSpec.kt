package com.jmnext.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 桌面端的设计令牌，数值与 Android 的 `ui/theme/ThemeStyle.kt` 一一对应。
 *
 * 之前桌面端把圆角与字号写死在几处，切风格时只有面板透明度在动，
 * 观感上就成了「风格切了但没切」。这里把 Android 的圆角阶梯、字号层级、
 * 表面参数搬过来，由 [LocalJmSpec] 随风格下发。
 *
 * 说明：桌面没有 Android 的实时背景模糊，[SurfaceSpec.backdropSaturate] 与
 * [SurfaceSpec.noise] 只能近似；[SurfaceSpec.pressScale] 在桌面按下态生效。
 */

    /** Wallpaper scrim: same as Android's wallpaperScrim. */
data class RadiusScale(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
)

/** 字号层级，随风格变化。 */
data class TypeScale(
    val display: TextUnit,
    val title: TextUnit,
    val subtitle: TextUnit,
    val body: TextUnit,
    val label: TextUnit,
    val caption: TextUnit,
    val titleWeight: FontWeight,
    val subtitleWeight: FontWeight,
    val bodyWeight: FontWeight,
    val lineHeightFactor: Float,
)

/**
 * 动效时长，数值与 Android 每种风格的 `MotionSpec` 对齐。
 *
 * Android 的动效**随风格变化**：Miuix 慢而弹、Material 快而直。
 * 桌面端原先是一组全局常量，切风格时手感不变，属于遗漏。
 */
data class MotionSpec(
    val fast: Int,
    val base: Int,
    val slow: Int,
    val springy: Boolean,
)

/** 表面材质参数。 */
data class SurfaceSpec(
    val fillAlphaScale: Float,
    val accentTint: Float,
    val hairline: Dp,
    val innerHighlight: Boolean,
    val backdropSaturate: Float,
    val noise: Float,
    val shadows: List<Dp>,
    val pressScale: Float,
)

data class JmSpec(
    /** Wallpaper scrim: same as Android's wallpaperScrim. */
    val wallpaperScrim: Float,
    val radius: RadiusScale,
    val surface: SurfaceSpec,
    val type: TypeScale,
    val motion: MotionSpec,
)

object JmSpecs {

    /** Windows 11：窗口 8、控件 4；Acrylic 有描边与高光。 */
    val windowGlass = JmSpec(
        wallpaperScrim = 0.34f,
        radius = RadiusScale(xs = 2.dp, sm = 4.dp, md = 6.dp, lg = 8.dp, xl = 12.dp),
        surface = SurfaceSpec(
            fillAlphaScale = 1f,
            accentTint = 0f,
            hairline = 1.dp,
            innerHighlight = true,
            backdropSaturate = 1.65f,
            noise = 0.5f,
            shadows = listOf(0.dp, 0.dp, 0.dp),
            pressScale = 0.985f,
        ),
        type = standardType(),
        motion = MotionSpec(fast = 120, base = 200, slow = 320, springy = false),
    )

    /** 半透明：与 WindowGlass 同一套几何，差别在填充更透、铺一点强调色。 */
    val translucent = windowGlass.copy(
        surface = windowGlass.surface.copy(
            fillAlphaScale = 0.78f,
            accentTint = 0.12f,
            hairline = 1.dp,
        ),
    )

    /** 扁平模糊：平面化，圆角更大，不画高光。 */
    val flatBlur = JmSpec(
        wallpaperScrim = 0.22f,
        radius = RadiusScale(xs = 8.dp, sm = 12.dp, md = 16.dp, lg = 20.dp, xl = 28.dp),
        surface = SurfaceSpec(
            fillAlphaScale = 1f,
            accentTint = 0f,
            hairline = 0.dp,
            innerHighlight = false,
            backdropSaturate = 1f,
            noise = 0f,
            shadows = listOf(0.dp, 0.dp, 0.dp),
            pressScale = 0.99f,
        ),
        type = flatBlurType(),
        motion = MotionSpec(fast = 120, base = 220, slow = 320, springy = false),
    )

    /** MIUI / HyperOS：大圆角、实心卡片、1dp 描边分层、按下有弹性。 */
    val miuix = JmSpec(
        wallpaperScrim = 0.12f,
        radius = RadiusScale(xs = 4.dp, sm = 8.dp, md = 12.dp, lg = 16.dp, xl = 24.dp),
        surface = SurfaceSpec(
            fillAlphaScale = 1f,
            accentTint = 0f,
            hairline = 1.dp,
            innerHighlight = false,
            backdropSaturate = 1f,
            noise = 0f,
            shadows = listOf(0.dp, 0.dp, 0.dp),
            pressScale = 0.97f,
        ),
        type = miuixType(),
        motion = MotionSpec(fast = 150, base = 240, slow = 360, springy = true),
    )

    /** Material 3：M3 的 typescale 与 elevation。 */
    val material = JmSpec(
        wallpaperScrim = 0f,
        radius = RadiusScale(xs = 4.dp, sm = 8.dp, md = 12.dp, lg = 16.dp, xl = 28.dp),
        surface = SurfaceSpec(
            fillAlphaScale = 1f,
            accentTint = 0f,
            hairline = 0.dp,
            innerHighlight = false,
            backdropSaturate = 1f,
            noise = 0f,
            shadows = listOf(0.dp, 1.dp, 6.dp),
            pressScale = 0.98f,
        ),
        type = TypeScale(
            display = 28.sp,
            title = 22.sp,
            subtitle = 16.sp,
            body = 16.sp,
            label = 14.sp,
            caption = 12.sp,
            titleWeight = FontWeight.Medium,
            subtitleWeight = FontWeight.Medium,
            bodyWeight = FontWeight.Normal,
            lineHeightFactor = 1.50f,
        ),
        motion = MotionSpec(fast = 100, base = 200, slow = 300, springy = false),
    )

    /** HyperOS / Miuix：对齐 miuix-kmp 的 TextStyles（32 / 24 / 20 / 17 / 14 / 11，行高 1.45）。 */
    private fun miuixType() = TypeScale(
        display = 32.sp,
        title = 24.sp,
        subtitle = 20.sp,
        body = 17.sp,
        label = 14.sp,
        caption = 11.sp,
        titleWeight = FontWeight.Bold,
        subtitleWeight = FontWeight.SemiBold,
        bodyWeight = FontWeight.Medium,
        lineHeightFactor = 1.45f,
    )

    /** 平面化 + 模糊：标题 21、正文 15、行高 1.60，与 Android 的 flatBlur 一致。 */
    private fun flatBlurType() = TypeScale(
        display = 28.sp,
        title = 21.sp,
        subtitle = 16.sp,
        body = 15.sp,
        label = 13.sp,
        caption = 11.5.sp,
        titleWeight = FontWeight.SemiBold,
        subtitleWeight = FontWeight.Medium,
        bodyWeight = FontWeight.Normal,
        lineHeightFactor = 1.60f,
    )

    private fun standardType() = TypeScale(
        display = 28.sp,
        title = 20.sp,
        subtitle = 16.sp,
        body = 15.5.sp,
        label = 13.sp,
        caption = 11.5.sp,
        titleWeight = FontWeight.SemiBold,
        subtitleWeight = FontWeight.Medium,
        bodyWeight = FontWeight.Normal,
        lineHeightFactor = 1.72f,
    )

    fun of(style: GlassStyle): JmSpec = when (style) {
        GlassStyle.WindowGlass -> windowGlass
        GlassStyle.Translucent -> translucent
        GlassStyle.FlatBlur -> flatBlur
        GlassStyle.Miuix -> miuix
        GlassStyle.Material -> material
    }
}

val LocalJmSpec = staticCompositionLocalOf { JmSpecs.windowGlass }

/** 圆角令牌，用法同 Android：`Radius.lg`。 */
object Radius {
    private val s: RadiusScale
        @Composable @ReadOnlyComposable get() = LocalJmSpec.current.radius

    val xs: Dp @Composable @ReadOnlyComposable get() = s.xs
    val sm: Dp @Composable @ReadOnlyComposable get() = s.sm
    val md: Dp @Composable @ReadOnlyComposable get() = s.md
    val lg: Dp @Composable @ReadOnlyComposable get() = s.lg
    val xl: Dp @Composable @ReadOnlyComposable get() = s.xl
    val pill: Dp = 999.dp
}

/** 动效时长令牌，随风格变化；与 Android 的 MotionSpec 同步。 */
object JmMotion {
    private val s: MotionSpec
        @Composable @ReadOnlyComposable get() = LocalJmSpec.current.motion

    /** 即时反馈：悬停、按压。 */
    val fast: Int @Composable @ReadOnlyComposable get() = s.fast
    /** 常规过渡：淡入、位移、展开。 */
    val base: Int @Composable @ReadOnlyComposable get() = s.base
    /** 整页切换。 */
    val slow: Int @Composable @ReadOnlyComposable get() = s.slow
    /** 该风格是否用弹性曲线。 */
    val springy: Boolean @Composable @ReadOnlyComposable get() = s.springy
}
/** 间距令牌：4dp 基准栅格，与 Android 的 Spacing 相同。 */
object Spacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}
