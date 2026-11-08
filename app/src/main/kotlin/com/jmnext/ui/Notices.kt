package com.jmnext.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.jmnext.ui.theme.Spacing
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 瞬时反馈（"操作做完给个交代"）。
 *
 * 为什么要它：审计发现两端都没有任何瞬时反馈 —— 收藏、追更、签到、复制、屏蔽这些操作
 * 要么只写日志、要么只改内联文字，用户不知道到底成没成。
 *
 * 设计要点（都是交互逻辑，不只是"弹个提示"）：
 *  - **同一条消息重复触发要重新计时**：连点两次收藏，提示不该在第一次的计时结束时提前消失
 *    （所以每次 show 都递增序号，宿主按序号重启计时）；
 *  - **失败要留得久一点**：失败往往需要看清原因，故 ERROR 的停留时间是 INFO/SUCCESS 的两倍；
 *  - **不挡操作**：宿主整层不可点击（透明覆盖层），只在底部居中显示；
 *  - **动效走既有约定**：进场/退场时长取自本平台的动效 token（见 docs/MOTION.md），不硬写数值。
 */
enum class NoticeKind { INFO, SUCCESS, ERROR }

data class NoticeItem(val id: Long, val text: String, val kind: NoticeKind)

object Notices {
    private val seq = java.util.concurrent.atomic.AtomicLong(0)
    private val _state = MutableStateFlow<NoticeItem?>(null)
    val state: StateFlow<NoticeItem?> = _state

    fun show(text: String, kind: NoticeKind = NoticeKind.INFO) {
        _state.value = NoticeItem(seq.incrementAndGet(), text, kind)
    }

    fun success(text: String) = show(text, NoticeKind.SUCCESS)
    fun error(text: String) = show(text, NoticeKind.ERROR)
    fun clear() { _state.value = null }
}

/** 把 [Notices] 显示出来；放在应用根部（覆盖层），不要放在会随页面销毁的位置。 */
@Composable
fun NoticeHost(
    enterMs: Int = 220,
    exitMs: Int = 120,
    infoMs: Long = 2400,
    errorMs: Long = 4800,
) {
    val item by Notices.state.collectAsState()
    // 同一条消息重复触发时重新计时：key 用 id
    LaunchedEffect(item?.id) {
        val cur = item ?: return@LaunchedEffect
        delay(if (cur.kind == NoticeKind.ERROR) errorMs else infoMs)
        if (Notices.state.value?.id == cur.id) Notices.clear()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = item != null,
            enter = fadeIn(tween(enterMs)) + slideInVertically(tween(enterMs)) { it / 3 },
            exit = fadeOut(tween(exitMs)) + slideOutVertically(tween(exitMs)) { it / 3 },
        ) {
            val cur = item
            if (cur != null) {
                Text(
                    text = cur.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (cur.kind) {
                        NoticeKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .padding(bottom = 28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when (cur.kind) {
                                NoticeKind.ERROR -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
        }
    }
}
