package com.jmnext.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmnext.ui.theme.JmTheme
import com.jmnext.ui.theme.Spacing

/**
 * 可展开的竖向选项组。
 *
 * 参照 `Xuewu-awa/bvds-for-Android` 的「清晰度」下拉（`app.css` 两段式：容器撑高 + 内容回弹），
 * 用 Compose 的等价实现，并把动画调成**弹性**而不是线性：
 * - 展开/收起用 spring（dampingRatio 0.7 左右），有轻微过冲，收尾利落 —— 这就是“灵动”的来源；
 * - 箭头旋转同样是 spring，但阻尼更低，转得快一点；
 * - 淡入淡出只负责遮掉首尾的硬边，时长压到 150ms 以内。
 *
 * 为什么需要它：壁纸来源、玻璃质量、动效、自动切换间隔这些选项都有 3–8 个值，
 * 平铺成 chips 会挤成两三行；折叠成一行「当前值 ▾」，展开后是竖向列表，一眼扫完。
 */
@Composable
fun <T> ExpandableOptionRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    val c = JmTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        // 阻尼 0.55：比展开动画更“脆”，点下去箭头立刻甩过去
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "arrow",
    )
    val current = options.firstOrNull { it.first == selected }?.second ?: ""

    // 按压反馈：点下的瞬间整行轻微缩小（0.98）+ 底色淡入，抬起回弹。
    // 用 spring 而不是 tween —— 手指按住时要有“被压住”的实感，松开要弹得快。
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press-scale",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // 头：标题 + 当前值 + 箭头，整行可点（≥48dp）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                ) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = summary ?: current,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "▾",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textTertiary,
                modifier = Modifier.rotate(arrow),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            // 展开：spring 带一点过冲（0.72 / MediumLow），像抽屉被轻轻弹出来
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn(tween(140)),
            // 收起：阻尼更低、更干脆，不让它慢慢缩
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
            ) + fadeOut(tween(120)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                options.forEachIndexed { index, (value, label) ->
                    val on = value == selected
                    // 错峰入场：每一项按 index 延迟一点点浮上来，整块不是“啪”地一起出现。
                    // 延迟要短（每项 28ms、上限 120ms），否则选项多时会显得拖沓。
                    val delayMs = (index * 28).coerceAtMost(120)
                    var shown by remember { mutableStateOf(false) }
                    val appear by animateFloatAsState(
                        targetValue = if (shown) 1f else 0f,
                        animationSpec = tween(durationMillis = 180, delayMillis = delayMs),
                        label = "item-appear",
                    )
                    LaunchedEffect(Unit) { shown = true }
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = appear
                                translationY = (1f - appear) * 8.dp.toPx()
                            }
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.RadioButton) {
                                onSelect(value)
                                expanded = false
                            }
                            .padding(horizontal = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = if (on) "●" else "○",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (on) c.accent else c.textTertiary,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (on) c.accent else c.text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
