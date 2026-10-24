package com.jmnext.data.prefs

/**
 * 漫画图的渲染模式（只作用于**漫画画面**，不影响界面配色）。
 *
 * 加这个开关的动机：墨水屏设备（e-ink）上彩色图画会发灰、对比度低，
 * 而 OLED/LCD 上又没必要牺牲色彩。三档覆盖两类设备：
 * - [Color]：原样输出，普通屏幕默认；
 * - [Grayscale]：只去色（饱和度 0），层次保留，适合彩屏但想"更像纸"的场合；
 * - [Ink]：去色 + 提高对比度，专门给墨水屏 —— e-ink 的灰阶响应对低对比内容不友好，
 *   压一下暗部、提一下亮部，字与线条才立得住。
 *
 * 与官方 Web 端的关系：官方没有这个开关（浏览器端不做图像处理），
 * 属于本项目为墨水屏设备增加的本地特性，因此不列入 parity 对照。
 */
enum class ComicRenderMode(val label: String, val hint: String) {
    Color("彩色", "原样显示"),
    Grayscale("黑白", "只去色，层次保留"),
    Ink("墨水屏", "去色并提高对比度，适合 e-ink");

    companion object {
        /** 从持久化的名字还原；未知/损坏时退回 [Color]（不因一个坏值把画面弄坏）。 */
        fun fromName(name: String?): ComicRenderMode =
            entries.firstOrNull { it.name == name } ?: Color
    }
}
