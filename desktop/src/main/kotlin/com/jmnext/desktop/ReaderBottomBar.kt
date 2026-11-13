package com.jmnext.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jmnext.desktop.i18n.tl

/**
 * 阅读页底部悬浮栏（桌面端，1.9.x）。**按 Android 阅读页的底栏位置与内容移植**。
 *
 * 为什么做这个：Android 的章节切换、进度条、页码与那五个功能**本来就在底栏**，
 * 默认隐藏、点一下才出现（沉浸阅读）。我先前把这些塞进侧栏，是"形式照搬"而不是照布局；
 * 用户也提出"不应该写一个翻页按钮在下面，像悬浮底栏一样吗"。本文件是那个容器。
 *
 * 内容顺序（左到右）：上一话 · 上一页 ｜ 进度条 + 页码 ｜ 下一页 · 下一话 ｜ 五个功能。
 * 五个功能与侧栏一致：纵向（模式）· 章节 · 评论 · 收藏 · 点赞；传 null 的置灰。
 *
 * 显示/隐藏由调用方控制 [visible]（例如鼠标移到窗口底部或点击内容区时显示，几秒不动隐藏），
 * 本组件只负责外观与动画。**尚未接线**：这一步只保证它能编译。
 */
@Composable
fun ReaderBottomBar(
    visible: Boolean,
    current: Int,
    total: Int,
    onSeek: (Int) -> Unit,
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    hasPrevPage: Boolean,
    hasNextPage: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    modeLabel: String = "纵向",
    onToggleMode: (() -> Unit)? = null,
    onOpenPicker: (() -> Unit)? = null,
    onOpenComments: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onToggleLike: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tight = androidx.compose.foundation.layout.PaddingValues(0.dp)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    // 进度：与侧栏滑轨同一个回调（页码语义相同，都是图片下标）
                    val max = (total - 1).coerceAtLeast(0)
                    val frac = if (max <= 0) 0f else current.coerceIn(0, max).toFloat() / max
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onPrevChapter, enabled = hasPrevChapter) { Text(tl("上一话")) }
                        TextButton(onClick = onPrevPage, enabled = hasPrevPage, contentPadding = tight) { Text("<") }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${current + 1}/$total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onNextPage, enabled = hasNextPage, contentPadding = tight) { Text(">") }
                        TextButton(onClick = onNextChapter, enabled = hasNextChapter) { Text(tl("下一话")) }

                        Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BarAction(modeLabel, onToggleMode)
                            BarAction(tl("章节"), onOpenPicker)
                            BarAction(tl("评论"), onOpenComments)
                            BarAction(tl("收藏"), onToggleFavorite)
                            BarAction(tl("点赞"), onToggleLike)
                        }
                    }
                }
            }
        }
    }
}

/** 底栏里的功能按钮：传 null 表示未实现，置灰并保持位置（与侧栏的处理一致）。 */
@Composable
private fun BarAction(label: String, action: (() -> Unit)?) {
    TextButton(
        onClick = { action?.invoke() },
        enabled = action != null,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.height(28.dp).padding(horizontal = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
