package com.jmnext.desktop
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import com.jmnext.data.remote.dto.DailyDay
import com.jmnext.data.remote.dto.NotificationItem

import kotlinx.coroutines.CancellationException

import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jmnext.data.JmRepository
import com.jmnext.data.auth.AuthStore
import com.jmnext.data.auth.SecureStore
import com.jmnext.data.prefs.BlockStore
import com.jmnext.data.prefs.ReadProgressStore
import com.jmnext.data.remote.dto.ListItem
import java.io.File
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog

/**
 * 桌面端第一版界面：首页列表（2.0.0）。
 *
 * 这一版的目的是**把最小闭环跑起来**：引导 → 拉首页 → 显示封面与标题。
 * 详情、阅读、搜索等页面在其后铺开。
 *
 * 数据层与 Android 完全同一份（:shared），这里只提供三个平台实现：
 * 键值存储、密钥来源、界面。
 */
private val readProgress by lazy { ReadProgressStore(PreferencesKeyValueStore("jm_read_progress")) }

private val repository: JmRepository by lazy {
    val configDir = File(System.getProperty("user.home"), ".config/jmnext")
    val secure = SecureStore(
        prefs = PreferencesKeyValueStore("jm_secure"),
        // fallback 传成 lambda（而不是先构造）：issue #16 要求"先判定新装/老装，再创建密钥材料"，
        // 惰性求值从结构上保证这一点。
        keys = PassphraseKeyProvider.maybeEnable(File(configDir, "keys"), { FileKeyProvider(File(configDir, "keys")) }),
    )
    JmRepository.create(
        authStore = AuthStore(secure),
        blockStore = BlockStore(PreferencesKeyValueStore("jm_block")),
        debug = false,
    )
}

fun main(args: Array<String>) {
    // 2.2.2：无界面启动 WebUI。没有这个入口，WebUI 在产品里就没有触发器。
    if (args.contains("--web")) {
        val port = args.firstOrNull { it.startsWith("--port=") }?.substringAfter("=")?.toIntOrNull() ?: 17800
        val lan = args.contains("--lan")
        val cfg = com.jmnext.web.WebConfig(lan = lan, port = port, passcode = if (lan) com.jmnext.web.WebConfig.newPasscode() else "")
        val (actual, server) = com.jmnext.web.startWebServer(cfg)
        println("WebUI: http://127.0.0.1:" + actual)
        if (lan) println("局域网已开启，口令：" + cfg.passcode)
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { server.stop(0, 0) } })
        while (true) Thread.sleep(1_000)
    }
    // 必须最先调用：否则在此之前打印的启动信息（版本与构建时间、渲染后端）只进终端、不进日志。
    // 此前"日志里看不到启动行"就是这个原因 —— 不是跑的是旧包。
    Log.init()
    // 渲染后端：用户环境（VNC）建不出 GL 上下文 —— 日志里反复出现
    // "org.jetbrains.skiko.RenderException: Cannot create Linux GL context"，
    // 表现是数据正常加载、画面全黑（用户报"看不到漫画"）。
    // 因此默认用软件渲染（Skia CPU 光栅）保证能看见；要回到 GPU 渲染就设 JMCOMIC_RENDER=GL。
    // 必须在创建第一个 Compose 窗口之前设置，所以放在 main 的最前面。
    val wantGpu = System.getenv("JMCOMIC_RENDER")?.equals("GL", ignoreCase = true) == true
    if (!wantGpu) {
        System.setProperty("skiko.renderApi", "SOFTWARE")
        System.err.println("[启动] 渲染后端：软件渲染（设 JMCOMIC_RENDER=GL 可改回 GPU）")
    } else {
        // issue #8：显式要 GPU 时把代价写清楚。GL 上下文在部分机器/远程桌面上建不出来，
        // 表现是"窗口不出现"或"内容全黑"，而日志当时已经写了、只是看不出该怎么办。
        System.err.println(
            "[启动] 渲染后端：GPU（你设置了 JMCOMIC_RENDER=GL）。" +
                "若窗口没有出现或内容全黑，去掉该环境变量即可回退软件渲染。"
        )
    }
    // 启动就打出版本与构建时间：一眼分辨手上跑的是哪一版
    // （此前出现过"拿旧安装包测试、以为改动没编译"的误会，这一行就是为它加的）
    val builtAt = runCatching {
        val loc = object {}.javaClass.protectionDomain.codeSource.location
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(File(loc.toURI()).lastModified()))
    }.getOrNull() ?: "未知"
    Log.line("启动", "JMNeXt 桌面端 版本 $DESKTOP_VERSION，构建时间 $builtAt")
    // issue #9：启动即记录运行环境（系统/版本/架构/运行时/图形后端/字体），
    // 拿到用户日志时第一眼看这行，判断是不是平台差异。
    Log.line("环境", Log.envSummary())
    // 把 stdout/stderr 同时写进 ~/jmnext.log，便于远程读日志定位问题
    // 组合期抛出的异常默认只进 AWT 的日志，容器里看不到；这里显式打到 stderr
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        System.err.println("[崩溃] 线程 ${t.name}：")
        // 崩溃时把环境信息一并写进日志头部（对应 issue #8 的"没有任何输出"）
        System.err.println(Log.envBlock())
        // 渲染初始化失败时给出可执行的下一步，而不是只留一堆堆栈
        val text = (e.javaClass.name + " " + (e.message ?: "")).lowercase()
        if (text.contains("render") || text.contains("skiko") || text.contains("gl context")) {
            System.err.println(
                "[启动] 看起来是渲染后端初始化失败：去掉 JMCOMIC_RENDER=GL 再启动一次" +
                    "（或把 run.bat 里那行改成 set JMCOMIC_RENDER=）即可回退软件渲染。"
            )
        }
        e.printStackTrace()
    }
    runApp()
}

