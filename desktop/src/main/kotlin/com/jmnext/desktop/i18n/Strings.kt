package com.jmnext.desktop.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * 当前语言的文案表。
 *
 * 桌面端没有 Android 那套资源系统，文案统一从 [STRINGS] 里按 key 取；
 * 没注入时直接报错，免得静默显示成空白。
 */
val LocalStrings = compositionLocalOf<Map<String, String>> {
    error("LocalStrings 未提供：请在入口用 CompositionLocalProvider(LocalStrings provides ...) 包裹")
}

/** 当前语言码，供需要按语言分支的地方使用。 */
val LocalLocale = compositionLocalOf { "zh-CN" }

/**
 * 取一条文案；键不存在时返回键本身，方便一眼看出漏翻。
 *
 * 名字取 t / tf 而不是 s / sf：Compose 与常见工具里有不少单字母符号，
 * s() 会和它们撞重载解析（已经踩过一次）。
 */
@Composable
fun t(key: String): String = LocalStrings.current[key] ?: key

/** 带参数的一条文案，占位符写法与 Android 的 %1$s / %2$d 一致。 */
@Composable
fun tf(key: String, vararg args: Any?): String =
    String.format(LocalStrings.current[key] ?: key, *args)

/** 中文原文 → 当前语言译文的表，供 tl() 使用。 */
val LocalByZh = compositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * 按中文原文取译文。
 *
 * 桌面端现有文案是写死的中文，逐条改成键名成本高；这里先用中文原文当查找键：
 * 有对应译文就用译文，没有就原样返回中文。等整屏迁移完再逐步换成键名。
 */
@Composable
fun tl(zh: String): String = LocalByZh.current[zh] ?: zh

/** 带参数的版本：参数照常按 %1$s / %2$d 填。 */
@Composable
fun tlf(zh: String, vararg args: Any?): String =
    String.format(LocalByZh.current[zh] ?: zh, *args)

/**
 * 给非 Compose 代码用的取词入口：命令行提示、Swing 对话框、协程里赋给状态变量的文案。
 *
 * Compose 里请用 tl()/tlf()；这里读的是同一份「中文原文 → 译文」表，
 * 由入口在启动时注入，未注入时原样返回中文。
 */
object I18n {
    @Volatile
    private var byZh: Map<String, String> = emptyMap()

    fun install(table: Map<String, String>) {
        byZh = table
    }

    fun tr(zh: String): String = byZh[zh] ?: zh

    fun trf(zh: String, vararg args: Any?): String =
        String.format(byZh[zh] ?: zh, *args)
}
