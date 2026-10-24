package com.jmnext.desktop

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动效 token：时长、缓动、弹簧集中在这里。
 *
 * 为什么要有这个文件：此前项目里的动画数值散落在各处（有的 tween、有的默认、多数干脆没有），
 * 结果是"手感不统一"——看起来不是没动效，而是每处都不一样，整体就显僵。
 * 集中之后，改手感只改这里，也不必在每个新界面里重新决定一次用多长。
 *
 * 取值依据朴素：短到不拖沓（滚动/点击 150ms 上下）、长到看得清（页面切换 220ms 上下），
 * 需要"有重量感"的地方用弹簧而不是线性。
 */
object Motion {
    /** 点击、悬停等即时反馈：要"跟手"，所以最短。 */
    const val QUICK_MS = 120
    /** 常规过渡：淡入、位移、展开收起。 */
    const val NORMAL_MS = 220
    /** 需要看清过程的过渡：整页切换。 */
    const val PAGE_MS = 280
    /** 图片淡入：略慢，避免闪烁感。 */
    const val IMAGE_MS = 260

    /** 标准缓动：起步快、收尾稳（Material 常用的那条）。 */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** 进场：稍慢起步。 */
    val Enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** 退场：快速离场。 */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 需要轻微回弹的地方（缩放、抬手）用这个。 */
    fun <T> gentleSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 即时反馈用的 tween（配 QUICK_MS）。 */
    fun <T> quick() = tween<T>(durationMillis = QUICK_MS, easing = Standard)
    /** 常规过渡用的 tween（配 NORMAL_MS）。 */
    fun <T> normal() = tween<T>(durationMillis = NORMAL_MS, easing = Standard)
}

/**
 * 交互级 token：与 Android 的 `JmMotionTable` **同一组比例**。
 *
 * 为什么要在 QUICK/NORMAL/PAGE 之上再加一层：Android 端已经把"列表进入 / 移除 / 前进 / 返回 /
 * 底部栏 / 对话框 / 工具栏"这些**交互**固定了相对关系（退出比进入快 0.72–0.87 倍），
 * 桌面端如果只按 NORMAL/PAGE 各自取值，两端手感会慢慢分叉 —— 同一款产品在两个平台上
 * 节奏不一致，用户是会察觉的（"电脑上好像更黏"）。
 *
 * 这里的数字**不是随便取的**，是从 [Motion.QUICK_MS] / [Motion.NORMAL_MS] / [Motion.PAGE_MS]
 * 派生出来的，比例与 `docs/MOTION-EXPRESSIVE.md` 第二节的表格一致。
 */
data class MotionToken(val millis: Int, val easing: Easing) {
    fun <T> spec() = tween<T>(durationMillis = millis, easing = easing)
}

object MotionTokens {
    /** 按压反馈：最短，才有"跟手"感。 */
    val press = MotionToken(Motion.QUICK_MS, Motion.Standard)

    /** 列表进入 / 移除：移除必须更快，否则显拖沓。 */
    val listEnter = MotionToken(Motion.NORMAL_MS, Motion.Enter)
    val listExit = MotionToken((Motion.NORMAL_MS * 0.72f).toInt(), Motion.Exit)

    /** 页面切换：返回是"撤销"，比前进快。 */
    val navForward = MotionToken(Motion.PAGE_MS, Motion.Enter)
    val navBack = MotionToken((Motion.PAGE_MS * 0.87f).toInt(), Motion.Exit)

    /** 共享元素：距离大，用 Standard（= emphasized）在开头给足速度。 */
    val hero = MotionToken(Motion.PAGE_MS, Motion.Standard)

    /** 底部栏：隐去比显出快。 */
    val bottomBarIn = MotionToken(Motion.NORMAL_MS, Motion.Enter)
    val bottomBarOut = MotionToken((Motion.NORMAL_MS * 0.83f).toInt(), Motion.Exit)

    /** 对话框：聚焦，不是飞入。 */
    val dialogIn = MotionToken((Motion.NORMAL_MS * 0.9f).toInt(), Motion.Enter)
    val dialogOut = MotionToken((Motion.NORMAL_MS * 0.75f).toInt(), Motion.Exit)

    /** 图片淡入：沿用 [Motion.IMAGE_MS]，略慢以避免闪烁。 */
    val image = MotionToken(Motion.IMAGE_MS, Motion.Standard)

    /** 全部 token（供自检/审计遍历）。 */
    val all = listOf(press, listEnter, listExit, navForward, navBack, hero, bottomBarIn, bottomBarOut, dialogIn, dialogOut, image)
}
