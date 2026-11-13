package com.jmnext.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface

/**
 * 桌面端主题：直接采用 moyingyilang.github.io 的设计令牌（2.0.0）。
 *
 * 这些值不是"照着感觉调的"，而是从博客的 src/styles/global.css 里逐个抄来的：
 * 强调色、三级文字灰、描边、表面色、圆角尺度、动效时长。这样做的好处是
 * **桌面端与博客观感一致**，而且以后博客改色，这里改同名的几个常量即可。
 *
 * 深色不是浅色的反相，而是博客里另给的一套值（强调色从 #0f6cbd 变为 #60cdff，
 * 因为深底上蓝要更亮才看得清；文字改半透明白）。
 */
private object Tokens {
    // 浅色
    val accentLight = Color(0xFF0F6CBD)
    val accentLightHover = Color(0xFF115EA3)
    val accentLightSoft = Color(0x1A0F6CBD)
    val textLight = Color(0xFF16181D)
    val textSecondaryLight = Color(0xFF4A4F5A)
    val textTertiaryLight = Color(0xFF767C88)
    val surfaceLight = Color(0xFFF6F7FA)
    val surfaceSunkenLight = Color(0x090F172A)
    val surfaceHoverLight = Color(0x0D0F172A)
    val strokeLight = Color(0x170F172A)

    // 深色
    val accentDark = Color(0xFF60CDFF)
    val accentDarkHover = Color(0xFF7FD8FF)
    val accentDarkSoft = Color(0x2460CDFF)
    val textDark = Color(0xFFF3F4F7)
    val textSecondaryDark = Color(0xB8FFFFFF)
    val textTertiaryDark = Color(0x80FFFFFF)
    val surfaceDark = Color(0xFF16181E)
    val surfaceSunkenDark = Color(0x3D000000)
    val surfaceHoverDark = Color(0x14FFFFFF)
    val strokeDark = Color(0x1AFFFFFF)

    // 圆角：博客的 --r-sm/md/lg 分别 8/12/18
    val rSm = 8.dp
    val rMd = 12.dp
    val rLg = 18.dp
}

