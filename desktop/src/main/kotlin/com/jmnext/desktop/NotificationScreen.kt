package com.jmnext.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.data.remote.dto.NotificationItem
import com.jmnext.desktop.i18n.I18n
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 通知（桌面端，1.9.x，整文件重写）。
 *
 * 布局约定（与历史/追更/随机一致，见 HistoryScreen 注释）：
 *   顶层 Column(fillMaxSize) → 顶部条 Row(fillMaxWidth) → 列表 weight(1f)
 *
 * 这一版补上**筛选标签**：`notifications(type = ...)` 的取值照抄 Android 的 TABS
 * （`all` / `comic_follow` / `site_notice`），此前桌面端只用了默认的 all。
 *
 * 两个接口历史坑沿用数据层已处理过的形态：
 *  1. 同名字段在不同响应里可能是字符串或数字 —— DTO 全按 JsonElement 收、由访问器解释；
 *  2. `data` 有时是裸数组、有时是 {list,total} —— 由 NotificationPage.from 兜住。
 *
 * 显示上只列标题、类型与未读标记：`content` 在 DTO 里没有公开访问器（它是 JsonElement），
 * 所以正文这一栏拿不到，页面上不假装有 —— 这条如实标注。
 */
@Composable
fun NotificationScreen(repository: JmRepository) {
    val scope = rememberCoroutineScope()

    // 取值照抄 Android 的 TABS
    val tabs = listOf(
        tl("全部") to "all",
        tl("追更") to NotificationItem.TYPE_COMIC_FOLLOW,
        tl("站内通知") to NotificationItem.TYPE_SITE_NOTICE,
    )

    var tab by remember { mutableStateOf("all") }
    var items by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var unread by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(I18n.tr("正在取通知…")) }

    fun load(next: Int) {
        busy = true
        scope.launch {
            runCatching { repository.notifications(type = tab, page = next) }
                .onSuccess { paged ->
                    items = if (next == 1) paged.list else (items + paged.list)
                        .distinctBy { it.idText ?: it.hashCode().toString() }
                    page = next
                    status = I18n.trf("已加载 %1\$d 条 / 共 %2\$d 条", items.size, paged.total)
                    Log.line("通知", "$status（筛选：$tab）")
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = I18n.trf("加载失败：%1\$s", it.message)
                    Log.error("通知", "加载失败 tab=$tab", it)
                }
            busy = false
        }
    }

    /**
     * 标记一条通知为已读。
     * 与 Android 一致：只做"标记已读"，不自己判断有没有更新；成功后重新拉第一页与未读数。
     */
    fun markRead(id: String) {
        busy = true
        scope.launch {
            runCatching { repository.markNotificationRead(id, true) }
                .onSuccess { Log.line("通知", "已标记已读 id=" + id) }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = I18n.tr("标记已读失败：") + it.message
                    Log.error("通知", "标记已读失败 id=" + id, it)
                }
            runCatching { repository.notificationsUnread() }.onSuccess { unread = it.total }
            busy = false
            load(1)
        }
    }

    LaunchedEffect(tab) {
        page = 1
        load(1)
        runCatching { repository.notificationsUnread() }
            .onSuccess {
                unread = it.total
                Log.line("通知", "未读 ${it.total} 条")
            }
            .onFailure {
                if (it is CancellationException) return@onFailure
                Log.error("通知", "未读数读取失败", it)
            }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(tl("通知"), style = MaterialTheme.typography.titleLarge)
            tabs.forEach { (label, value) ->
                TextButton(enabled = !busy, onClick = { if (tab != value) tab = value }) {
                    Text(
                        if (tab == value) "· $label" else label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (unread > 0) {
                Text(I18n.trf("未读 %1\$d", unread), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
StatusLine(status, busy)
            if (items.isNotEmpty()) {
                Button(enabled = !busy, onClick = { load(page + 1) }) { Text(tl("加载更多")) }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.idText ?: it.hashCode().toString() }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 未读标记：一个小圆点，不用符号
                    Column(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.isRead) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.primary,
                            ),
                    ) {}
                    Column(Modifier.weight(1f)) {
                        if (item.typeText == NotificationItem.TYPE_COMIC_FOLLOW) {
                            // 追更通知的标题不在 title 字段，而在 content 数组里（followedUpdates）——
                            // 这就是此前"追更标签下全被解析成无标题"的原因。
                            item.followedUpdates().forEach { up ->
                                Text(
                                    up.comicTitleText ?: up.comicIdText ?: tl("(未知作品)"),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    listOfNotNull(tl("更新"), up.updateDateText).joinToString(" "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(item.titleText ?: tl("(无标题)"), style = MaterialTheme.typography.bodyMedium)
                            // 站内通知的正文是 HTML，DTO 有 siteNoticeHtml()，转纯文本再渲染
                            item.siteNoticeHtml()?.plainText()?.let { body ->
                                Text(body, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                listOfNotNull(item.typeText, item.dateText).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // 标记已读：Android 端也是单条标记（markNotificationRead(id, true)）。
                    // 只对未读显示按钮；读完重新拉第一页与未读数，让服务端状态如实反映。
                    if (!item.isRead) {
                        TextButton(
                            enabled = !busy,
                            onClick = { item.idText?.let { markRead(it) } },
                        ) { Text(tl("标记已读")) }
                    }
                }
            }
        }
    }
}
