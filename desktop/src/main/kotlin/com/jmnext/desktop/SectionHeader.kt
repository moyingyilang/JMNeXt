package com.jmnext.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 区块标题，对应 Android 的 `ui/components/SectionTitle.kt`。
 *
 * 两件事必须和手机端一致：
 * 1. 服务端会把操作提示混进标题（实测「连载更新→右滑看更多→」），只取箭头前的部分；
 * 2. 字号走令牌里的 [TypeScale.title]，而不是各处写 `titleMedium`，
 *    否则首页与其它页很快就会漂移。
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    val spec = LocalJmSpec.current
    Text(
        text = text.substringBefore("→").trim().ifEmpty { text },
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = spec.type.titleWeight,
        ),
        color = MaterialTheme.colorScheme.onSurface,
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
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionTitle(text = title, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}


