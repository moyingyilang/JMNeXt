package com.jmnext.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.jmnext.ui.theme.JmTheme

/**
 * 区块标题。
 *
 * 字号按材质取官方值，而不是到处写 `titleLarge`：
 * - **Material（Tonal）**：`titleMedium` —— M3 的小节标题档；
 * - **Miuix（Card）**：`subtitle` 档（20sp，miuix-kmp 的 TextStyles 里对应 title3/title4）；
 * - 玻璃材质：`titleMedium`，与 Material 一致。
 *
 * 抽出来的理由和 `InfoRow` 一样：标题层级是**全站一致**的东西，
 * 每个页面各写一遍必然漂移（之前首页与设置页就不一致）。
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    // 目前 Material 与 Miuix 都落在 M3 的 titleMedium 档；差异留给字号令牌（ThemeStyle 的 TypeScale）去体现，
    // 这样这里不需要按材质分支，避免“同一层级出现两种写法”。
    val style = MaterialTheme.typography.titleMedium
    Text(
        // 服务端会把操作提示混进标题（实测「连载更新→右滑看更多→」），
        // 只取箭头前面的部分，避免把提示当成标题内容。
        text = text.substringBefore("→").trim().ifEmpty { text },
        style = style,
        color = JmTheme.colors.text,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** 区块标题 + 右侧动作（「更多 ›」这类）。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        SectionTitle(text = title, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
