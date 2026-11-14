package com.jmnext.desktop
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.data.remote.dto.ListItem
import com.jmnext.desktop.i18n.I18n
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

/**
 * 搜索页（桌面端）。
 *
 * 与 Android 端一致的约定（`SearchFilters.kt` 与 `AppPrefs` 是权威来源，这里照抄）：
 *  1. 结果带 redirect_aid 表示「按作品编号精确命中」，直接打开详情而不是列列表；
 *  2. 被屏蔽规则挡掉的条数要在顶部明示（BlockedNotice），不能静默少几条；
 *  3. 排序 5 档：`""` 最新、`mv` 最多点阅、`mp` 最多图片、`tf` 最多爱心、`old` 最旧；
 *     检索字段 5 档：`site` 站内、`work` 作品、`author` 作者、`tag` 标签、`character` 登场人物；
 *     另有年、月两个可选条件；
 *  4. 「最旧」这一档官方客户端会在**本地按 addDate 二次排序**，这里照做；
 *  5. 搜索历史存本地、最多 20 条、最近的在前、大小写不敏感去重。
 *
 * 桌面端此前只有关键词输入，这些是功能对齐审计里列为"严重"的缺口。
 */
private val SEARCH_ORDERS = listOf(
    "" to "最新",
    "mv" to "最多点阅",
    "mp" to "最多图片",
    "tf" to "最多爱心",
    "old" to "最旧",
)

private val SEARCH_TYPES = listOf(
    "site" to "站内搜索",
    "work" to "作品",
    "author" to "作者",
    "tag" to "标签",
    "character" to "登场人物",
)

/** 搜索历史：语义照 Android 的 AppPrefs.searchHistory（最多 20 条、最近的在前、大小写不敏感去重）。 */
private class SearchHistory(private val prefs: PreferencesKeyValueStore) {
    fun load(): List<String> =
        prefs.getString(KEY, null)?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    fun add(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return load()
        val next = (listOf(q) + load().filterNot { it.equals(q, ignoreCase = true) }).take(LIMIT)
        prefs.putString(KEY, next.joinToString("\n"))
        return next
    }

    fun clear(): List<String> {
        prefs.putString(KEY, "")
        return emptyList()
    }

    private companion object {
        const val KEY = "history"
        const val LIMIT = 20
    }
}

@Composable
fun SearchScreen(
    repository: JmRepository,
    onOpenComic: (ListItem) -> Unit,
) {
    val historyStore = remember { SearchHistory(PreferencesKeyValueStore("jm_search_history")) }
    var history by remember { mutableStateOf(historyStore.load()) }
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    var hidden by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(I18n.tr("输入关键词后回车搜索")) }
    var order by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("site") }
    var year by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    // 标签级屏蔽的命中集合（未初始化时为 null，此时不做任何过滤）
    val hiddenFlow = TagBlocker.hidden
    val hiddenIds by (hiddenFlow?.collectAsState() ?: remember { mutableStateOf<Set<String>>(emptySet()) })

    // 未搜索时的建议：热门标签 + 随机推荐（照 Android：这两个只影响"没搜索时"那一屏，失败就留空）
    var hotTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var recommend by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    var suggestBusy by remember { mutableStateOf(false) }

    fun reloadSuggest() {
        suggestBusy = true
        scope.launch {
            runCatching { repository.hotTags() }
                .onSuccess { hotTags = it }
                .onFailure { Log.line("搜索", "热门标签读取失败（留空继续）：${it.message}") }
            runCatching { repository.randomRecommend() }
                .onSuccess { recommend = it }
                .onFailure { Log.line("搜索", "随机推荐读取失败（留空继续）：${it.message}") }
            suggestBusy = false
        }
    }

    fun runSearch(nextPage: Int) {
        if (query.isBlank()) return
        // 记历史只在真的发起搜索时做（翻页不重复记）
        if (nextPage == 1) history = historyStore.add(query)
        busy = true
        scope.launch {
            runCatching {
                repository.search(
                    query = query.trim(),
                    page = nextPage,
                    order = order.ifEmpty { null },
                    type = type.ifEmpty { null },
                    year = year.takeIf { it.isNotEmpty() },
                    month = month.takeIf { it.isNotEmpty() },
                )
            }
                .onSuccess { result ->
                    // 精确命中作品编号：直接进详情
                    val redirect = result.redirectAid
                    if (nextPage == 1 && !redirect.isNullOrBlank()) {
                        Log.line("搜索", "精确命中作品 id=$redirect，直接打开详情")
                        onOpenComic(ListItem(id = redirect, name = query.trim()))
                        busy = false
                        return@launch
                    }
                    var list = if (nextPage == 1) result.page.items else (items + result.page.items).distinctBy { it.id }
                    // 「最旧」在本地按 addDate 二次排序（照 Android）
                    if (order == "old") {
                        list = list.sortedBy { it.addDate.orEmpty() }
                    }
                    items = list
                    // 把结果交给标签屏蔽器补标签（只有存在标签规则时才真正发请求）
                    list.forEach { TagBlocker.request(it.id) }
                    hidden += result.page.hidden
                    total = result.page.total
                    page = nextPage
                    status = I18n.trf("共 %1\$d 条，已加载 %2\$d 条", total, items.size)
                    Log.line(
                        "搜索",
                        "$status（本页屏蔽 ${result.page.hidden} 条；排序=$order 字段=$type 年=$year 月=$month" +
                            if (order == "old") "，已本地二次排序）" else "）",
                    )
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = I18n.tr("搜索失败：") + it.message
                    Log.error("搜索", status, it)
                }
            busy = false
        }
    }

    LaunchedEffect(Unit) { reloadSuggest() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(tl("搜索作品")) },
                singleLine = true,
                modifier = Modifier.width(360.dp).onKeyEvent { e ->
                    when {
                        e.key == Key.Enter -> { runSearch(1); true }
                        // Esc：清空并收起结果，回到"没搜索"那一屏 —— 与 Enter 配套的取消动作
                        e.key == Key.Escape -> {
                            query = ""
                            items = emptyList()
                            status = I18n.tr("输入关键词后回车搜索")
                            true
                        }
                        else -> false
                    }
                },
            )
            Button(enabled = !busy && query.isNotBlank(), onClick = { runSearch(1) }) {
                Text(if (busy) tl("搜索中…") else tl("搜索"))
            }
