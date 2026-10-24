package com.jmnext.desktop
import com.jmnext.selftune.PageSample
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.data.prefs.ReaderMode
import com.jmnext.data.remote.dto.SeriesItem
import com.jmnext.data.prefs.ReadProgressStore
import com.jmnext.data.remote.dto.ReadImage
import com.jmnext.data.remote.dto.ReadPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 阅读页（桌面端，1.9.x 全量移植中）。
 *
 * 形式是**纵向连续滚动**：桌面上鼠标滚轮纵向翻比左右翻自然，也不用处理
 * "一屏放不下整页"的分段逻辑（那在手机上是必需的，在桌面上是负担）。
 *
 * 这一版补上三件"能不能真用起来"的事：
 *  1. 上一话 / 下一话 —— 章节顺序由详情页传入（接口下发的是从旧到新）
 *  2. 单页失败可以点一下重试（原来失败就永远停在加载中）
 *  3. 进入章节时记录进度，详情页据此显示"继续阅读"
 *
 * 反切片：服务端把部分漫画整页切成若干条错位重排，必须在显示前还原。
 * 判定与算法都在共享层（needsUnscramble / ImageUnscramble），这里只管调用的时机与缓存。
 */
@Composable
fun ReaderScreen(
    repository: JmRepository,
    progress: ReadProgressStore,
    comicId: String,
    chapterId: String,
    chapterIds: List<String> = emptyList(),
    onBack: () -> Unit,
    onSwitchChapter: (String) -> Unit = {},
    // 评论入口由 Main 传入（阅读页不持有路由）；收藏与点赞在栏内直接调 repository。
    onOpenComments: (String) -> Unit = {},
) {
    var payload by remember(chapterId) { mutableStateOf<ReadPayload?>(null) }
    // 页级进度：桌面端专用（共享层那份是章节粒度，Android 按那个工作）
    val pageProgress = remember { PageProgress(PreferencesKeyValueStore("jm_read_page")) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // 当前页（0 基）：从列表状态派生，滚动时自动更新，给右侧页码栏用
    val currentPage by androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex }
    // 章节选择对话框的数据与开关（按 A 方案：打开时才请求一次 album 拿 series，含话名）
    var series by remember(comicId) { mutableStateOf<List<SeriesItem>>(emptyList()) }
    var pickerOpen by remember(comicId) { mutableStateOf(false) }
    // 阅读模式：复用共享层的 ReaderMode（Scroll = 纵向连续滚动 / Page = 横向逐页适配整屏）。
    // 默认形态取自设置页（AppearanceScreen 写、这里读，见文件末尾的 ReaderModePref）；
    // 阅读页底栏与右侧栏的切换按钮也写回同一个键。
    var mode by remember { mutableStateOf(ReaderModePref.mode) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var status by remember(chapterId) { mutableStateOf("正在加载章节…") }
    var loading by remember(chapterId) { mutableStateOf(true) }
    var retryToken by remember(chapterId) { mutableStateOf(0) }

    val index = remember(chapterId, chapterIds) { chapterIds.indexOf(chapterId) }
    val prevId = if (index > 0) chapterIds.getOrNull(index - 1) else null
    val nextId = if (index >= 0 && index < chapterIds.lastIndex) chapterIds.getOrNull(index + 1) else null

    LaunchedEffect(chapterId, retryToken) {
        payload = null
        runCatching { repository.read(chapterId) }
            .onSuccess {
                payload = it
                status = "${it.images.size} 页"
                loading = false
                runCatching { progress.record(comicId, chapterId) }
                System.err.println("[阅读] 已加载 $status（章节 ${index + 1}/${chapterIds.size}）")
            }
            .onFailure {
                if (it is CancellationException) return@onFailure
                status = "加载失败：${it.message}（点“重试”）"
                loading = false
                System.err.println("[阅读] $status")
            }
    }

    // 恢复到上次读到的页：等章节数据到位后再滚（此前只记到章节，重进会从头开始）
    androidx.compose.runtime.LaunchedEffect(payload) {
        if (payload != null) {
            val saved = pageProgress.lastPage(comicId, chapterId)
            if (saved > 0) {
                Log.line("阅读", "恢复到第 ${saved + 1} 页")
                listState.scrollToItem(saved)
            }
        }
    }

    // 滚动时记页（只在页码变化时写，避免每帧都落盘）
    androidx.compose.runtime.LaunchedEffect(listState, chapterId) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { pageProgress.record(comicId, chapterId, it) }
    }

    // 章节选择对话框（按 Android 的 ChapterPickerDialog 移植，见该文件注释）
    if (pickerOpen && series.isNotEmpty()) {
            Log.line("阅读", "章节选择：series 到达（${series.size} 话），准备弹对话框")
        ChapterPickerDialog(
            series = series,
            currentChapterId = chapterId,
            onPick = { id ->
                pickerOpen = false
                onSwitchChapter(id)
            },
            onDismiss = { pickerOpen = false },
        )
    }

    Box(
        Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            // 加载中与失败分开：失败显示可复制的错误文案（含"重试"提示），加载中才转圈
            if (loading) {
                LoadingHint("正在加载章节…")
            } else {
                Text(status, style = MaterialTheme.typography.titleMedium)
            }
            if (chapterIds.isNotEmpty() && index >= 0) {
                Text(
                    "第 ${index + 1} / ${chapterIds.size} 话",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (payload == null) {
                TextButton(onClick = { retryToken += 1 }) { Text("重试") }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            }
        }

        val p = payload ?: return@Box



        // 横向翻页用的 pager 状态。放在这里（payload 可用之后）是因为页数取自 p.images.size；
        // 用全限定名调用，避免再动 import。（第 3 步接线时由 PagedReader 使用。）
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = currentPage.coerceIn(0, (p.images.size - 1).coerceAtLeast(0)),
        ) { p.images.size }

        // 键盘翻页用的焦点请求：桌面特有（Android 端只有手势）。
        // 第一步只把焦点基础设施就位；按键处理在下一步加到内容区那一行上。
        val focusRequester = remember { FocusRequester() }
        // 键里带 mode 与 chapterId：切换阅读模式会重建内容区（焦点节点被销毁），
        // 只写 LaunchedEffect(Unit) 的话只在首次组合请求一次，切换后键盘就失效了（对应 issue #2 第 2 条）。
        LaunchedEffect(mode, chapterId) { focusRequester.requestFocus() }
        // 预取当前页之后的若干页图片（下载+解码都提前做完，翻页时直接命中缓存）。
        // 依据（真机实测，2026-10-04）：
        //  - 反切片总耗时里下载占绝大部分：配对 381 样本 → 总 1284ms、下载 1078ms、解码+画band 86ms；
        //  - 用户用键盘快速翻页时约 0.4~0.5 秒/页，而单页下载中位数约 1.1 秒 —— 原来只提前 2 页赶不上，
        //    实测分界后 25 次反切片仍有 14 次走了网络，故提前量加大到 6 页；
        //  - 实测还发现同一页被并发预取多次（第 6 页 3 次）→ 用 inFlight 集合做在途去重。
        // 只做串行预取：按 N+1、N+2…… 的顺序取，最近的页优先备好；并发会让更靠后的页先到，反而没用。
        // 失败不影响阅读：两条加载路径自带兜底与日志，这里只记一行结果便于验证。
        val prefetchInFlight = remember { java.util.Collections.synchronizedSet(mutableSetOf<String>()) }
        // 自学习采样（反馈侧）：进页记时与是否命中预取，离页时结算成样本喂回调参器。
        // 只采样"用户真正停留过的页"；预取动作本身不采样（它没有停留时长，喂进去只会污染信号）。
        // 取消单独统计：图片还没备好就被取消，算"被打断"，不算失败。
        LaunchedEffect(chapterId, currentPage, mode, p.id) {
            val idx = if (mode == ReaderMode.Page) pagerState.currentPage else currentPage
            val img = p.images.getOrNull(idx) ?: return@LaunchedEffect
            val entered = System.currentTimeMillis()
            val hitCache = RemoteImage.cached(img.image) != null
            var ready = hitCache
            var latency = 0L
            try {
                // 等本页图片可用（命中预取时几乎立刻）；最多 15 秒，超时按失败样本记。
                while (RemoteImage.cached(img.image) == null && System.currentTimeMillis() - entered < 15_000L) {
                    kotlinx.coroutines.delay(50)
                }
                ready = RemoteImage.cached(img.image) != null
                latency = System.currentTimeMillis() - entered
                // 停留时长要等离开这一页才知道，所以挂起等到本页结束。
                kotlinx.coroutines.awaitCancellation()
            } finally {
                val dwell = System.currentTimeMillis() - entered
                if (!ready) SelfTuner.onCancellation()
                SelfTuner.onPage(
                    PageSample(
                        latencyMs = if (hitCache) 0L else latency,
                        bytes = RemoteImage.downloadedSize(img.image),
                        hitCache = hitCache,
                        failed = !ready,
                        dwellMs = dwell,
                    ),
                )
            }
        }

        LaunchedEffect(chapterId, currentPage, mode) {
            val start = if (mode == ReaderMode.Page) pagerState.currentPage else currentPage
            scope.launch {
                val depth = SelfTuner.prefetchDepth.coerceAtLeast(1)
                val to = minOf(start + depth, p.images.size - 1)
                Log.line("阅读", "本窗口预取深度 $depth（来自 SelfTuner）")
                var i = start + 1
                while (i <= to) {
                    val img = p.images[i]
                    // 在途去重：只有 add 成功的那个协程负责下载，避免同一页被并发预取多次
                    if (RemoteImage.cached(img.image) == null && prefetchInFlight.add(img.image)) {
                        try {
                            val t0 = System.currentTimeMillis()
                            val need = runCatching { repository.needsUnscramble(img.image, p.id, p.scrambleId) }.getOrDefault(false)
                            val got = if (need) RemoteImage.loadScrambled(img.image, p.id, img.fileNameStem) else RemoteImage.load(img.image)
                            Log.debug("阅读", "预取第 " + (i + 1) + " 页" + (if (got == null) "失败" else "成功") + "（用时 " + (System.currentTimeMillis() - t0) + " ms）")
                        } finally {
                            prefetchInFlight.remove(img.image)
                        }
                    }
                    i++
                }
            }
        }

        // 横向模式也要记页：上面那条 flow 用的是 listState，横向下它不动。
        // 只在 Page 模式写入，避免两种模式互相覆盖对方的位置。
        androidx.compose.runtime.LaunchedEffect(pagerState, chapterId, mode) {
            androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { if (mode == ReaderMode.Page) pageProgress.record(comicId, chapterId, it) }
        }

        // ── 预加载下一话 ──
        // 只预取"下一话的图片列表"（一次 read 请求），**不整话下载图片** ——
        // 整话预下载会把内存与流量放大几十倍，这里刻意不做；代价就是翻到下一话时
        // 首页图片仍要现取，但省掉了一次"章节信息"请求的等待。
        // 触发点：本话读到 60% 之后；进入该章节只跑一次，来回滚动不会反复请求。
        var prefetched by remember(chapterId) { mutableStateOf(false) }
        val prefetchAt = (p.images.size * 6) / 10
        LaunchedEffect(chapterId, currentPage, nextId) {
            if (!prefetched && nextId != null && currentPage >= prefetchAt) {
                prefetched = true
                val t0 = System.currentTimeMillis()
                runCatching { repository.read(nextId) }
                    .onSuccess {
                        Log.line(
                            "阅读",
                            "已预加载下一话信息（用时 ${System.currentTimeMillis() - t0} ms，图片 ${it.images.size} 张）",
                        )
                    }
                    .onFailure {
                        if (it is CancellationException) return@onFailure
                        // 预加载失败不影响当前阅读，只记日志
                        Log.error("阅读", "预加载下一话失败（不影响当前阅读）", it)
                    }
            }
        }
        Row(
            Modifier.fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { ev ->
                    // 键盘翻页（桌面特有；Android 端只有手势）。只认按下，避免长按重复触发两遍。
                    if (ev.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        val step = when (ev.key) {
                            Key.DirectionLeft, Key.PageUp -> -1
                            Key.DirectionRight, Key.PageDown -> 1
                            else -> 0
                        }
                        if (step == 0) {
                            false
                        } else {
                            val total = p.images.size
                            if (mode == ReaderMode.Page) {
                                scope.launch { pagerState.scrollToPage((pagerState.currentPage + step).coerceIn(0, (total - 1).coerceAtLeast(0))) }
                            } else {
                                scope.launch { listState.scrollToItem((currentPage + step).coerceIn(0, (total - 1).coerceAtLeast(0))) }
                            }
                            Log.line("阅读", "键盘翻页 " + (if (step < 0) "上一页" else "下一页"))
                            true
                        }
                    }
                },
        ) {
        // 两种模式共用侧栏：只替换内容区（LazyColumn ↔ PagedReader），PageRail 留在外面
        if (mode == ReaderMode.Page) {
            PagedReader(
                repository = repository,
                payload = p,
                state = pagerState,
                onPageChange = { },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            // 页面之间不留空隙：漫画是连续的，8dp 间距会把跨页画面割断
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(p.images, key = { _, img -> img.image }) { idx, image ->
                PageItem(repository, p, image, idx)
            }
            item {
                // 底部也放一次"下一话"：连续滚动读完一话后，手停在这里
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(enabled = nextId != null, onClick = { nextId?.let(onSwitchChapter) }) {
                        Text(if (nextId != null) "下一话" else "已经是最后一话")
                    }
                    if (nextId != null) {
                        Box(Modifier.padding(start = 12.dp)) {
                            TextButton(onClick = onBack) { Text("回详情页") }
                        }
                    }
                }
            }
        }
        }

        // 右侧竖排页码栏（仿 Android 版）：当前页 / 总页数 + 竖向进度
        // 与 Android 的 PageSeekRow 同一套控件（只竖过来），见 PageRail.kt

        // 底栏（仿 Android 的阅读页底栏；容器见 ReaderBottomBar.kt）。
        // 暂以 visible = true 常显，先确认外观与交互无碍，下一轮再加"鼠标移到底部/点击才出现、几秒隐藏"。
        // 与 Android 的一处差异如实标注：Android 的栏是浮在内容之上，这里是 Column 的最后一个子项
        // （隐藏时占 0dp，观感差别很小）；改成浮层需要把根布局 Column 换成 Box，会连带改动 weight 的
        // 作用域，风险大，故先不做。
        }
        PageRail(
            modeLabel = if (mode == ReaderMode.Page) "横向" else "纵向",
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd).width(40.dp),
            current = if (mode == ReaderMode.Page) pagerState.currentPage else currentPage,
            total = p.images.size,
            onSeek = { page ->
                // 跳页用 scrollToItem：直接定位，不做动画（长列表做动画会又慢又抖）
                Log.line("阅读", "跳页 → 第 ${page + 1} 页")
                scope.launch { if (mode == ReaderMode.Page) pagerState.scrollToPage(page) else listState.scrollToItem(page) }
            },
            hasPrev = prevId != null,
            hasNext = nextId != null,
            onPrev = { prevId?.let(onSwitchChapter) },
            onNext = { nextId?.let(onSwitchChapter) },
            onOpenComments = { onOpenComments(comicId) },
            onToggleMode = {
                // 切换模式前先把位置同步到对方：否则横向翻到第 80 页、切回纵向会回到旧位置
                // （两种模式索引同一张图片列表，语义相同，只是载体不同，所以同步的是同一个下标）
                scope.launch {
                    if (mode == ReaderMode.Page) {
                        listState.scrollToItem(pagerState.currentPage.coerceIn(0, (p.images.size - 1).coerceAtLeast(0)))
                    } else {
                        pagerState.scrollToPage(currentPage.coerceIn(0, (p.images.size - 1).coerceAtLeast(0)))
                    }
                }
                // 与 Android 同一个枚举、同一个语义；桌面端额外把选择存下来
                mode = if (mode == ReaderMode.Scroll) ReaderMode.Page else ReaderMode.Scroll
                ReaderModePref.mode = mode
                Log.line("阅读", "阅读模式切换为 " + (if (mode == ReaderMode.Page) "横向翻页" else "纵向滚动"))
            },
            onOpenPicker = {
                // 章节选择：点一次才请求 album（A 方案），拿到含话名的 series 再开对话框
                scope.launch {
Log.line("阅读", "章节选择：开始请求 album(comicId)…")
                    runCatching { repository.album(comicId) }
                        .onSuccess {
                            series = it.series
                            pickerOpen = true
                        }
                        .onFailure {
                            if (it is CancellationException) return@onFailure
                            Log.error("阅读", "打开章节选择失败", it)
                        }
                }
            },
            onToggleFavorite = {
                scope.launch {
                    runCatching { repository.toggleFavorite(comicId) }
                        .onSuccess { Log.line("阅读", "收藏状态已切换（写操作，未验证）") }
                        .onFailure {
                            if (it is CancellationException) return@onFailure
                            Log.error("阅读", "切换收藏失败", it)
                        }
                }
            },
            onToggleLike = {
                scope.launch {
                    runCatching { repository.like(comicId) }
                        .onSuccess { Log.line("阅读", "已点赞（写操作，未验证）") }
                        .onFailure {
                            if (it is CancellationException) return@onFailure
                            Log.error("阅读", "点赞失败", it)
                        }
                }
            },
        )
        ReaderBottomBar(
            modeLabel = if (mode == ReaderMode.Page) "横向" else "纵向",
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            visible = true,
            current = if (mode == ReaderMode.Page) pagerState.currentPage else currentPage,
            total = p.images.size,
            onSeek = { page ->
                scope.launch {
                    if (mode == ReaderMode.Page) pagerState.scrollToPage(page) else listState.scrollToItem(page)
                }
            },
            hasPrevChapter = prevId != null,
            hasNextChapter = nextId != null,
            onPrevChapter = { prevId?.let(onSwitchChapter) },
            onNextChapter = { nextId?.let(onSwitchChapter) },
            hasPrevPage = (if (mode == ReaderMode.Page) pagerState.currentPage else currentPage) > 0,
            hasNextPage = (if (mode == ReaderMode.Page) pagerState.currentPage else currentPage) < p.images.size - 1,
            onPrevPage = {
                scope.launch {
                    if (mode == ReaderMode.Page) pagerState.scrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    else listState.scrollToItem((currentPage - 1).coerceAtLeast(0))
                }
            },
            onNextPage = {
                scope.launch {
                    if (mode == ReaderMode.Page) pagerState.scrollToPage((pagerState.currentPage + 1).coerceAtMost(p.images.size - 1))
                    else listState.scrollToItem((currentPage + 1).coerceAtMost(p.images.size - 1))
                }
            },
            onToggleMode = {
                scope.launch {
                    if (mode == ReaderMode.Page) listState.scrollToItem(pagerState.currentPage) else pagerState.scrollToPage(currentPage)
                }
                mode = if (mode == ReaderMode.Scroll) ReaderMode.Page else ReaderMode.Scroll
                ReaderModePref.mode = mode
                Log.line("阅读", "阅读模式切换为 " + (if (mode == ReaderMode.Page) "横向翻页" else "纵向滚动"))
            },
            onOpenPicker = {
                scope.launch {
                    runCatching { repository.album(comicId) }
                        .onSuccess { series = it.series; pickerOpen = true }
                        .onFailure {
                            if (it is CancellationException) return@onFailure
                            Log.error("阅读", "打开章节选择失败", it)
                        }
                }
            },
            onOpenComments = { onOpenComments(comicId) },
            onToggleFavorite = { scope.launch { repository.toggleFavorite(comicId); Log.line("阅读", "收藏状态已切换（写操作，未验证）") } },
            onToggleLike = { scope.launch { repository.like(comicId); Log.line("阅读", "已点赞（写操作，未验证）") } },
        )
    }
}

