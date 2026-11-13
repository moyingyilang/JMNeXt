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

/** 取一条文案；键不存在时返回键本身，方便一眼看出漏翻。 */
@Composable
fun s(key: String): String = LocalStrings.current[key] ?: key

/** 带参数的一条文案，占位符写法与 Android 的 %1$s / %2$d 一致。 */
@Composable
fun sf(key: String, vararg args: Any?): String =
    String.format(LocalStrings.current[key] ?: key, *args)
