package com.jmnext.desktop

import com.jmnext.desktop.i18n.I18n
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 周刊（2.0.0 桌面端）。
 *
 * 刊期与类型都来自接口：先取刊期列表，默认用**最新一期**与第一个类型，
 * 避免让用户先做两次选择才能看到内容；选完之后可以切换。
 */
@Composable
fun WeekScreen(repository: JmRepository, onOpenComic: (ListItem) -> Unit) {
    var issues by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }   // id to 标题
    var types by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }    // id to 名称
    var issueId by remember { mutableStateOf<String?>(null) }
    var typeId by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    var hidden by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在取刊期…") }
    val scope = rememberCoroutineScope()

    fun loadList(issue: String, type: String, next: Int) {
        busy = true
        scope.launch {
            runCatching { repository.weekList(issueId = issue, type = type, page = next) }
                .onSuccess { paged ->
                    items = if (next == 1) paged.items else (items + paged.items).distinctBy { it.id }
                    hidden += paged.hidden
                    page = next
                    status = I18n.trf("已加载 %1\$d 条", items.size) + if (paged.hidden > 0) I18n.trf("（本页屏蔽 %1\$d 条）", paged.hidden) else ""
                    System.err.println("[周刊] $status")
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = I18n.trf("加载失败：%1\$s", it.message)
                    System.err.println("[周刊] $status")
                }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { repository.weekIssues() }
            .onSuccess { payload ->
                issues = payload.categories.map { it.id to (it.title ?: it.time ?: it.id) }
                types = payload.type.map { it.id to (it.title ?: it.name ?: it.id) }
                val issue = issues.firstOrNull()?.first
                val type = types.firstOrNull()?.first
                issueId = issue
                typeId = type
                System.err.println("[周刊] 刊期 ${issues.size} 个、类型 ${types.size} 个，默认取最新一期")
                if (issue != null && type != null) loadList(issue, type, 1) else status = "没有可用的刊期"
            }
            .onFailure {
                if (it is CancellationException) return@onFailure
                status = I18n.trf("刊期加载失败：%1\$s", it.message)
                System.err.println("[周刊] $status")
            }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(tl("周刊"), style = MaterialTheme.typography.titleLarge)
StatusLine(status, busy)
            if (issues.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tl("刊期"), style = MaterialTheme.typography.labelSmall)
                    issues.forEach { (id, title) ->
                        Button(
                            enabled = !busy && id != issueId,
                            onClick = { issueId = id; typeId?.let { t -> loadList(id, t, 1) } },
                        ) { Text(title, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            // 类型切换器：原先只用第一个类型，等于类型这一维完全没做。
            if (types.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tl("类型"), style = MaterialTheme.typography.labelSmall)
                    types.forEach { (id, title) ->
                        Button(
                            enabled = !busy && id != typeId,
                            onClick = { typeId = id; issueId?.let { i -> loadList(i, id, 1) } },
                        ) { Text(title, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        BlockedNotice(hidden)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.id }) { item -> ComicCover(repository, item, modifier = Modifier.animateItem()) { onOpenComic(item) } }
            if (items.isNotEmpty()) {
                item {
                    Button(
                        enabled = !busy,
                        onClick = { issueId?.let { i -> typeId?.let { t -> loadList(i, t, page + 1) } } },
                    ) { Text(tl("加载更多")) }
                }
            }
        }
    }
}
