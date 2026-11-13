package com.jmnext.desktop

import com.jmnext.desktop.i18n.I18n
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.jmnext.data.remote.dto.FavoriteFolder
import com.jmnext.data.remote.dto.ListItem
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 收藏页（桌面端，1.9.x，整文件重写）。
 *
 * **结构对齐 Android**：那边收藏页里有标签（含「追更」），追更**不是独立页面**。
 * 桌面端现在也这样了 —— 「追更」标签内嵌上一轮抽出的 [TrackingList]，
 * 两处共用同一份逻辑（而不是复制一遍各自演化）。
 *
 * 布局约定（与历史/随机/通知一致，见 HistoryScreen 注释）：
 *   顶层 Column(fillMaxSize) → 顶部条 Row(fillMaxWidth) → 内容 weight(1f)
 *
 * 文件夹切换：清单来自收藏列表响应的 `folder_list`（字段名是 FID，
 * DTO 里专门警告过用 id 会静默拿到空串、表现为"收藏夹全是无名项"）。
 *
 * 写操作说明：列表内的「取消收藏」是 toggleFavorite，只在用户点击时调用；
 * 开发期间我一次没点过（用的是用户账号），所以该路径**未经我验证**。
 */
@Composable
fun FavoriteScreen(
    repository: JmRepository,
    onOpenComic: (ListItem) -> Unit,
    initialTab: String = "favorite",
) {
    val loggedIn = repository.auth.isLoggedIn
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(initialTab) }
    var folders by remember { mutableStateOf<List<FavoriteFolder>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }   // null 表示「全部」
    var items by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (loggedIn) I18n.tr("加载中…") else I18n.tr("需要登录后才能查看收藏")) }
    var notice by remember { mutableStateOf<String?>(null) }
    // 收藏夹管理用的输入框内容（新建与改名共用）
    var folderName by remember { mutableStateOf("") }

    fun load(next: Int) {
        busy = true
        scope.launch {
            runCatching { repository.favorites(page = next, folderId = selected) }
                .onSuccess { payload ->
                    items = if (next == 1) payload.list else (items + payload.list).distinctBy { it.id }
                    if (payload.folderList.isNotEmpty()) folders = payload.folderList
                    total = if (payload.totalCount > 0) payload.totalCount else items.size
                    page = next
                    val name = folders.firstOrNull { it.folderId == selected }?.name ?: I18n.tr("全部")
                    status = if (items.isEmpty()) I18n.trf("%1\$s：还没有作品", name) else I18n.trf("%1\$s：已加载 %2\$d 条 / 共 %3\$d 条", name, items.size, total)
                    Log.line("收藏", "$status（文件夹 ${folders.size} 个）")
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = I18n.tr("加载失败：") + it.message
                    Log.error("收藏", "加载失败", it)
                }
            busy = false
        }
    }

    LaunchedEffect(loggedIn) { if (loggedIn) load(1) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 标签：收藏 / 追更（Android 端追更就在这一页里）
            TextButton(enabled = !busy, onClick = { if (tab != "favorite") tab = "favorite" }) {
                Text(if (tab == "favorite") "· " + tl("收藏") else tl("收藏"), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(enabled = !busy, onClick = { if (tab != "tracking") tab = "tracking" }) {
                Text(if (tab == "tracking") "· " + tl("追更") else tl("追更"), style = MaterialTheme.typography.titleMedium)
            }
            if (tab == "favorite") {
              // 空态/未登录时由中间的 MessageState 表达，不在顶部重复一行
              if (items.isNotEmpty() || busy) StatusLine(status, busy)
                if (items.isNotEmpty() && items.size < total) {
                    Button(enabled = !busy, onClick = { load(page + 1) }) { Text(tl("加载更多")) }
                }
                notice?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (tab == "tracking") {
            // 内嵌追更列表（与侧栏独立入口共用同一份逻辑）
            TrackingList(
                repository = repository,
                onOpenComic = onOpenComic,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            return@Column
        }

        // ── 收藏标签 ──
        if (folders.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = !busy && selected != null,
                    onClick = { selected = null; page = 1; load(1) },
                ) { Text(if (selected == null) "· " + tl("全部") else tl("全部"), style = MaterialTheme.typography.labelSmall) }
                folders.forEach { f ->
                    Button(
                        enabled = !busy && f.folderId != selected,
                        onClick = { selected = f.folderId; page = 1; load(1) },
                    ) {
                        Text(
                            (if (f.folderId == selected) "· " else "") + (f.name ?: f.folderId),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        // ── 收藏夹管理（新建 / 改名 / 删除）──
        // 类型常量照 Android 的定义：add / edit / del / move（这里做前三种）。
        // 三者都是写操作，只在用户点击时调用；开发期间我一次没点过，所以均未验证。
        if (loggedIn) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(tl("收藏夹名称")) },
                    singleLine = true,
                    modifier = Modifier.width(220.dp),
                )
                Button(
                    enabled = !busy && folderName.isNotBlank(),
                    onClick = {
                        val name = folderName.trim()
                        busy = true
                        notice = null
                        scope.launch {
                            runCatching { repository.editFavoriteFolder(type = "add", folderName = name) }
                                .onSuccess {
                                    notice = I18n.tr("已新建收藏夹：")
                                    folderName = ""
                                    Log.line("收藏", "新建收藏夹：")
                                    load(1)
                                }
                                .onFailure {
                                    if (it is CancellationException) return@onFailure
                                    notice = I18n.tr("新建失败：") + it.message
                                    Log.error("收藏", "新建收藏夹失败", it)
                                }
                            busy = false
                        }
                    },
                ) { Text(tl("新建")) }
                if (selected != null) {
                    Button(
                        enabled = !busy && folderName.isNotBlank(),
                        onClick = {
                            val id = selected ?: return@Button
                            val name = folderName.trim()
                            busy = true
                            notice = null
                            scope.launch {
                                runCatching { repository.editFavoriteFolder(type = "edit", folderId = id, folderName = name) }
                                    .onSuccess {
                                        notice = I18n.tr("已改名") 
                                        folderName = ""
                                        load(1)
                                    }
                                    .onFailure {
                                        if (it is CancellationException) return@onFailure
                                        notice = I18n.tr("改名失败：") + it.message
                                        Log.error("收藏", "改名失败", it)
                                    }
                                busy = false
                            }
                        },
                    ) { Text(tl("改名")) }
                    Button(
                        enabled = !busy,
                        onClick = {
                            val id = selected ?: return@Button
                            busy = true
                            notice = null
                            scope.launch {
                                runCatching { repository.editFavoriteFolder(type = "del", folderId = id) }
                                    .onSuccess {
                                        notice = I18n.tr("已删除收藏夹")
                                        selected = null
                                        load(1)
                                    }
                                    .onFailure {
                                        if (it is CancellationException) return@onFailure
                                        notice = I18n.tr("删除失败：") + it.message
                                        Log.error("收藏", "删除收藏夹失败", it)
                                    }
                                busy = false
                            }
                        },
                    ) { Text(tl("删除")) }
                }
            }
        }

        if (items.isEmpty() && !busy) {
            MessageState(
                title = status,
                modifier = Modifier.fillMaxWidth().weight(1f),
                onRetry = if (loggedIn) ({ load(1) }) else null,
            )
        } else {
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
                                runCatching { repository.toggleFavorite(item.id) }
                                    .onSuccess {
                                        items = items.filterNot { it.id == item.id }
                                        total = (total - 1).coerceAtLeast(0)
                                        Log.line("收藏", "已取消收藏：" + (item.name ?: item.id))
                                    }
                                    .onFailure {
                                        if (it is CancellationException) return@onFailure
                                        notice = I18n.tr("取消收藏失败：") + it.message
                                        Log.error("收藏", "取消收藏失败", it)
                                    }
                                busy = false
                            }
                        },
                    ) { Text(tl("取消收藏"), style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        }
    }
}
