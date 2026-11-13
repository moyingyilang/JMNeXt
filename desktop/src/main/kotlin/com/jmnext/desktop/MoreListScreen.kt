package com.jmnext.desktop

import com.jmnext.desktop.i18n.I18n
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
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

/**
 * 首页某个推荐分区的「更多」页（桌面端）。
 *
 * 对应 Android 的 `ui/screens/more/MoreListScreen.kt`：首页分区标题旁的「更多」
 * 点进来就是该分区的完整列表，走 `promoteList(id, page)` 分页。
 *
 * 与 Android 一致的两条约定（这里照抄）：
 *  1. `promoteList` 的 page 从 0 起算（与 latest 一致，服务端约定），
 *     所以「刷新」重新拉第 0 页、「加载更多」拉 page + 1；
 *  2. 被屏蔽规则挡掉的条数由 `PagedList.hidden` 带上来，必须显示（BlockedNotice），
 *     否则用户只会觉得「结果比预期的少」。
 *
 * 连载更新表（分区 id 26）在 Android 端由同一个页面切到「每周更新表」，桌面端对应
 * [WeeklyUpdateScreen]（入口在首页那个分区的「更多」按钮里）。
 */
@Composable
fun MoreListScreen(
    repository: JmRepository,
    sectionId: String,
    title: String,
    onOpenComic: (ListItem) -> Unit,
) {
    var items by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    // 标签级屏蔽：命中集合 + 过滤 + 把列表交给屏蔽器补标签（放在 items 声明之后）
    val hiddenIds = rememberHiddenTagIds()
    val (blockedByTag, visibleItems) = splitBlockedByTag(items, hiddenIds) { it.id }
    LaunchedEffect(items.map { it.id }) { items.forEach { TagBlocker.request(it.id) } }
    var hidden by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    // promoteList 的 page 是 0 起算（见函数注释第 1 条）
    var page by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var exhausted by remember { mutableStateOf(false) }
    val shown = title.ifBlank { sectionId }
    var status by remember { mutableStateOf(I18n.trf("正在加载「%1\$s」…", shown)) }
    val scope = rememberCoroutineScope()

    fun load(next: Int) {
        busy = true
        scope.launch {
            runCatching { repository.promoteList(id = sectionId, page = next) }
                .onSuccess { paged ->
                    items = if (next == 0) paged.items else (items + paged.items).distinctBy { it.id }
                    hidden = if (next == 0) paged.hidden else hidden + paged.hidden
                    total = paged.total
                    page = next
                    // 空页即到底：接口给了 total 就以 total 为准，没给就靠空页判断
                    exhausted = paged.items.isEmpty()
                    status = (if (total > 0) {
                        I18n.trf("共 %1\$d 条，已加载 %2\$d 条", total, items.size)
                    } else {
                        I18n.trf("已加载 %1\$d 条", items.size)
                    }) + if (hidden > 0) I18n.trf("（屏蔽规则挡掉 %1\$d 条）", hidden) else ""
                    Log.line("分区更多", "「$shown」(id=$sectionId) 第 $next 页：$status")
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = I18n.tr("加载失败：") + it.message
                    Log.error("分区更多", "「$shown」(id=$sectionId) 第 $next 页", it)
                }
            busy = false
        }
    }

    // 换分区（sectionId 变）重新拉第一页；本页通常一个分区一个实例，这一条是保险
    LaunchedEffect(sectionId) { load(0) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = tl(title.ifBlank { "分区更多" }),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(enabled = !busy, onClick = { load(0) }) {
                    Text(if (busy) tl("加载中…") else tl("刷新"))
                }
                // total 给了就按 total 判到底，没给就等一次空页（exhausted）
                val moreLeft = total <= 0 || items.size < total
                Button(
                    enabled = !busy && items.isNotEmpty() && !exhausted && moreLeft,
                    onClick = { load(page + 1) },
                ) { Text(tl("加载更多")) }
StatusLine(status, busy, style = MaterialTheme.typography.labelSmall, color = Color.Unspecified)
            }
        }

        BlockedNotice(hidden)

        BlockedByTagBanner(blockedByTag) { it.id }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(visibleItems, key = { it.id }) { item ->
                ComicCover(repository, item, modifier = Modifier.animateItem()) { onOpenComic(item) }
            }
        }
    }
}

/** 连载更新表的星期档位：1..7 是周一..周日，0 是完结（见 `JmRepository.weeklyUpdate` 的注释）。 */
private val WEEKLY_DAYS = listOf(
    1 to "周一",
    2 to "周二",
    3 to "周三",
    4 to "周四",
    5 to "周五",
    6 to "周六",
    7 to "周日",
    0 to "完结",
)

