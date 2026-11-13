package com.jmnext.ui.screens.more

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.jmnext.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jmnext.ui.ComicTarget
import com.jmnext.ui.jmComicSharedKey
import com.jmnext.data.JmRepository
import com.jmnext.data.remote.dto.ListItem
import com.jmnext.data.remote.dto.PagedList
import com.jmnext.ui.LocalRepository
import com.jmnext.ui.components.CardSizes
import com.jmnext.ui.components.ComicCard
import com.jmnext.ui.components.ErrorBox
import com.jmnext.ui.components.GlassTopBar
import com.jmnext.ui.components.LoadMoreFooter
import com.jmnext.ui.components.LoadingBox
import com.jmnext.ui.components.MessageState
import com.jmnext.ui.theme.JmTheme
import com.jmnext.ui.theme.Spacing
import com.jmnext.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/** 连载更新表 `type` 的三个档位（官方 `ComicType`）。 */
enum class WeeklyType(val key: String, @StringRes val labelRes: Int) {
    ALL(JmRepository.WEEKLY_TYPE_ALL, com.jmnext.R.string.week_type_all),
    MANGA(JmRepository.WEEKLY_TYPE_MANGA, com.jmnext.R.string.week_type_manga),
    HANMAN(JmRepository.WEEKLY_TYPE_HANMAN, com.jmnext.R.string.week_type_hanman),
}

/**
 * 连载更新的日期档位。
 *
 * [key] 就是接口的 `date` 参数：**1..7 是周一..周日，0 是完结**。
 * 这个映射来自官方 `getWeekInfo`（把 JS 的「周日=0」换成「周一=1」，
 * 第 8 个标签「完结」记 0），不能按直觉从 0 起数。
 */
enum class WeeklyDay(val key: Int, @StringRes val labelRes: Int) {
    MON(1, com.jmnext.R.string.week_day_mon),
    TUE(2, com.jmnext.R.string.week_day_tue),
    WED(3, com.jmnext.R.string.week_day_wed),
    THU(4, com.jmnext.R.string.week_day_thu),
    FRI(5, com.jmnext.R.string.week_day_fri),
    SAT(6, com.jmnext.R.string.week_day_sat),
    SUN(7, com.jmnext.R.string.week_day_sun),
    DONE(0, com.jmnext.R.string.week_day_done),
    ;

    companion object {
        /** 今天是星期几对应的档位（周一..周日）。 */
        fun today(): WeeklyDay {
            val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val date = if (dow == Calendar.SUNDAY) 7 else dow - 1
            return entries.first { it.key == date }
        }
    }
}

data class MoreListUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<ListItem> = emptyList(),
    /** 总条数；0 表示服务端没给（连载更新表就没有）。 */
    val total: Int = 0,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null,
    val exhausted: Boolean = false,
    /** 是否按「每周更新表」渲染。由分区 id 决定，构造后不再变化。 */
    val weekly: Boolean = false,
    val type: WeeklyType = WeeklyType.ALL,
    val day: WeeklyDay = WeeklyDay.today(),
)

/**
 * 「分区更多」与「连载更新」的共用列表页。
 *
 * 两者看起来都是「一屏长列表」，但接口语义不同，且**页码约定相反**：
 *  - 普通分区：`promote_list?id=&page=`，**page 从 0 起**，有 `total`
 *  - 连载更新：`serialization?type=&date=&page=`，**page 从 1 起**，没有 `total`，
 *    翻到末页返回 `{"error":"没有资料"}`（一个空 list），因此只能靠「本页为空」判断到底
 */
