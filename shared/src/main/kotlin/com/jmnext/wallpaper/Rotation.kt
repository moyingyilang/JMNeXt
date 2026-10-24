package com.jmnext.wallpaper

/**
 * 壁纸轮换的一步决策（纯函数，便于单测）。
 *
 * 抽出来的动机：用户反馈"翻来覆去只有那几张"，根因有两处 ——
 * 1. 攒够 CACHE_MIN 后 `needFetch` 恒为 false，池子再也不更新；
 * 2. 合并候选时老图在前，被 `take(CACHE_MAX)` 截掉新图。
 * 这两处都在有 Android 依赖的 `WallpaperStore` 里，没法单测。这里只抽出**游标推进**这一小段：
 * 它决定了"这次取哪张""是否走完一轮"，是"会不会重复同一张"的关键。
 *
 * @param rawCursor 持久化的游标（**不取模**，这样调用方能知道是否回绕）
 * @param pool 候选地址池
 * @param currentUrl 当前正在显示的地址（用于避免连点两次看到同一张）
 */
data class RotationStep(
    /** 本次应取的池内下标。 */
    val index: Int,
    /** 下次应持久化的游标值。 */
    val nextCursor: Int,
    /** 本次是否走完了一整轮 —— 为 true 时调用方应去补一批新图。 */
    val wrap: Boolean,
)

/**
 * 算一步轮换。
 *
 * 语义（与原先 `pick()` 里的内联逻辑一致，抽出后行为不变）：
 * - 下标按 `rawCursor % pool.size` 取；
 * - 若取到的正是当前这张，且池子不止一张，则后移一位（避免连点两次同一张）；
 * - `nextCursor` 回绕到 0 时标记 [RotationStep.wrap]，提示"该补货了"。
 *
 * @throws IllegalArgumentException 池子为空（调用方应保证非空，空池本身就是错误状态）
 */
fun rotationStep(rawCursor: Int, pool: List<String>, currentUrl: String?): RotationStep {
    require(pool.isNotEmpty()) { "壁纸候选池为空" }
    val size = pool.size
    var index = ((rawCursor % size) + size) % size          // 防御负数游标
    if (size > 1 && pool.getOrNull(index) == currentUrl) index = (index + 1) % size
    val next = (index + 1) % size
    return RotationStep(index = index, nextCursor = next, wrap = next == 0)
}
