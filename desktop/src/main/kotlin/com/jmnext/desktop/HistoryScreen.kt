package com.jmnext.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.data.remote.dto.ListItem
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 历史（桌面端，1.9.x，整文件重写）。
 *
 * **为什么重写**：旧实现与追更共用一个 AccountListPage，而那个页面顶部的状态行用了
 * `Row(Modifier.fillMaxSize())` —— 它占满整个高度，导致下面的列表拿到零高度：
 * 数据到了、计数对、一个封面都不画（用户报的"列表空但显示已加载 N 条"）。
 * 补丁改成 fillMaxWidth 能治，但那一页还混着两个页面的需求；用户明确要求重写，
 * 于是这里独立成文件，布局一次写对。
 *
 * **布局约定（三个列表页统一）**：
 *   顶层 Column(fillMaxSize)
 *     ├─ 顶部条 Row(fillMaxWidth)      ← 只能用 fillMaxWidth
 *     └─ 列表 LazyVerticalGrid(weight(1f))  ← 占满剩余高度，不能用 fillMaxSize
 * 这条约定是为了避免"列表零高度"这类只有跑起来才看得见的错误。
 *
 * 删除单条历史是写操作（deleteHistory），只在用户点击时调用；开发期间我没点过它，
 * 所以该路径未经我验证。
 */
@Composable
fun HistoryScreen(repository: JmRepository, onOpenComic: (ListItem) -> Unit) {
    val loggedIn = repository.auth.isLoggedIn
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (loggedIn) "加载中…" else "需要登录后才能查看历史") }
    var notice by remember { mutableStateOf<String?>(null) }

    fun load(next: Int) {
        busy = true
        scope.launch {
            runCatching { repository.history(page = next) }
                .onSuccess { payload ->
                    items = if (next == 1) payload.list else (items + payload.list).distinctBy { it.id }
                    total = if (payload.totalCount > 0) payload.totalCount else items.size
                    page = next
                    status = if (items.isEmpty()) "还没有观看记录" else "已加载 ${items.size} 条 / 共 $total 条"
                    Log.line("历史", status)
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = "加载失败：${it.message}"
                    Log.error("历史", "加载失败", it)
                }
            busy = false
        }
    }

    LaunchedEffect(loggedIn) { if (loggedIn) load(1) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tl("历史"), style = MaterialTheme.typography.titleLarge)
StatusLine(status, busy)
            if (items.isNotEmpty() && items.size < total) {
                Button(enabled = !busy, onClick = { load(page + 1) }) { Text(tl("加载更多")) }
            }
            notice?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(items, key = { it.id }) { item ->
                Column {
                    ComicCover(repository, item, modifier = Modifier.animateItem()) { onOpenComic(item) }
                    TextButton(
                        onClick = {
                            busy = true
                            notice = null
                            scope.launch {
                                runCatching { repository.deleteHistory(item.id) }
                                    .onSuccess {
                                        items = items.filterNot { it.id == item.id }
                                        total = (total - 1).coerceAtLeast(0)
                                        Log.line("历史", "已删除一条：" + (item.name ?: item.id))
                                    }
                                    .onFailure {
                                        if (it is CancellationException) return@onFailure
                                        // 失败必须说出来：否则表现只是"那一行又回来了"，没有解释
                                        notice = "删除失败：${it.message}"
                                        Log.error("历史", "删除失败（列表未变）", it)
                                    }
                                busy = false
                            }
                        },
                    ) { Text(tl("删除"), style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}
