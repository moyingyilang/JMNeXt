package com.jmnext.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jmnext.data.FavoriteTags
import com.jmnext.data.JmRepository
import com.jmnext.data.RandomRanking
import com.jmnext.data.remote.dto.ListItem
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 随机本子（桌面端，1.9.x）。
 *
 * 布局约定（与历史/追更一致，见 HistoryScreen 注释）：
 *   顶层 Column(fillMaxSize) → 顶部条 Row(fillMaxWidth) → 列表 weight(1f)
 * 这条约定是为了根治"数据到了但列表不画"——它编译不报、只有跑起来才看得见。
 *
 * **按收藏偏好排序**（Android 端有）做成**开关**，默认关：开启后每取一批要多 N 次
 * 详情请求（N = 这一批的条数），代价写在界面上，由用户决定。
 *
 * 实现要点（读共享层源码确认的）：
 *  - `RandomRanking.rank` 的 `tagsOf` 是**同步 lambda**，不能在里面发请求；
 *    所以顺序是"先并发把这一批的标签取进 Map，再把 { id -> map[id] } 传给 rank"。
 *  - 收藏标签统计用**本地缓存**，没有才扫一次（Android 还判断过期，桌面端暂未做，
 *    这一点如实标注：首次开启较慢，之后走缓存）。
 *  - 取标签设并发上限（这里按 3 条一批），避免把接口打爆。
 */
@Composable
fun RandomScreen(repository: JmRepository, onOpenComic: (ListItem) -> Unit) {
    val scope = rememberCoroutineScope()
    val tagStore = remember { FavoriteTags(PreferencesKeyValueStore("jm_favorite_tags")) }
    // 版式（网格 / 列表）：选择要记住，与 Android 的 AppPrefs.randomLayout 对应
    val prefs = remember { PreferencesKeyValueStore("jm_prefs") }
    var layout by remember { mutableStateOf(prefs.getString(PREF_RANDOM_LAYOUT, LAYOUT_GRID) ?: LAYOUT_GRID) }

    var items by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    // 标签级屏蔽：命中集合 + 过滤 + 把列表交给屏蔽器补标签（放在 items 声明之后）
    val hiddenIds = rememberHiddenTagIds()
    val (blockedByTag, visibleItems) = splitBlockedByTag(items, hiddenIds) { it.id }
    LaunchedEffect(items.map { it.id }) { items.forEach { TagBlocker.request(it.id) } }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在取一批随机作品…") }
    var ranked by remember { mutableStateOf(false) }

    fun roll() {
        busy = true
        scope.launch {
            runCatching {
                val batch = repository.randomRecommend()
                if (!ranked) return@runCatching batch

                // 偏好排序：先备好收藏标签权重。
                // 过期判断照 Android 的 FavoriteTags.isFresh：7 天内直接用缓存，
                // 过期或从未扫过才重扫。它的源码注释专门警告过语义 ——
                // "刚好到期"按时效处理，判断写反了会变成永远不刷新（功能看着正常、数据永远旧）。
                var counts = tagStore.cached()
                if (!FavoriteTags.isFresh(tagStore.cachedAt(), System.currentTimeMillis())) {
                    status = "收藏标签统计已过期（或从未扫描），正在重新扫描…"
                    counts = runCatching { tagStore.refresh(repository) }.getOrDefault(counts)
                }
                if (counts.isEmpty()) return@runCatching batch

                // 再并发取这一批的标签（每批 3 条，人为限流），存进 Map
                val tagsOf = mutableMapOf<String, Set<String>>()
                batch.chunked(3).forEach { chunk ->
                    coroutineScope {
                        chunk.map { item ->
                            async { item.id to runCatching { repository.album(item.id).tags.toSet() }.getOrNull() }
                        }.awaitAll()
                    }.forEach { (id, tags) -> if (tags != null) tagsOf[id] = tags }
                }
                Log.line("随机", "偏好排序：取得 ${tagsOf.size}/${batch.size} 部作品的标签（权重 ${counts.size} 个）")
                // isBlocked 一律返回 false：屏蔽已在数据层过滤，这里不重复过滤
                RandomRanking.rank(batch, { tagsOf[it.id] }, counts) { false }
            }
                .onSuccess {
                    items = it
                    status = if (ranked) "这一批 ${it.size} 条（已按收藏偏好排序）" else "这一批 ${it.size} 条"
                    Log.line("随机", status)
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = "加载失败：${it.message}"
                    Log.error("随机", "加载失败", it)
                }
            busy = false
        }
    }

    LaunchedEffect(Unit) { roll() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tl("随机本子"), style = MaterialTheme.typography.titleLarge)
StatusLine(status, busy)
            Button(enabled = !busy, onClick = { roll() }) { Text(if (busy) tl("取中…") else tl("换一批")) }
            // 版式切换：按钮文字写"切过去会变成什么"（照 Android 的按钮语义）
            TextButton(
                onClick = {
                    layout = if (layout == LAYOUT_GRID) LAYOUT_LIST else LAYOUT_GRID
                    prefs.putString(PREF_RANDOM_LAYOUT, layout)
                    Log.line("随机", "版式切换为：$layout")
                },
            ) { Text(if (layout == LAYOUT_GRID) tl("切换成列表") else tl("切换成网格"), style = MaterialTheme.typography.labelSmall) }
            TextButton(
                enabled = !busy,
                onClick = { ranked = !ranked; roll() },
            ) { Text(if (ranked) tl("· 按收藏偏好排序") else tl("按收藏偏好排序"), style = MaterialTheme.typography.labelSmall) }
        }
        Text(
            tl("开启偏好排序后，取一批会额外请求这一批作品的详情以获取标签（每批约 20 到 30 次），首次还可能要扫描一次收藏标签。"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )

        // 版式切换（缺口：此前只有网格）。选择写进 prefs，下次进来还是上次那一档。
        if (layout == LAYOUT_LIST) {
        BlockedByTagBanner(blockedByTag) { it.id }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibleItems, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenComic(item) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val coverUrl = remember(item.id) { runCatching { repository.coverUrl(item) }.getOrNull() }
                        val bmp = rememberRemoteImage(coverUrl)
                        Box(
                            modifier = Modifier.width(84.dp).aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            if (bmp != null) {
                                Image(bmp, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.name ?: item.id, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            Text(
                                listOfNotNull(item.author, item.category?.title, item.categorySub?.title)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(visibleItems, key = { it.id }) { item -> ComicCover(repository, item, modifier = Modifier.animateItem()) { onOpenComic(item) } }
            }
        }
    }
}

/** 随机页版式（与 Android 的 AppPrefs.randomLayout 同义：存在 prefs 里的一档字符串）。 */
private const val LAYOUT_GRID = "grid"
private const val LAYOUT_LIST = "list"

/** prefs 键名照抄 Android 的 KEY_RANDOM_LAYOUT = "random_layout"（存储节点各自独立）。 */
private const val PREF_RANDOM_LAYOUT = "random_layout"