@Composable
fun BlogTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 深浅色的优先级：用户显式选过 → 用用户的；没选过 → 跟随系统。
    // 之前这里直接用 isSystemInDarkTheme()，于是外观页切深浅色**完全没反应** ——
    // 外观页改的是 Appearance.dark，而主题读的是系统值，两者不通。
    val systemDark = isSystemInDarkTheme()
    val useDark = if (Appearance.userChoseDark) Appearance.dark else systemDark
    val jmSpec = JmSpecs.of(Appearance.style)

    val scheme = if (useDark) {
        darkColorScheme(
            primary = Tokens.accentDark,
            onPrimary = Color(0xFF04263A),
            primaryContainer = Tokens.accentDarkSoft,
            onPrimaryContainer = Tokens.accentDark,
            background = Color.Transparent,   // 透出壁纸
            onBackground = Tokens.textDark,
            surface = Tokens.surfaceDark.copy(alpha = Appearance.effectiveAlpha),
            onSurface = Tokens.textDark,
            surfaceVariant = Tokens.surfaceSunkenDark,
            onSurfaceVariant = Tokens.textSecondaryDark,
            outline = Tokens.strokeDark,
            outlineVariant = Tokens.strokeDark,
        )
    } else {
        lightColorScheme(
            primary = Tokens.accentLight,
            onPrimary = Color.White,
            primaryContainer = Tokens.accentLightSoft,
            onPrimaryContainer = Tokens.accentLight,
            background = Color.Transparent,   // 透出壁纸
            onBackground = Tokens.textLight,
            // 半透明表面：所有用 colorScheme.surface 的面板因此一次性变成玻璃，
            // 不必逐处改背景。透明度由 Appearance.style 决定。
            surface = Tokens.surfaceLight.copy(alpha = Appearance.effectiveAlpha),
            onSurface = Tokens.textLight,
            surfaceVariant = Tokens.surfaceSunkenLight,
            onSurfaceVariant = Tokens.textSecondaryLight,
            outline = Tokens.strokeLight,
            outlineVariant = Tokens.strokeLight,
        )
    }

    // 字号也照博客：正文 1rem(16sp) 上下、辅助文字 0.85/0.78rem
    // 汉字要靠一个**确定含中日韩字形**的字体族，不能只依赖 FontFamily.Default：
    // Windows 的默认字体（Segoe UI）不含汉字，fallback 可能挑到不含汉字的字体（显示成方框），
    // 也可能挑到日文字体 —— 汉字会按日式写法渲染（比如"直""骨"这类字的形态）。两者都属于"汉字显示有毛病"。
    val cjk = cjkFontFamily()
    val typography = Typography(
        titleMedium = MaterialTheme.typography.titleMedium.copy(
            fontFamily = cjk,
            fontSize = jmSpec.type.title,
            fontWeight = FontWeight.SemiBold,
        ),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = cjk, fontSize = jmSpec.type.body, lineHeight = 22.sp),
        bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = cjk, fontSize = jmSpec.type.label, lineHeight = 20.sp),
        labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = cjk, fontSize = jmSpec.type.caption),
        // 其余样式也统一到同一字体族：否则大标题、按钮文字等会退回默认字体，汉字又可能出问题
        displayLarge = MaterialTheme.typography.displayLarge.copy(fontFamily = cjk),
        displayMedium = MaterialTheme.typography.displayMedium.copy(fontFamily = cjk),
        displaySmall = MaterialTheme.typography.displaySmall.copy(fontFamily = cjk),
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = cjk),
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = cjk),
        headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontFamily = cjk),
        // 四档层级与手机端一致：页标题 24 / 区块 17 / 正文 15 / 说明 12.5
        titleLarge = MaterialTheme.typography.titleLarge.copy(
            fontFamily = cjk,
            fontSize = jmSpec.type.display,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 32.sp,
        ),
        titleSmall = MaterialTheme.typography.titleSmall.copy(fontFamily = cjk),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = cjk,
            fontSize = jmSpec.type.subtitle,
            lineHeight = 24.sp,
        ),
        labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = cjk),
        labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = cjk),
    )

    androidx.compose.runtime.CompositionLocalProvider(LocalJmSpec provides jmSpec) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(jmSpec.radius.xs),
                small = RoundedCornerShape(jmSpec.radius.sm),
                medium = RoundedCornerShape(jmSpec.radius.md),
                large = RoundedCornerShape(jmSpec.radius.lg),
            ),
            typography = typography,
        ) {
            // 壁纸铺在最底层，上面所有面板是半透明的，于是形成玻璃观感
            Box(Modifier.fillMaxSize()) {
                WallpaperLayer()
                // 内容整体落在一块玻璃面板上：Android 每一页都是面板，
                // 桌面端之前把文字直接铺在壁纸上，配浅色壁纸时读不清。
                // 留 10dp 边让壁纸从四周透出来，玻璃感才成立。
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                ) {
                    JmSurface(
                        modifier = Modifier.fillMaxSize(),
                        level = 1,
                        fillAlpha = 0.82f,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(jmSpec.radius.xl),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 选一个含中日韩字形的系统字体族。
 *
 * 为什么要显式选：`FontFamily.Default` 在桌面端等于系统默认字体，Windows 上是 Segoe UI（不含汉字），
 * 于是 fallback 要么找不到字形（方框），要么落到日文字体上（汉字按日式写法渲染）。这两者都是"汉字显示有毛病"。
 *
 * 候选按平台给，取到第一个能加载的；都取不到就返回 null，让 Compose 用默认字体（至少不比现在差）。
 * 用 Skiko 的 FontMgr 按**字体名**查系统字体，再用 Compose Desktop 的 Typeface 包装成 FontFamily。
 */
private fun cjkFontFamily(): FontFamily? = runCatching {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val names = when {
        os.contains("win") -> listOf("Microsoft YaHei UI", "Microsoft YaHei", "SimHei", "SimSun")
        os.contains("mac") -> listOf("PingFang SC", "Hiragino Sans GB", "STHeiti", "Heiti SC")
        else -> listOf(
            "Noto Sans CJK SC", "Source Han Sans SC", "Noto Sans SC",
            "WenQuanYi Micro Hei", "Droid Sans Fallback", "DejaVu Sans",
        )
    }
    val mgr = org.jetbrains.skia.FontMgr.default
    val sk = names.firstNotNullOfOrNull { name ->
        runCatching { mgr.matchFamilyStyle(name, org.jetbrains.skia.FontStyle.NORMAL) }.getOrNull()
    } ?: return@runCatching null
    FontFamily(androidx.compose.ui.text.platform.Typeface(sk))
}.getOrNull()
