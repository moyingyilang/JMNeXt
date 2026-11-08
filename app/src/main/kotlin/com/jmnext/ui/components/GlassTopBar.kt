package com.jmnext.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmnext.ui.theme.JmTheme
import com.jmnext.ui.theme.Sizing
import com.jmnext.ui.theme.Spacing
import com.jmnext.ui.theme.SurfaceCraft

/**
 * 毛玻璃顶栏。
 *
 * 带 MIUI 橙→蓝薄层（[tinted]），并自己处理状态栏内边距 ——
 * 应用是 edge-to-edge 的，顶栏必须自己让开系统栏，否则内容会被状态栏压住。
 *
 * @param navigation 左侧，通常是返回键
 * @param actions 右侧操作区
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = JmTheme.colors

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        level = GlassLevel.Raised,
        shape = RoundedCornerShape(0.dp),
        tinted = false,   // 纯粹的玻璃：不要暖→冷渐变层（用户明确要去掉栏上的渐变残留）
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(Sizing.appBar)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation?.invoke()

            Box(Modifier.weight(1f).padding(start = if (navigation == null) Spacing.sm else 0.dp)) {
                Column(
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        // 字重按材质取官方值：Material 的 app bar 用 titleLarge（M3 标准），
                        // Miuix（HyperOS）标题层级更低一档，用 titleMedium 更接近原生观感。
                        style = if (JmTheme.spec.surface.craft == SurfaceCraft.Card) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                content = actions,
            )
        }
    }
}
