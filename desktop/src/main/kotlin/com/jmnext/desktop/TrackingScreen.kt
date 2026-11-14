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
import com.jmnext.desktop.i18n.I18n
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 追更（桌面端，1.9.x）。
 *
 * **结构说明（与 Android 对齐中）**：Android 端的追更**不是独立页面**，而是收藏页里的一个
 * 标签。桌面端此前做成了独立页，本轮把列表体抽成可复用的 [TrackingList] ——
 * 收藏页的「追更」标签可以直接内嵌它，侧栏的独立入口也仍然可用。
 *
 * 布局约定（与历史/随机一致，见 HistoryScreen 注释）：
 *   顶层 Column(fillMaxSize) → 顶部条 Row(fillMaxWidth) → 列表 weight(1f)
 * 这条约定是为了根治"数据到了但列表不画"——它编译不报、只有跑起来才看得见。
 */
@Composable
fun TrackingScreen(repository: JmRepository, onOpenComic: (ListItem) -> Unit) {
    var status by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tl("追更"), style = MaterialTheme.typography.titleLarge)
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TrackingList(
            repository = repository,
            onOpenComic = onOpenComic,
            onStatus = { status = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/**
 * 追更列表体（可内嵌）。
 *
 * 抽出来的目的：收藏页的「追更」标签要用同一份逻辑，而不是复制一遍 ——
 * 否则两处会各自演化（这个项目里已经发生过几次）。
 *
 * 状态由本组件自己持有；[onStatus] 只用于把状态文字回传给外层标题栏（可选）。
 */
@Composable
fun TrackingList(
    repository: JmRepository,
    onOpenComic: (ListItem) -> Unit,
    modifier: Modifier = Modifier,
    onStatus: (String) -> Unit = {},
) {
    val loggedIn = repository.auth.isLoggedIn
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (loggedIn) "加载中…" else "需要登录后才能查看追更") }

    fun report(text: String) {
        status = text
        onStatus(text)
    }

    fun load(next: Int) {
        busy = true
        scope.launch {
            runCatching { repository.trackingList(page = next) }
                .onSuccess { paged ->
                    items = if (next == 1) paged.items else (items + paged.items).distinctBy { it.id }
                    total = if (paged.total > 0) paged.total else items.size
                    page = next
                    var text = if (items.isEmpty()) {
                        "还没有追更的作品"
                    } else {
                        I18n.trf("已加载 %1\$d 条 / 共 %2\$d 条", items.size, total)
                    }
                    if (paged.hidden > 0) text += I18n.trf("（本页被屏蔽挡掉 %1\$d 条）", paged.hidden)
                    report(text)
                    Log.line("追更", text)
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    report(I18n.trf("加载失败：%1\$s", it.message))
                    Log.error("追更", "加载失败", it)
                }
            busy = false
        }
    }

    LaunchedEffect(loggedIn) { if (loggedIn) load(1) }

    Column(modifier) {
        if (items.isNotEmpty() && items.size < total) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(enabled = !busy, onClick = { load(page + 1) }) { Text(tl("加载更多")) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(items, key = { it.id }) { item -> ComicCover(repository, item, modifier = Modifier.animateItem()) { onOpenComic(item) } }
        }
    }
}
