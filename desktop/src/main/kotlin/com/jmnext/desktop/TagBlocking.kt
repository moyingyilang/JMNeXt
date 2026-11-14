package com.jmnext.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmnext.data.remote.dto.ListItem

/**
 * 标签级屏蔽在**列表页**的统一接入（照 Android：被挡的条数要明示，并给「允许一次」）。
 *
 * 抽成共用三件套的原因：分类、随机、作者、更多这四个列表页的接入方式完全一样，
 * 各写一遍的话，改屏蔽文案或改「允许一次」的语义就得记住改四处。
 */

/** 命中集合；未初始化时返回空集（此时不做任何过滤，与 Android 一致）。 */
@Composable
fun rememberHiddenTagIds(): Set<String> {
    val flow = TagBlocker.hidden
    val state = flow?.collectAsState() ?: remember { mutableStateOf<Set<String>>(emptySet()) }
    return state.value
}

/** 按命中集合拆成「被挡」与「可见」。 */
fun <T> splitBlockedByTag(items: List<T>, hiddenIds: Set<String>, idOf: (T) -> String): Pair<List<T>, List<T>> =
    items.partition { idOf(it) in hiddenIds }

/** 被挡条目提示条 + 「允许一次」。 */
@Composable
fun <T> BlockedByTagBanner(blocked: List<T>, idOf: (T) -> String) {
    if (blocked.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val tags = blocked.flatMap { TagBlocker.blockedTagsOf(idOf(it)) }.distinct().take(4)
        Text(
            "已按标签屏蔽 ${blocked.size} 条（命中：${tags.joinToString("、")}）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { TagBlocker.allowOnce(blocked.map { idOf(it) }.toSet()) }) { Text("允许一次") }
    }
}
