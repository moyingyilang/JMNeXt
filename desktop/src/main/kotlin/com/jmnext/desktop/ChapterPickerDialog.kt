package com.jmnext.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmnext.data.remote.dto.SeriesItem
import com.jmnext.desktop.i18n.I18n
import com.jmnext.desktop.i18n.tl

/**
 * 章节选择对话框（桌面端，1.9.x）。
 *
 * **按 Android 的 `ChapterPickerDialog` 移植**（用户定的准则：样式跟 Android 版同步）：
 * 同样的标题「选择章节」、同样的**分页**列表（每页 chunkSize 话，drop/take 切片）、
 * 同样的**当前话高亮**（原注释："翻页后一眼能找到「我在哪」"）、同样的 confirmButton 翻页
 * 与 dismissButton「关闭」。
 *
 * 如实标注的一处差异：Android 的翻页按钮用**图标**，桌面端未引入 material-icons 依赖，
 * 这里改用文字「上一页 / 下一页」（与 PageRail 的处理一致）。
 *
 * 另一个坑是从 Android 原样带过来的：滚动状态必须**按页取**
 * （`remember(safePage) { ScrollState(0) }`）—— 若整屏共用一个，
 * 翻页后新页会停在上一页的偏移处，看起来像"这页内容缺了一半"。
 */
@Composable
fun ChapterPickerDialog(
    series: List<SeriesItem>,
    currentChapterId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val chunkSize = 50
    val pageCount = ((series.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)
    var page by remember(series.size, currentChapterId) {
        val index = series.indexOfFirst { it.id == currentChapterId }
        mutableIntStateOf(if (index < 0) 0 else index / chunkSize)
    }
    val safePage = page.coerceIn(0, pageCount - 1)
    val slice = series.drop(safePage * chunkSize).take(chunkSize)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tl("选择章节")) },
        text = {
            // 按页取滚动状态（Android 那边踩过的坑，见文件头注释）
            val scroll = remember(safePage) { ScrollState(0) }
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                slice.forEachIndexed { i, chapter ->
                    val number = safePage * chunkSize + i + 1
                    val isCurrent = chapter.id == currentChapterId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { onPick(chapter.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = chapter.sort ?: number.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(
                            text = chapter.name?.takeIf { it.isNotBlank() } ?: I18n.trf("第 %1\$d 话", number),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = safePage > 0, onClick = { page = safePage - 1 }) { Text(tl("上一页")) }
                Text(
                    text = I18n.trf("第 %1\$d/%2\$d 页", safePage + 1, pageCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(enabled = safePage < pageCount - 1, onClick = { page = safePage + 1 }) { Text(tl("下一页")) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tl("关闭")) }
        },
    )
}
