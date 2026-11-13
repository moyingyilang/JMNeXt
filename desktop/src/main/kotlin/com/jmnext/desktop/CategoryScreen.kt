package com.jmnext.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jmnext.data.remote.dto.CategoryBlock
import com.jmnext.data.remote.dto.ListItem
import com.jmnext.data.JmRepository
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 分类（2.0.0 桌面端）。
 *
 * 左侧是本子分类树（一个个分类 + 它们的子分类），右侧是筛选结果。
 * 分类树来自 `categories()`，筛选走 `categoryFilter(c = slug)`。
 *
 * 与 Android 端的一致之处：列表过滤仍在数据层做，页面只显示"被挡掉多少条"。
 */
@Composable
fun CategoryScreen(
    repository: JmRepository,
    onOpenComic: (ListItem) -> Unit,
    // 点标签的出口：给了就路由到搜索页（Main.kt 尚未传），没给就在本页结果区就地搜索
    onSearch: ((String) -> Unit)? = null,
) {
    // slug 与显示名；空 slug 表示"全部"
    var nodes by remember { mutableStateOf<List<Triple<String, String, List<Pair<String, String>>>>>(emptyList()) }
    var current by remember { mutableStateOf<String?>(null) }
    // 展开的是哪个父类，与"选中了哪个 slug"分开记。
    // 之前两者共用 current，点二级项时父类不再匹配 → 子项立刻收回（用户报的"直接收回"）。
    var expanded by remember { mutableStateOf<String?>(null) }
    var currentName by remember { mutableStateOf("全部") }
    // 排序档位取自 Android 端 CategorySort：比搜索多出月榜与周榜。
    var sort by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    var hidden by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在取分类…") }
    // 分组标签（`categories` 响应的 blocks）与分类树失败时的热门标签兜底（缺口：此前都没有）
    var blocks by remember { mutableStateOf<List<CategoryBlock>>(emptyList()) }
    var hotTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var treeError by remember { mutableStateOf<String?>(null) }
    // "按标签搜索"状态：标签与页码与分类筛选的 current / page 分开记，避免互相污染
    var tagMode by remember { mutableStateOf<String?>(null) }
    var tagPage by remember { mutableStateOf(1) }
    val scope = rememberCoroutineScope()
    // 标签级屏蔽：命中集合 + 把当前列表交给屏蔽器补标签（只在有规则时发请求）
    val hiddenIds = rememberHiddenTagIds()
    LaunchedEffect(items) { items.forEach { TagBlocker.request(it.id) } }

    fun filter(slug: String?, name: String, next: Int) {
        busy = true
        scope.launch {
            runCatching {
                repository.categoryFilter(c = slug, page = next, order = sort.takeIf { it.isNotEmpty() })
            }
                .onSuccess { paged ->
                    items = if (next == 1) paged.items else (items + paged.items).distinctBy { it.id }
                    hidden += paged.hidden
                    page = next
                    // 走分类筛选就不再是"标签搜索"状态（否则「加载更多」会拿标签去翻分类页）
                    tagMode = null
                    tagPage = 1
                    status = "$name：已加载 ${items.size} 条" +
                        if (paged.hidden > 0) "（本页屏蔽 ${paged.hidden} 条）" else ""
                    System.err.println("[分类] $status")
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = "加载失败：${it.message}"
                    System.err.println("[分类] $status")
                }
            busy = false
        }
    }

    /** 分类树拿不到时的兜底：热门标签（共享层 `hotTags()`，Android 的 TagFallback 用的也是它）。 */
    fun loadHotTags() {
        busy = true
        scope.launch {
            runCatching { repository.hotTags() }
                .onSuccess { list ->
                    hotTags = list
                    status = "用热门标签兜底：${list.size} 个"
                    Log.line("分类", status)
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = "热门标签也拿不到：" + it.message
                    Log.error("分类", "热门标签兜底失败", it)
                }
            busy = false
        }
    }

    /** 拉分类树（含分组标签 blocks）。失败或为空都退回热门标签，保证这一屏不是死的。 */
    fun loadCategories() {
        busy = true
        scope.launch {
            val r = runCatching { repository.categories() }
            if (r.exceptionOrNull() is CancellationException) { busy = false; return@launch }
            val payload = r.getOrNull()
            if (payload != null) {
                nodes = payload.categories.map { node ->
                    Triple(
                        node.slug,
                        node.name ?: node.slug,
                        node.subCategories.map { it.slug to (it.name ?: it.slug) },
                    )
                }
                blocks = payload.blocks
                treeError = null
                status = "分类 ${nodes.size} 个" +
                    if (blocks.isNotEmpty()) "，标签组 ${blocks.size} 组" else ""
                Log.line("分类", status)
            } else {
                nodes = emptyList()
                blocks = emptyList()
                val e = r.exceptionOrNull()
                treeError = e?.message ?: "原因未知"
                status = "分类加载失败：$treeError，改用热门标签兜底"
                Log.line("分类", status)
                if (e != null) Log.error("分类", "分类加载失败", e)
            }
            busy = false
            if (nodes.isEmpty()) loadHotTags() else filter(null, "全部", 1)
        }
    }

    /** 就地按标签搜索：复用右侧结果网格；分页由 tagPage 记，不与分类筛选的 page 混。 */
    fun searchTag(tag: String, next: Int) {
        busy = true
        scope.launch {
            runCatching { repository.search(query = tag, page = next) }
                .onSuccess { result ->
                    items = if (next == 1) result.page.items else (items + result.page.items).distinctBy { it.id }
                    hidden = if (next == 1) result.page.hidden else hidden + result.page.hidden
                    tagMode = tag
                    tagPage = next
                    status = "标签「$tag」：已加载 ${items.size} 条 / 共 ${result.page.total}" +
                        if (result.page.hidden > 0) "（本页屏蔽 ${result.page.hidden} 条）" else ""
                    Log.line("分类", status)
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = "按标签搜索失败：${it.message}"
                    Log.error("分类", "按标签搜索失败 tag=$tag", it)
                }
            busy = false
        }
    }

    /**
     * 点标签 = 按标签搜索。
     *
     * 调用方给了 [onSearch] 就交给它路由（与 TagsScreen 的 onSearch 同路）；没给就**就地搜索**，
     * 结果铺在右侧同一块结果区里（不做"点了没反应"的假按钮）。
     *
     * 注意 Kotlin 的局部函数必须先声明后用，所以 [searchTag] 写在上面。
     */
    fun openTag(tag: String) {
        if (onSearch != null) {
            Log.line("分类", "按标签搜索（交给外层路由）：$tag")
            onSearch(tag)
            return
        }
        current = null
        currentName = "标签：$tag"
        sort = ""
        searchTag(tag, 1)
    }

    LaunchedEffect(Unit) { loadCategories() }

    Row(Modifier.fillMaxWidth()) {
        // 左：分类树
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(tl("分类"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            NavChip(tl("全部"), current == null) { current = null; currentName = "全部"; filter(null, "全部", 1) }
            nodes.forEach { (slug, name, subs) ->
                NavChip(name, current == slug) {
                    current = slug
                    currentName = name
                    expanded = if (expanded == slug) null else slug
                    filter(slug, name, 1)
                }
                if (expanded == slug && subs.isNotEmpty()) {
                    subs.forEach { (subSlug, subName) ->
                        val subActive = current == subSlug
                        Text(
                            text = (if (subActive) "· $subName" else "· $subName"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (subActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 只改选中项，不动 expanded —— 父类保持展开，焦点停在这一项
                                    current = subSlug
                                    currentName = subName
                                    filter(subSlug, subName, 1)
                                }
                                .padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }

        // 右：结果
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(tl(currentName), style = MaterialTheme.typography.titleLarge)
            StatusLine(status, busy)
                // 排序档位：换档后从第一页重新加载
                listOf(
                    "" to tl("最新"),
                    "tf" to tl("最多爱心"),
                    "mv" to tl("总排行"),
                    "mv_m" to tl("月排行"),
                    "mp_w" to tl("周排行"),
                ).forEach { (key, label) ->
                    TextButton(onClick = { sort = key; filter(current, currentName, 1) }) {
                        Text(if (sort == key) "· $label" else label, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // 标签搜索结果的翻页必须走 searchTag —— 走分类筛选会把标签结果换成分类结果
                if (tagMode != null) {
                    TextButton(onClick = {
                        tagMode = null
                        items = emptyList()
                        hidden = 0
                        status = if (nodes.isEmpty()) "热门标签兜底" else "分类 ${nodes.size} 个"
                    }) { Text(if (nodes.isEmpty()) tl("返回热门标签") else tl("返回分类"), style = MaterialTheme.typography.labelSmall) }
                }
                if (items.isNotEmpty()) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            val t = tagMode
                            if (t != null) searchTag(t, tagPage + 1) else filter(current, currentName, page + 1)
                        },
                    ) { Text(tl("加载更多")) }
                }
            }
            BlockedNotice(hidden)
            // 分类树拿不到时的兜底（照 Android 的 TagFallback）：改用热门标签，点标签按标签搜索。
            // 有它这一屏不会是死的；点过标签后换成结果网格，可用「返回热门标签」回来。
            if (nodes.isEmpty() && tagMode == null) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("分类拿不到" + (treeError?.let { "：$it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                    if (hotTags.isEmpty()) {
                        Text(tl("热门标签也拿不到。可以点「重试」再取一次。"), style = MaterialTheme.typography.bodyMedium)
                        Button(enabled = !busy, onClick = { loadCategories() }) { Text(tl("重试")) }
                    } else {
                        Text(tl("热门标签（点标签按标签搜索）"), style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            hotTags.forEach { tag -> TagChip(tag) { openTag(tag) } }
                        }
                    }
                }
            } else {
                val (blockedByTag, visibleItems) = splitBlockedByTag(items, hiddenIds) { it.id }
                BlockedByTagBanner(blockedByTag) { it.id }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(168.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visibleItems, key = { it.id }) { item -> ComicCover(repository, item, modifier = Modifier.animateItem()) { onOpenComic(item) } }
                    // 分组标签（categories 响应的 blocks）铺满整行放在结果之后 —— 照 Android：
                    // 它是「换个方式浏览」的出口，不该抢结果上方的位置，也不该与结果争列宽。
                    if (blocks.isNotEmpty() && tagMode == null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                blocks.forEach { block ->
                                    if (block.content.isNotEmpty()) {
                                        Text(block.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            block.content.forEach { tag -> TagChip(tag) { openTag(tag) } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 标签小片：分组标签与热门标签兜底共用（点它按标签搜索）。 */
@Composable
private fun TagChip(tag: String, onClick: () -> Unit) {
    Text(
        text = "#$tag",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun NavChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}