@Composable
internal fun PageItem(repository: JmRepository, payload: ReadPayload, image: ReadImage, index: Int) {
    val url = image.image
    val needsUnscramble = remember(url) {
        runCatching { repository.needsUnscramble(url, payload.id, payload.scrambleId) }.getOrDefault(false)
    }
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(RemoteImage.cached(url)) }
    var failed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableStateOf(0) }

    LaunchedEffect(url, attempt) {
        if (bitmap == null) {
            failed = false
            val loaded = if (needsUnscramble) {
                RemoteImage.loadScrambled(url, payload.id, image.fileNameStem)
            } else {
                RemoteImage.load(url)
            }
            bitmap = loaded
            failed = loaded == null
            if (loaded == null) System.err.println("[阅读] 第 ${index + 1} 页加载失败：$url")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()


            .then(if (bitmap == null) Modifier.height(220.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        when {
            b != null -> Image(
                bitmap = b,
                contentDescription = "第 ${index + 1} 页",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
                // 黑白 / 墨水屏：与 Android 共用同一套颜色矩阵（见 ComicRender.kt）
                colorFilter = comicRenderFilter(ComicRenderPref.mode),
            )

            // 失败可以点一下重试：原来失败就永远停在"加载中"，读者只能退出去再进来
            failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { attempt += 1 }.padding(24.dp),
            ) {
                Text("第 ${index + 1} 页加载失败", style = MaterialTheme.typography.bodyMedium)
                Text("点这里重试", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            else -> Text("第 ${index + 1} 页 加载中…", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * 阅读默认形态（桌面端）。
 *
 * 放在这里而不是设置页，是因为**实际取值在阅读页**：[ReaderScreen] 进入时读它，
 * 设置页（`AppearanceScreen`）只写。两边共用同一个持久化键，所以设置页选完，
 * 下次进阅读页就是所选形态。
 *
 * 对应 Android 的 `AppPrefs.readerMode`（默认 [ReaderMode.Scroll]，见 app 模块
 * `data/prefs/AppPrefs.kt:157`）与「我的」页的 ReadingCard（`ProfileScreen.kt:923`）。
 * 那个 AppPrefs 在 `app` 模块，桌面端看不到，所以这里用桌面自己的 KeyValueStore
 * 存同一个语义；键名沿用阅读页原来私有使用的 `jm_reader_mode` / `mode`，
 * 老用户已有的选择不会丢。
 */
internal object ReaderModePref {
    private val prefs = PreferencesKeyValueStore("jm_reader_mode")

    var mode: ReaderMode
        get() = if (prefs.getString("mode", null) == "page") ReaderMode.Page else ReaderMode.Scroll
        set(value) {
            prefs.putString("mode", if (value == ReaderMode.Page) "page" else "scroll")
        }
}