StatusLine(status, busy, style = MaterialTheme.typography.labelSmall, color = Color.Unspecified)
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(tl("排序："), style = MaterialTheme.typography.labelMedium)
            SEARCH_ORDERS.forEach { (key, label) ->
                TextButton(enabled = !busy, onClick = { order = key }) {
                    Text((if (order == key) "· " else "") + tl(label))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(tl("字段："), style = MaterialTheme.typography.labelMedium)
            SEARCH_TYPES.forEach { (key, label) ->
                TextButton(enabled = !busy, onClick = { type = key }) {
                    Text((if (type == key) "· " else "") + tl(label))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(tl("年月："), style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = year,
                onValueChange = { input -> year = input.filter { it.isDigit() }.take(4) },
                label = { Text(tl("年（如 2026）")) },
                singleLine = true,
                modifier = Modifier.width(150.dp),
            )
            OutlinedTextField(
                value = month,
                onValueChange = { input -> month = input.filter { it.isDigit() }.take(2) },
                label = { Text(tl("月（1-12）")) },
                singleLine = true,
                modifier = Modifier.width(130.dp),
            )
            TextButton(enabled = !busy, onClick = { year = ""; month = "" }) { Text(tl("不限")) }
        }

        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(tl("搜索历史："), style = MaterialTheme.typography.labelMedium)
                history.forEach { h ->
                    TextButton(enabled = !busy, onClick = { query = h; runSearch(1) }) { Text(h) }
                }
                TextButton(enabled = !busy, onClick = { history = historyStore.clear() }) { Text(tl("清空")) }
            }
        }

        if (items.isEmpty()) {
            if (hotTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(tl("热门标签："), style = MaterialTheme.typography.labelMedium)
                    // 顺序由服务端给，不自行排序（照 Android）
                    hotTags.take(12).forEach { tag ->
                        TextButton(enabled = !busy, onClick = { query = tag; type = "tag"; runSearch(1) }) { Text(tag) }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(tl("随机推荐："), style = MaterialTheme.typography.labelMedium)
                recommend.take(6).forEach { item ->
                    // 用标题按钮而不是封面：ComicCover 是给网格用的，塞进行内排版会变形
                    TextButton(enabled = !busy, onClick = { onOpenComic(item) }) {
                        Text(item.name.orEmpty().take(12))
                    }
                }
                TextButton(enabled = !suggestBusy, onClick = { reloadSuggest() }) { Text(tl("换一批")) }
            }
        }

        BlockedNotice(hidden)

        // 按标签屏蔽过滤（与 Android 同样：被挡的条数要明示，并提供「允许一次」）
        val blockedItems = items.filter { it.id in hiddenIds }
        val visibleItems = items.filterNot { it.id in hiddenIds }
        if (blockedItems.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val tags = blockedItems.flatMap { TagBlocker.blockedTagsOf(it.id) }.distinct().take(4)
                Text(
                    I18n.trf("已按标签屏蔽 %1\$d 条（命中：%2\$s）", blockedItems.size, tags.joinToString("、")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { TagBlocker.allowOnce(blockedItems.map { it.id }.toSet()) }) { Text(tl("允许一次")) }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(visibleItems, key = { it.id }) { item ->
                ComicCover(repository, item, modifier = Modifier.animateItem()) { onOpenComic(item) }
            }
            if (items.isNotEmpty() && items.size < total) {
                item {
                    Button(onClick = { runSearch(page + 1) }, enabled = !busy) { Text(tl("加载更多")) }
                }
            }
        }
    }
}