/** 连载更新表的作品类型档位（官方 `ComicType`，取共享层的常量）。 */
private val WEEKLY_TYPES = listOf(
    JmRepository.WEEKLY_TYPE_ALL to "全部",
    JmRepository.WEEKLY_TYPE_MANGA to "漫画",
    JmRepository.WEEKLY_TYPE_HANMAN to "韩漫",
)

/**
 * 今天是星期几（周一 = 1 .. 周日 = 7）。
 *
 * 不能直接拿 `Calendar.DAY_OF_WEEK`：它是「周日 = 1」，与服务端约定的
 * 「周一 = 1」正好错开一天（照 Android `WeeklyDay.today()` 的换算）。
 */
private fun todayWeekDay(): Int {
    val dow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
    return if (dow == java.util.Calendar.SUNDAY) 7 else dow - 1
}

/**
 * 连载更新表（桌面端，首页「连载更新」分区即 id 26 的「更多」）。
 *
 * 对应 Android `MoreListViewModel`：分区 id 等于 `WEEKLY_SECTION_ID` 时，
 * 它不是一个普通分区，而是按 **作品类型** 与 **星期** 两个维度切分的日更表，
 * 走 `weeklyUpdate(type, date, page)`（官方 `serialization` 接口）。
 *
 * 与 `promoteList` 相反的两条约定（这里照抄共享层的注释）：
 *  1. page **从 1 起算**；
 *  2. 接口不给 total，`PagedList.total` 恒为 0 —— 只能靠「本页为空」判断到底。
 *
 * 注：任务描述里提到用 `weekIssues()` / `weekList(issueId, type, page)`，那套接口是
 * **周刊**（按刊期 + 类型），桌面端早已有 [WeekScreen]；而「类型 + 星期筛选」这张表
 * 官方走 `serialization`，故此处用 `weeklyUpdate`（Android 端也是这么分的）。
 */
@Composable
fun WeeklyUpdateScreen(repository: JmRepository, onOpenComic: (ListItem) -> Unit) {
    var items by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    // 标签级屏蔽：命中集合 + 过滤 + 把列表交给屏蔽器补标签（放在 items 声明之后）
    val hiddenIds = rememberHiddenTagIds()
    val (blockedByTag, visibleItems) = splitBlockedByTag(items, hiddenIds) { it.id }
    LaunchedEffect(items.map { it.id }) { items.forEach { TagBlocker.request(it.id) } }
    // serialization 的 page 从 1 起算（与 promoteList 相反）
    var page by remember { mutableStateOf(1) }
    var type by remember { mutableStateOf(JmRepository.WEEKLY_TYPE_ALL) }
    var day by remember { mutableStateOf(todayWeekDay()) }
    var busy by remember { mutableStateOf(false) }
    var exhausted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(I18n.tr("正在加载连载更新…")) }
    val scope = rememberCoroutineScope()

    fun load(next: Int, t: String = type, d: Int = day) {
        busy = true
        scope.launch {
            runCatching { repository.weeklyUpdate(type = t, date = d, page = next) }
                .onSuccess { paged ->
                    items = if (next == 1) paged.items else (items + paged.items).distinctBy { it.id }
                    page = next
                    // 不给 total：空页就是终点（推进页码只会重复请求空页）
                    exhausted = paged.items.isEmpty()
                    status = I18n.trf("已加载 %1\$d 条", items.size) + if (exhausted && next > 1) I18n.tr("（已到底）") else ""
                    Log.line("连载更新", "$t / date=$d 第 $next 页：$status")
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = I18n.tr("加载失败：") + it.message
                    Log.error("连载更新", "$t / date=$d 第 $next 页", it)
                }
            busy = false
        }
    }

    LaunchedEffect(Unit) { load(1) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(tl("连载更新"), style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(tl("类型"), style = MaterialTheme.typography.labelSmall)
                WEEKLY_TYPES.forEach { (key, label) ->
                    Button(enabled = !busy && key != type, onClick = { type = key; load(1, key, day) }) {
                        Text(tl(label), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(tl("星期"), style = MaterialTheme.typography.labelSmall)
                WEEKLY_DAYS.forEach { (key, label) ->
                    Button(enabled = !busy && key != day, onClick = { day = key; load(1, type, key) }) {
                        Text(tl(label), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(enabled = !busy, onClick = { load(1) }) {
                    Text(if (busy) tl("加载中…") else tl("刷新"))
                }
                Button(
                    enabled = !busy && items.isNotEmpty() && !exhausted,
                    onClick = { load(page + 1) },
                ) { Text(tl("加载更多")) }
StatusLine(status, busy, style = MaterialTheme.typography.labelSmall, color = Color.Unspecified)
            }
        }

        BlockedByTagBanner(blockedByTag) { it.id }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(visibleItems, key = { it.id }) { item ->
                ComicCover(repository, item, modifier = Modifier.animateItem()) { onOpenComic(item) }
            }
        }
    }
}
