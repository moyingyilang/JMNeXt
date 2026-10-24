package com.jmnext.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * 一次运动的最小描述：**时长 + 曲线**，外加两个用于自检的元数据。
 *
 * 为什么要这一层：项目里原来散着 9 处 `tween(...)` 与 5 处 `spring(...)`，
 * 同一个动作（如"列表项出现"）在不同页面用了不同时长，用户能感到"这里和那里不一样快"却说不出哪不对。
 *
 * 注意本表**不是写死的常数**：它由当前风格的 [MotionSpec] 派生（见 [JmMotionTable]）——
 * Material / Miuix / HyperOS / Plasma 各有自己的 fast/base/slow 与进出曲线，
 * 这是风格识别度的一部分，不能被一套全局数值抹平。
 *
 * 三条硬约束由 `JmMotionTest` 对**每一套风格**分别验证：
 * 时长落在 90..600ms、退出不得慢于进入、除加载态外不得用线性曲线。
 */
data class JmMotionSpec(
    val name: String,
    val durationMillis: Int,
    val easing: Easing,
    val isEnter: Boolean,
    val pair: String? = null,
) {
    fun <T> tween(): TweenSpec<T> = tween(durationMillis = durationMillis, easing = easing)
}

/**
 * 由某个风格的 [MotionSpec] 派生出全部交互的 token。
 *
 * 映射关系（比例是按"感知时长"定的，不是随手取整）：
 *
 * | 交互 | 时长取自 |
 * | --- | --- |
 * | 按压反馈 | `fast`（<100ms 才有触觉感） |
 * | 列表进入 / 移除 | `base` / `base×0.72`（退出更快） |
 * | 前进 / 返回转场 | `slow` / `slow×0.87` |
 * | 共享元素 hero | `slow`（距离大，用强调曲线） |
 * | 底部栏 / 对话框 | `base` 与其更快值 |
 * | 阅读器工具栏 | `fast×2` / `fast×1.67` |
 *
 * 加载态与错误抖动是**体验常量**（与风格无关），因为它们的语义是"进度未知"与"输入被拒"。
 */
class JmMotionTable(private val m: MotionSpec) {

    private fun spec(name: String, ms: Int, easing: Easing, isEnter: Boolean, pair: String? = null) =
        JmMotionSpec(name, ms.coerceIn(90, 600), easing, isEnter, pair)

    val press get() = spec("press", m.fast, m.enter, isEnter = true)

    val listEnter get() = spec("listEnter", m.base, m.enter, isEnter = true)
    val listExit get() = spec("listExit", (m.base * 0.72f).toInt(), m.exit, isEnter = false, pair = "listEnter")

    val navForward get() = spec("navForward", m.slow, m.enter, isEnter = true)
    val navBack get() = spec("navBack", (m.slow * 0.87f).toInt(), m.exit, isEnter = false, pair = "navForward")

    val hero get() = spec("hero", m.slow, JmEasing.emphasized, isEnter = true)

    val bottomBarIn get() = spec("bottomBarIn", m.base, m.enter, isEnter = true)
    val bottomBarOut get() = spec("bottomBarOut", (m.base * 0.83f).toInt(), m.exit, isEnter = false, pair = "bottomBarIn")

    val dialogIn get() = spec("dialogIn", (m.base * 0.9f).toInt(), m.enter, isEnter = true)
    val dialogOut get() = spec("dialogOut", (m.base * 0.75f).toInt(), m.exit, isEnter = false, pair = "dialogIn")

    val readerBarsIn get() = spec("readerBarsIn", m.fast * 2, JmEasing.standard, isEnter = true)
    val readerBarsOut get() = spec("readerBarsOut", (m.fast * 1.67f).toInt(), JmEasing.standard, isEnter = false, pair = "readerBarsIn")

    /** 骨架扫光：线性 = 进度未知，这里线性反而是正确语义。 */
    val loading get() = JmMotionSpec("loading", 1200, LinearEasing, isEnter = true)

    /** 错误抖动：只在"有输入被拒"时用。 */
    val errorShake get() = JmMotionSpec("errorShake", 200, JmEasing.outCubic, isEnter = true)

    /** 全部 token（供自检遍历；新增 token 时记得登记）。 */
    val all: List<JmMotionSpec>
        get() = listOf(
            press, listEnter, listExit, navForward, navBack, hero,
            bottomBarIn, bottomBarOut, dialogIn, dialogOut,
            readerBarsIn, readerBarsOut, loading, errorShake,
        )
}

/** 由风格派生出 token 表。 */
fun MotionSpec.toJmMotionTable(): JmMotionTable = JmMotionTable(this)

/**
 * 系统"减少动画"开启时的退化：统一 120ms 淡入淡出，不带位移。
 *
 * 为什么不直接"不动画"：完全无动画会让页面切换失去因果线索（用户会以为没点上），
 * 极短交叉淡入既尊重无障碍设置，又保留了"发生了变化"的反馈。
 */
fun JmMotionSpec.reducedMotion(): JmMotionSpec =
    JmMotionSpec("reduce(${name})", 120, JmEasing.standard, isEnter, pair)