private fun runApp() = application {
    Window(
        // 应用图标：取自 Android 端 mipmap-xxxhdpi 的启动图标（项目自己的资源），
        // 由 scripts 打包脚本同样引用 src/main/resources/icon.png，保持两处一致。
        icon = androidx.compose.ui.res.painterResource("icon.png"),
        onCloseRequest = ::exitApplication,
        title = "JMNeXt",
        state = rememberWindowState(width = 1100.dp, height = 820.dp),
    ) {
        BlogTheme { App() }
    }
}

@Composable
private fun HomeScreen(
    onOpen: (ListItem) -> Unit,
    onOpenSection: (String, String) -> Unit,
    onOpenRandom: () -> Unit = {},
) {
    var items by remember { mutableStateOf<List<ListItem>>(emptyList()) }
    var status by remember { mutableStateOf("正在引导…") }
    var page by remember { mutableStateOf(1) }
    var total by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var updatedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    /** 拉第 next 页：next == 1 时重新引导，并刷新列表与状态。 */
    fun reload(next: Int) {
        busy = true
        scope.launch {
            runCatching {
                if (next == 1) repository.bootstrap()
                repository.latest(next)
            }
                .onSuccess { result ->
                    items = if (next == 1) result.items else (items + result.items).distinctBy { it.id }
                    page = next
                    total = result.total
                    val hit = items.count { it.id in updatedIds }
                    status = "首页 ${items.size}/${result.total} 条" +
                        (if (result.hidden > 0) "（屏蔽规则挡掉 ${result.hidden} 条）" else "") +
                        (if (hit > 0) "，其中 $hit 部在追更里有更新" else "")
                    Log.line("首页", status)
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = "加载失败：${it.message}"
                    Log.error("首页", status, it)
                }
            busy = false
        }
    }

    // 标签级屏蔽：与 Android 的 JmApp.tagBlocker 用同一份共享层实现（TagBlockResolver + TagCache）。
    // 没有标签规则时它一个请求都不会发；规则跟随屏蔽名单，加规则立刻生效且只用缓存重算。
    LaunchedEffect(Unit) { repository.blockStore?.let { TagBlocker.init(repository, it) } }
    LaunchedEffect(Unit) {
        // 「你追的连载里哪些更新了」：数据来自服务端通知（comic_follow 里未读的那批），
        // 照 Android 的 HomeScreen：不要拿阅读时间去猜（既不准又多一次判断）。失败留空，不打扰列表。
        if (repository.auth.isLoggedIn) {
            runCatching {
                repository.notifications(type = NotificationItem.TYPE_COMIC_FOLLOW)
                    .list.filterNot { it.isRead }
                    .flatMap { it.followedUpdates() }
                    .mapNotNull { it.comicIdText }
                    .toSet()
            }
                .onSuccess { updatedIds = it; Log.line("首页", "追更里有更新的作品 ${it.size} 部") }
                .onFailure { Log.line("首页", "追更更新集合读取失败（留空继续）：${it.message}") }
        }
        reload(1)
    }

    LaunchedEffect(status) { System.err.println("[界面] 状态：$status") }

    // 外面这层 Box 是给右下角的浮钮定位用的（Column 里没法 align 到角落）；
    // 里面的 Column 保持原样，所以下面那些行的缩进没有跟着改，避免无意义的整段重排。
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Text(
            text = status,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(enabled = !busy, onClick = { reload(1) }) {
                Text(if (busy) "加载中…" else "刷新")
            }
            Button(enabled = !busy && items.size < total, onClick = { reload(page + 1) }) {
                Text("加载更多")
            }
            Text("已加载 ${items.size} / $total", style = MaterialTheme.typography.labelSmall)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // 推荐分区作为整行表头（跨满整行），下面才是最新列表
            item(span = { GridItemSpan(maxLineSpan) }) {
                PromoteHeader(repository = repository, onOpenComic = onOpen, onOpenSection = onOpenSection)
            }

            items(items, key = { it.id }) { item -> ComicCard(item, updated = item.id in updatedIds, onOpen = { onOpen(item) }) }
        }
    }

        // 首页浮钮（照 Android 的 RandomFab.kt）：随机一本 + 快捷签到。
        // 都放在这一层 Box 的右下角，浮在列表之上；签到按钮在随机按钮正上方，
        // 与 Android 的排布一致（DailyQuickFab 自己会在未登录时返回、不占位置）。
        DailyQuickFab(
            repository = repository,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp + 52.dp + 12.dp),
        )
        RandomFab(
            repository = repository,
            onOpen = onOpen,
            onOpenRandomList = onOpenRandom,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable
private fun ComicCard(item: ListItem, updated: Boolean = false, onOpen: () -> Unit) {
    val coverUrl = remember(item.id) { runCatching { repository.coverUrl(item) }.getOrNull() }
    val bitmap = rememberRemoteImage(coverUrl)

    Column(
        modifier = Modifier.clickable { onOpen() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        // 「追更里有更新」角标：数据来自服务端追更通知（见 HomeScreen 的 updatedIds）
        if (updated) {
            Text(
                "更新",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text = item.name ?: "(无标题)",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = item.author.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 桌面端的页面栈（2.0.0 第一版：就两个页面，用手写状态机而不是导航库）。 */
private sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: String, val title: String) : Screen
    data class Reader(val comicId: String, val chapterId: String, val chapterIds: List<String> = emptyList()) : Screen
    data object Login : Screen
    data class Page(val route: String) : Screen
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val authState by repository.auth.state.collectAsState()
    val navSelection = when (val s = screen) {
        is Screen.Page -> s.route
        else -> "home"
    }

    // 列表页统一的"点作品进详情"，避免在每个分支里重复三段一样的代码
    val openComic: (ListItem) -> Unit = { item ->
        System.err.println("[界面] 打开作品：${item.name} (id=${item.id})")
        screen = Screen.Detail(item.id, item.name.orEmpty())
    }

    // issue #13：只有"从随机进入的详情页"才显示"换一个"。用 aid 比较而不是布尔开关，
    // 这样从首页/搜索/收藏/分类打开详情时自动不显示，无需改动那些入口。
    var randomAid by remember { mutableStateOf<String?>(null) }
    val uiScope = rememberCoroutineScope()

    // 未读通知数：Android 把它挂在「我的」页的通知入口上（ProfileScreen 的 EntryButton badge），
    // 桌面端挂在左侧常驻导航的「通知」项上（对应用户这次的要求）。只镜像服务端的数量。
    // 以 screen 为 key：进/出通知页都会重算，标记已读之后角标会跟着刷新。
    var unreadNotifications by remember { mutableStateOf(0) }
    LaunchedEffect(authState.loggedIn, screen) {
        unreadNotifications = if (!authState.loggedIn) {
            0
        } else {
            runCatching { repository.notificationsUnread().total }.getOrDefault(0)
        }
    }

    // 连载更新提醒：开关在设置页，默认关（与 Android 的 AppPrefs.serialNotify 一致）；
    // 桌面端只能在程序运行时提醒，托盘发不出去时降级为上面那条提示。
    var serialNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        SerialReminder.start(scope, repository) { serialNotice = it }
    }

    Row(Modifier.fillMaxWidth()) {
        SideNav(selected = navSelection, unreadNotifications = unreadNotifications, onSelect = { route ->
            System.err.println("[导航] $route")
            screen = if (route == "home") Screen.Home else Screen.Page(route)
        })

        Column(Modifier.weight(1f).fillMaxSize()) {
            // 连载更新提醒的降级提示（托盘不可用或发送失败时才出现，见 SerialReminder）
            serialNotice?.let { notice ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { serialNotice = null }) { Text("知道了") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "JMNeXt",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable { screen = Screen.Home },
                )
                Text(
                    text = if (authState.loggedIn) {
                        "已登录：${authState.member?.username ?: authState.member?.uid ?: ""}"
                    } else {
                        "未登录"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    if (authState.loggedIn) {
                        showLogoutConfirm = true
                    } else {
                        screen = Screen.Login
                    }
                }) { Text(if (authState.loggedIn) "退出登录" else "登录") }
            }

            Box(Modifier.weight(1f)) {
                NoticeHost()   // 瞬时反馈覆盖层（不挡操作，底部居中）
            // 退出登录是"会丢失登录态"的操作：先确认再执行（与"删除屏蔽规则"不同——那个可逆，不需要确认）
            if (showLogoutConfirm) {
                AlertDialog(
                    onDismissRequest = { showLogoutConfirm = false },
                    title = { Text("退出登录") },
                    text = { Text("退出后需要重新登录才能使用收藏、追更等功能。") },
                    confirmButton = {
                        TextButton(onClick = {
                            showLogoutConfirm = false
                            scope.launch {
                                repository.logout()
                                System.err.println("[登录] 已退出")
                                Notices.success("已退出登录")
                            }
                        }) { Text("退出") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
                    },
                )
            }
                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (fadeIn(tween(durationMillis = Motion.PAGE_MS, easing = Motion.Enter)) +
                            slideInHorizontally(tween(durationMillis = Motion.PAGE_MS, easing = Motion.Enter)) { it / 14 })
                            .togetherWith(fadeOut(tween(durationMillis = Motion.QUICK_MS, easing = Motion.Exit)))
                    },
                    label = "route",
                ) { target ->
                SharedPageHost(this@AnimatedContent) {
                when (val s = target) {
                    is Screen.Home -> HomeScreen(
                        onOpen = openComic,
                        onOpenSection = { id, title ->
                            Log.line("首页", "打开分区更多：$title (id=$id)")
                            screen = Screen.Page("more/$id?title=" + java.net.URLEncoder.encode(title, "UTF-8"))
                        },
                        // 随机浮钮长按 → 「随机本子」页（Android 的长按也是进随机列表）
                        onOpenRandom = { screen = Screen.Page("random") },
                    )

                    is Screen.Detail -> DetailScreen(
                        repository = repository,
                        comicId = s.id,
                        onBack = { screen = Screen.Home },
                        onOpenComments = { aid -> screen = Screen.Page("comments:" + aid) },
                        onOpenComic = openComic,
                        progress = readProgress,
                        // 章节顺序由详情页回传（接口下发的是从旧到新），阅读页据此判断上一话/下一话
                        fromRandom = (randomAid == s.id),
                        onRandomAgain = {
                            uiScope.launch {
                                val batch = runCatching { repository.randomRecommend() }.getOrNull().orEmpty()
                                batch.firstOrNull()?.let { next ->
                                    System.err.println("[界面] 换个随机作品：${next.name} (id=${next.id})")
                                    randomAid = next.id
                                    openComic(next)
                                }
                            }
                        },
                        onOpenChapter = { ch, ids ->
                            System.err.println("[界面] 打开章节：sort=${ch.sort} id=${ch.id}（顺序 ${ids.size} 项）")
                            screen = Screen.Reader(comicId = s.id, chapterId = ch.id, chapterIds = ids)
                        },
                    )

                    is Screen.Reader -> ReaderScreen(
                        repository = repository,
                        progress = readProgress,
                        comicId = s.comicId,
                        chapterId = s.chapterId,
                        chapterIds = s.chapterIds,
                        onOpenComments = { aid -> screen = Screen.Page("comments:" + aid) },
                        onBack = { screen = Screen.Home },
                        onSwitchChapter = { id ->
                            System.err.println("[界面] 切换章节 → $id")
                            screen = Screen.Reader(comicId = s.comicId, chapterId = id, chapterIds = s.chapterIds)
                        },
                    )

                    is Screen.Login -> LoginScreen(repository = repository, onDone = { screen = Screen.Home })

                    is Screen.Page if s.route == "random" -> RandomScreen(
                        repository,
                        onOpenComic = { item -> randomAid = item.id; openComic(item) },
                    )
                    is Screen.Page if s.route == "week" -> WeekScreen(repository, onOpenComic = openComic)

                    is Screen.Page if s.route == "about" -> AboutScreen(repository)
                    is Screen.Page if s.route == "category" -> CategoryScreen(repository, onOpenComic = openComic)
                    is Screen.Page if s.route == "profile" -> ProfileScreen(repository, onGoLogin = { screen = Screen.Login })
                    is Screen.Page if s.route == "notifications" -> NotificationScreen(repository)
                    is Screen.Page if s.route == "creator" -> CreatorScreen(repository)
                    // 评论页用路由字符串带 aid，避免再加一个 Screen 变体
                    is Screen.Page if s.route.startsWith("comments:") -> CommentsScreen(
                        repository = repository,
                        aid = s.route.removePrefix("comments:"),
                        onBack = { screen = Screen.Detail(s.route.removePrefix("comments:"), "") },
                    )
                    is Screen.Page if s.route == "block" -> BlockScreen(repository)
                    is Screen.Page if s.route == "appearance" -> AppearanceScreen()
                    is Screen.Page if s.route == "tags" -> TagsScreen(repository, onSearch = { q ->
                        Log.line("标签", "按标签搜索：$q")
                        screen = Screen.Page("search")
                    })
                    is Screen.Page if s.route == "search" -> SearchScreen(repository, onOpenComic = openComic)
                    is Screen.Page if s.route == "favorites" -> FavoriteScreen(repository, onOpenComic = openComic)
                    is Screen.Page if s.route == "history" -> HistoryScreen(repository, onOpenComic = openComic)
                    // 侧栏「追更」→ 打开收藏页并选中「追更」标签（Android 那边追更就是收藏页里的标签，
                    // 不是独立页面；这样两处入口最终落到同一份 UI，不会各自演化）
                    is Screen.Page if s.route == "tracking" -> FavoriteScreen(repository, onOpenComic = openComic, initialTab = "tracking")
                    // 首页分区「更多」：路由 more/<id>?title=<title>（title 用 URL 编码，
                    // 避免标题里的 & 或 ? 破坏路由解析）
                    is Screen.Page if s.route.startsWith("more/") -> {
                        val rest = s.route.removePrefix("more/")
                        val id = rest.substringBefore("?")
                        // 分区 id 26 是「连载更新」：它不是普通分区，而是按类型 + 星期切的日更表
                        if (id == JmRepository.WEEKLY_SECTION_ID) {
                            WeeklyUpdateScreen(repository = repository, onOpenComic = openComic)
                        } else {
                            MoreListScreen(
                                repository = repository,
                                sectionId = id,
                                title = java.net.URLDecoder.decode(rest.substringAfter("?title=", ""), "UTF-8"),
                                onOpenComic = openComic,
                            )
                        }
                    }

                    is Screen.Page -> {
                        val title = NAV_ITEMS.firstOrNull { it.first == s.route }?.second ?: s.route
                        PageShell(title = title, planned = PAGE_PLANS[s.route] ?: "（待补）")
                    }
                }
                }
                }
            }
        }
    }
}

/**
 * 首页浮钮：**随机一本**（照 Android 的 `RandomFab.kt`，桌面端放 [HomeScreen] 里）。
 *
 * - 单击：拉一次 `randomRecommend()`（共享层 `JmRepository.kt:479`，返回值已应用
 *   标题/作者/分类的屏蔽规则），抽到一本就进详情页；列表为空只记一行日志，不假跳转。
 * - 长按：进「随机本子」页挑一批（Android 的长按也是这个行为）。
 *
 * 与 Android 的两处差异如实写在这里：
 *  1. 桌面端**未引入 material-icons 依赖**（见 `ChapterPickerDialog` 的说明与 `PageRail` 的做法），
 *     所以按钮上写字而不是画骰子图标；
 *  2. Android 的骰子转两圈当"正在办事"的反馈，这里用按钮文字「抽…」代替。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RandomFab(
    repository: JmRepository,
    onOpen: (ListItem) -> Unit,
    onOpenRandomList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .combinedClickable(
                onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val one = runCatching { repository.bootstrap(); repository.randomRecommend() }
                                .getOrDefault(emptyList())
                                .randomOrNull()
                            if (one != null) {
                                Log.line("首页", "随机一本：${one.name} (id=${one.id})")
                                onOpen(one)
                            } else {
                                Log.line("首页", "随机一本：没抽到可用的作品（列表为空）")
                            }
                            busy = false
                        }
                    }
                },
                onLongClick = onOpenRandomList,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (busy) "抽…" else "随机",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * 首页浮钮：**快捷签到**（照 Android 的 `DailyQuickFab`，与 `RandomFab` 同一个文件）。
 *
 * 三个判定都以服务端为准，不靠本地记状态（与 Android 一致）：
 *  - 进首页先问一次 `daily(uid)`（`JmRepository.kt:698`），按日历里的 `signed` +
 *    当天日号判断今天签过没有 —— 换设备/重装都不会错；
 *  - 没有进行中的活动（`daily_id` 为空）就如实说，不当成失败；
 *  - 重复打卡由服务端在 `msg` 里说明（"已經簽到過了"），此时把状态置为已签而不是报错。
 * 打卡走 `dailyCheck(uid, dailyId)`（`JmRepository.kt:719`），只在按下时调用，
 * 页面加载只读不写。
 *
 * 未登录不显示这个按钮（签到要账号）。Android 的 `Daily` 纯逻辑对象在 `app` 模块
 * （`data/Daily.kt`），桌面端看不到，所以下面写了两个等价函数并注明出处。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DailyQuickFab(repository: JmRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val loggedIn = repository.auth.isLoggedIn
    var signedToday by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            signedToday = false
            return@LaunchedEffect
        }
        val uid = repository.auth.member?.uid
        if (uid.isNullOrBlank()) return@LaunchedEffect
        runCatching { repository.daily(uid) }.getOrNull()?.let { d ->
            signedToday = dailySignedToday(
                d.record,
                java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH),
            )
        }
    }

    if (!loggedIn) return

    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (signedToday) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .combinedClickable(
                    onClick = {
                        if (!busy && !signedToday) {
                            busy = true
                            notice = null
                            scope.launch {
                                val uid = repository.auth.member?.uid.orEmpty()
                                val d = runCatching { repository.daily(uid) }.getOrNull()
                                val id = d?.dailyId
                                val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
                                when {
                                    id.isNullOrBlank() -> notice = "现在没有进行中的签到活动"
                                    d != null && dailySignedToday(d.record, today) -> {
                                        signedToday = true
                                        notice = "今天已经签过了"
                                    }
                                    else -> runCatching { repository.dailyCheck(uid = uid, dailyId = id) }.fold(
                                        onSuccess = { res ->
                                            signedToday = true
                                            notice = res.msg?.takeIf { it.isNotBlank() } ?: "签到成功"
                                        },
                                        onFailure = {
                                            if (dailyAlreadyChecked(it.message)) signedToday = true
                                            notice = it.message?.takeIf { m -> m.isNotBlank() } ?: "签到失败，稍后再试"
                                        },
                                    )
                                }
                                Log.line("首页", "快捷签到：${notice ?: "(无返回)"}")
                                busy = false
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    signedToday -> "已签"
                    busy -> "签…"
                    else -> "签到"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // 服务端的说法直接显示给用户（"今天已经签过了"/"签到成功"/失败原因），不做本地改写
        notice?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 「今天签过没有」的桌面端实现。
 *
 * 照 Android 的 `Daily.isSignedToday`（app 模块 `data/Daily.kt:65`）：日历里的 `date`
 * 只是"几号"（实测是 `"01"`、`"02"` 这样的两位字符串），所以拿当月的日号去对；
 * 对不上就返回 false —— 宁可让按钮可点（最多服务端回一句"已经签过了"），
 * 也不要错误地显示"已签"。
 */
private fun dailySignedToday(record: List<List<DailyDay>>, dayOfMonth: Int): Boolean =
    record.flatten().any { it.signed && dailyDayNumberOf(it.date) == dayOfMonth }

/** 从 `"01"` 或 `"2026-10-01"` 里取出日号；取不出来返回 null（照 Android 的 `Daily.dayNumberOf`）。 */
private fun dailyDayNumberOf(date: String?): Int? =
    date?.trim()?.takeLast(2)?.trimStart('0')?.takeIf { it.isNotEmpty() }?.toIntOrNull()

/** 响应是否表示"今天已经签过了"（照 Android 的 `Daily.isAlreadyChecked`，同时认简繁）。 */
private fun dailyAlreadyChecked(message: String?): Boolean {
    val m = message ?: return false
    return m.contains("已簽到") || m.contains("已签到") ||
        m.contains("已經簽到") || m.contains("已经签到")
}