class MoreListViewModel(
    private val repo: JmRepository,
    private val sectionId: String,
) : ViewModel() {

    private val weekly = sectionId == JmRepository.WEEKLY_SECTION_ID

    private val _state = MutableStateFlow(MoreListUiState(weekly = weekly))
    val state: StateFlow<MoreListUiState> = _state.asStateFlow()

    /** 页码。约定不同，所以起值也不同（见类注释）。 */
    private var page = if (weekly) 1 else 0

    /**
     * 每次「重新加载」自增的世代号。
     *
     * 换档位/换星期会重新拉第一页，而此时可能还有一个在飞的「加载更多」请求；
     * 它回来时若照旧把结果并进去，就会把上一个档位的条目混进新列表 ——
     * 表现是「切到周二，列表里却混着周一的漫画」。
     */
    private var generation = 0

    init {
        load()
    }

    fun load() {
        generation++
        val gen = generation
        page = if (weekly) 1 else 0
        _state.update {
            it.copy(loading = true, error = null, loadMoreError = null, exhausted = false)
        }
        viewModelScope.launch {
            val result = runCatching {
                repo.bootstrap()
                fetch(page)
            }
            if (gen != generation) return@launch
            _state.update {
                it.copy(
                    loading = false,
                    items = result.getOrNull()?.items.orEmpty(),
                    total = result.getOrNull()?.total ?: 0,
                    error = result.exceptionOrNull().toUserMessage(),
                    exhausted = result.getOrNull()?.let { p -> p.total > 0 && p.items.size >= p.total } == true,
                )
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.items.isEmpty()) return
        if (s.exhausted || s.loadMoreError != null) return

        val gen = generation
        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            val next = page + 1
            val result = runCatching { fetch(next) }
            if (gen != generation) return@launch
            _state.update { prev ->
                val more = result.getOrNull()?.items.orEmpty()
                // 只有成功且非空才推进页码：空页是终点，推进它只会重复请求同一个空页
                if (result.isSuccess && more.isNotEmpty()) page = next
                prev.copy(
                    loadingMore = false,
                    items = if (result.isSuccess) prev.items + more else prev.items,
                    total = result.getOrNull()?.total?.takeIf { it > 0 } ?: prev.total,
                    loadMoreError = if (result.isSuccess) null else result.exceptionOrNull().toUserMessage(),
                    exhausted = result.isSuccess && more.isEmpty(),
                )
            }
        }
    }

    /** 续加失败后的重试：先清错误，否则 [loadMore] 会立刻早退。 */
    fun retryLoadMore() {
        _state.update { it.copy(loadMoreError = null) }
        loadMore()
    }

    fun setType(type: WeeklyType) {
        if (_state.value.type == type) return
        _state.update { it.copy(type = type) }
        load()
    }

    fun setDay(day: WeeklyDay) {
        if (_state.value.day == day) return
        _state.update { it.copy(day = day) }
        load()
    }

    private suspend fun fetch(page: Int): PagedList {
        val s = _state.value
        return if (s.weekly) {
            repo.weeklyUpdate(type = s.type.key, date = s.day.key, page = page)
        } else {
            repo.promoteList(id = sectionId, page = page)
        }
    }
}

/**
 * 某个推荐分区的完整列表（首页每个分区的「更多」）。
 *
 * 连载更新那一块（分区 id 26）改用「每周更新表」：它不是一条普通列表，
 * 而是按 **星期** 与 **作品类型** 两个维度切分的日更表 —— 官方也正是这么处理的
 * （`Comic.tsx` 里 `queryId === "26"` 时切到 `serialization` 接口）。
 */
@Composable
fun MoreListScreen(
    sectionId: String,
    title: String,
    onBack: () -> Unit,
    onOpenComic: (ComicTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val vm: MoreListViewModel = viewModel(
        key = "more-$sectionId",
        factory = viewModelFactory { initializer { MoreListViewModel(repo, sectionId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val c = JmTheme.colors

    Column(modifier = modifier.fillMaxSize()) {
        GlassTopBar(
            title = title.ifBlank { stringResource(R.string.more_title) },
            subtitle = when {
                state.loading -> stringResource(R.string.more_loading)
                state.total > 0 -> stringResource(R.string.more_total_fmt, state.total)
                else -> null
            },
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.more_back),
                        tint = c.accent,
                    )
                }
            },
        )

        if (state.weekly) {
            WeeklyFilterBar(
                type = state.type,
                day = state.day,
                onType = { vm.setType(it) },
                onDay = { vm.setDay(it) },
            )
        }

        when {
            state.loading && state.items.isEmpty() -> LoadingBox()

            state.error != null && state.items.isEmpty() -> ErrorBox(
                message = state.error.orEmpty(),
                onRetry = { vm.load() },
            )

            state.items.isEmpty() -> MessageState(
                title = if (state.weekly) "这天的更新是空的" else "这个分区暂时没有内容",
                onRetry = { vm.load() },
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = CardSizes.grid),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.items, key = { it.id }) { comic ->
                    val cover = repo.coverUrl(comic)
                    ComicCard(
                        item = comic,
                        coverUrl = cover,
                        // 封面与标题随路由带给详情页：共享元素的第一帧目标矩形
                        onClick = { onOpenComic(ComicTarget(comic.id, cover, comic.name.orEmpty())) },
                        sharedKey = jmComicSharedKey(comic.id),
                        width = CardSizes.grid,
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    LoadMoreFooter(
                        loading = state.loadingMore,
                        error = state.loadMoreError,
                        exhausted = state.exhausted,
                        onLoadMore = { vm.loadMore() },
                        onRetry = { vm.retryLoadMore() },
                    )
                }
            }
        }
    }
}

/** 连载更新的两个筛选维度：作品类型 / 星期。 */
@Composable
private fun WeeklyFilterBar(
    type: WeeklyType,
    day: WeeklyDay,
    onType: (WeeklyType) -> Unit,
    onDay: (WeeklyDay) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ChipRow(
            label = "类型",
            options = WeeklyType.entries.map { it to stringResource(it.labelRes) },
            selected = type,
            onSelect = onType,
        )
        ChipRow(
            label = "更新",
            options = WeeklyDay.entries.map { it to stringResource(it.labelRes) },
            selected = day,
            onSelect = onDay,
        )
    }
}

@Composable
private fun <T> ChipRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = JmTheme.colors.textTertiary,
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.sm),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            items(options) { (value, text) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { if (selected != value) onSelect(value) },
                    label = { Text(text, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}
