package com.jmnext.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * 一次运动的最小描述：**时长 + 曲线**，外加两个用于自检的元数据。
 *
 * 为什么要有这一层：项目里原来散着 9 处 `tween(...)` 与 5 处 `spring(...)`，同一个动作
 * （比如"列表项出现"）在不同页面用了不同时长，用户能感觉到"这里和那里不一样快"却说不出哪里不对。
 * 现在所有动效都从这里取，参数集中、可被单测约束。
 *
 * 设计契约见 `docs/MOTION-EXPRESSIVE.md`；三条硬约束由 `JmMotionTest` 保证：
 * 时长落在 90..600ms、**退出不得慢于进入**、除加载态外不得用线性曲线。
 *
 * @param isEnter true = 进入/出现；false = 退出/消失
 * @param pair 与哪一段配对（成对校验"退出 ≤ 进入"）
 */
data class JmMotionSpec(
    val name: String,
    val durationMillis: Int,
    val easing: Easing,
    val isEnter: Boolean,
    val pair: String? = null,
) {
    /** 转成 Compose 的 [TweenSpec]；需要弹簧时另说（本项目当前不用弹簧，见文档说明）。 */
    fun <T> tween(): TweenSpec<T> = tween(durationMillis = durationMillis, easing = easing)
}

/**
 * 全项目唯一的动效参数表。
 *
 * 数值来源与理由见 `docs/MOTION-EXPRESSIVE.md` 第二节；这里只强调两条最容易做错的：
 * 1. **退出比进入快**（160 vs 220、260 vs 300、150 vs 200）—— 退出是"撤销"，用户已知要去哪；
 * 2. **加载态是唯一允许线性**的地方 —— 线性 = 进度未知，这反而是正确语义。
 */
object JmMotion {

    /** 按压反馈：小于 100ms 才有"触觉"感，慢了像卡了一下。 */
    val press = JmMotionSpec("press", 90, JmEasing.standard, isEnter = true)

    /** 列表项进入：内容"长出来"，不是"跳出来"。 */
    val listEnter = JmMotionSpec("listEnter", 220, JmEasing.fluent, isEnter = true)
    val listExit = JmMotionSpec("listExit", 160, JmEasing.inCubic, isEnter = false, pair = "listEnter")

    /** 页面转场：前进从右推入；返回是撤销，因此更快。 */
    val navForward = JmMotionSpec("navForward", 300, JmEasing.decel, isEnter = true)
    val navBack = JmMotionSpec("navBack", 260, JmEasing.outCubic, isEnter = false, pair = "navForward")

    /** 共享元素（hero）：距离大，需要"强调"曲线在开头给足速度。 */
    val hero = JmMotionSpec("hero", 320, JmEasing.emphasized, isEnter = true)

    /** 底部栏：先给容器、再给内容（内容错开 40ms，由调用方用 delay 实现）。 */
    val bottomBarIn = JmMotionSpec("bottomBarIn", 240, JmEasing.decel, isEnter = true)
    val bottomBarOut = JmMotionSpec("bottomBarOut", 200, JmEasing.inCubic, isEnter = false, pair = "bottomBarIn")

    /** 对话框：聚焦，不是"飞入"。 */
    val dialogIn = JmMotionSpec("dialogIn", 200, JmEasing.decel, isEnter = true)
    val dialogOut = JmMotionSpec("dialogOut", 150, JmEasing.inCubic, isEnter = false, pair = "dialogIn")

    /** 阅读器工具栏：沉浸阅读时必须有让位感，所以隐去比显出更快。 */
    val readerBarsIn = JmMotionSpec("readerBarsIn", 180, JmEasing.standard, isEnter = true)
    val readerBarsOut = JmMotionSpec("readerBarsOut", 150, JmEasing.standard, isEnter = false, pair = "readerBarsIn")

    /** 骨架屏扫光：唯一允许线性的地方。 */
    val loading = JmMotionSpec("loading", 1200, LinearEasing, isEnter = true)

    /** 错误抖动：只在"有输入被拒"时用，且必须衰减。 */
    val errorShake = JmMotionSpec("errorShake", 200, JmEasing.outCubic, isEnter = true)

    /** 全部 token（供自检遍历，新增时不要漏登记）。 */
    val all: List<JmMotionSpec> = listOf(
        press, listEnter, listExit, navForward, navBack, hero,
        bottomBarIn, bottomBarOut, dialogIn, dialogOut,
        readerBarsIn, readerBarsOut, loading, errorShake,
    )

    /**
     * 系统"减少动画"开启时的退化：统一 120ms 淡入淡出，不用任何位移曲线。
     *
     * 为什么不是"直接不动画"：完全无动画会让页面切换失去因果线索（用户会以为没点上），
     * 极短的交叉淡入既尊重了无障碍设置，又保留了"发生了变化"的反馈。
     */
    fun reduceMotion(spec: JmMotionSpec): JmMotionSpec =
        JmMotionSpec("reduce(${spec.name})", 120, JmEasing.standard, spec.isEnter, spec.pair)
}
