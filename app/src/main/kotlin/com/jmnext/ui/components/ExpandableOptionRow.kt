package com.jmnext.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmnext.ui.theme.JmTheme
import com.jmnext.ui.theme.Radius
import com.jmnext.ui.theme.Spacing

/**
 * 可展开的竖向选项组。
 *
 * 参照 `Xuewu-awa/bvds-for-Android` 里「清晰度」下拉的做法（`app.css` 的两段式：
 * 容器撑高 + 内容回弹），这里用 Compose 的等价实现：
 * `expandVertically + fadeIn` 撑开、箭头 `animateFloatAsState` 转 180°。
 *
 * 为什么需要它：壁纸来源这类选项已经有七八个，平铺成 chips 会挤成两三行、
 * 每次都要在整屏里找；折叠成一行「当前值 + 箭头」，展开后是竖向列表，一眼扫完。
 *
 * @param options 选项：值是任意类型，label 用于显示
 * @param selected 当前选中值
 * @param onSelect 选中回调（点击后自动收起）
 * @param summary 收起状态下标题右侧显示的文案，默认取选中项的 label
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
        animationSpec = tween(220),
        label = "arrow",
    )
    val current = options.firstOrNull { it.first == selected }?.second ?: ""

    Column(modifier = modifier.fillMaxWidth()) {
        // 头：标题 + 当前值 + 箭头，整行可点（≥48dp）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded },
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
            enter = expandVertically(animationSpec = tween(240)) + fadeIn(tween(180)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(140)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                options.forEach { (value, label) ->
                    val on = value == selected
                    Row(
                        modifier = Modifier
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

