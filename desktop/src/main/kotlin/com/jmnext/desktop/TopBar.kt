package com.jmnext.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 顶栏，对应 Android 的 `ui/components/GlassTopBar.kt`。
 *
 * 桌面端没有状态栏，所以这不是「状态栏下面那条」，而是应用自己的第一条横带：
 * 半透明表面 + 下缘发丝线，让内容看起来是**从栏下滚过去**的。
 * 之前桌面端把标题和一个按钮直接摆在内容里，没有"栏"这层，观感上就少了一截。
 *
 * 高度取 Android 的 `Sizing.appBar`（56dp）。
 */
@Composable
fun JmTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val spec = LocalJmSpec.current
    val hairline = spec.surface.hairline
    val outline = MaterialTheme.colorScheme.outline
    val barFill = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(barFill)
            .drawBehind {
                if (hairline > androidx.compose.ui.unit.Dp(0f)) {
                    val y = size.height - hairline.toPx() / 2f
                    drawLine(
                        color = outline,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = hairline.toPx(),
                    )
                }
            }
            .padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        navigation?.invoke()
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onTitleClick != null) Modifier.clickable { onTitleClick() } else Modifier,
                ),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = spec.type.titleWeight,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions,
            )
        }
    }
}
