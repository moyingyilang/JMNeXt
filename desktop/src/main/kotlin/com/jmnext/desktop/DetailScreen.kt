package com.jmnext.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.jmnext.data.JmRepository
import com.jmnext.data.prefs.BlockKind
import com.jmnext.data.prefs.ReadProgressStore
import com.jmnext.data.remote.dto.AlbumDetail
import com.jmnext.data.remote.dto.ListItem
import com.jmnext.data.remote.dto.SeriesItem
import com.jmnext.desktop.i18n.I18n
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.selection.SelectionContainer

/**
 * 作品详情页（桌面端，1.9.x 全量移植中）。
 *
 * 已有：封面、标题、作者、标签、页数、简介、章节列表（倒序）、无章节作品直接开读、
 * 收藏/取消收藏、相关作品。
 *
 * 关于收藏：`toggleFavorite` 是**对服务端的写操作**，只在用户按下按钮时调用，
 * 页面加载时绝不自动触发。开发期间我没有用自己的验证去点它（用的是用户账号），
 * 所以"收藏成功"这条路径的真实结果未经我验证，界面显示服务端返回的原文。
 *
 * 下载：`albumDownload(comicId)` 拿服务端下发的地址后交给系统浏览器 ——
 * 桌面端没有 Android 的 DownloadManager，不在应用里另造下载管理器（与 Android 同一取舍）。
 */
@Composable
fun DetailScreen(
    repository: JmRepository,
    comicId: String,
    onBack: () -> Unit,
    // 第二个参数是本章节的顺序（从旧到新），阅读页据此判断上一话/下一话
    onOpenChapter: (SeriesItem, List<String>) -> Unit,
    progress: ReadProgressStore,
    onOpenComments: (String) -> Unit,
    onOpenComic: (ListItem) -> Unit,
    // 点标签的出口：给了就路由到搜索页（Main.kt 尚未传），没给就在本页就地搜索
    onOpenTag: ((String) -> Unit)? = null,
    // issue #13：由"随机"进入时显示"换一个"按钮，点击后原地重新随机（替换详情内容）
    fromRandom: Boolean = false,
    onRandomAgain: (() -> Unit)? = null,
) {
    var detail by remember(comicId) { mutableStateOf<AlbumDetail?>(null) }
    var status by remember(comicId) { mutableStateOf(I18n.tr("正在加载作品…")) }
    var loading by remember(comicId) { mutableStateOf(true) }
    var favorite by remember(comicId) { mutableStateOf(false) }
    // 追更状态：初始态由下面单独的 LaunchedEffect 从接口取（详情接口不下发这个字段）。
    var tracked by remember(comicId) { mutableStateOf(false) }
    // 点赞状态：已赞与否、次数都在 AlbumDetail 里（与 Android 同源），这里只放忙碌标记与提示
    var likeBusy by remember { mutableStateOf(false) }
    var likeMessage by remember(comicId) { mutableStateOf<String?>(null) }
    var trackBusy by remember { mutableStateOf(false) }
    var favMessage by remember(comicId) { mutableStateOf<String?>(null) }
    var favBusy by remember { mutableStateOf(false) }
    // 下载（照抄 Android 详情页那条「下载整部作品」入口）：忙碌标记与就地提示
    var downloadBusy by remember { mutableStateOf(false) }
    var downloadMessage by remember(comicId) { mutableStateOf<String?>(null) }
    // 标签交互（中等缺口）：点标签搜索、屏蔽标签、标星标签
    var tagBusy by remember { mutableStateOf(false) }
    var tagMessage by remember(comicId) { mutableStateOf<String?>(null) }
    var tagQuery by remember(comicId) { mutableStateOf<String?>(null) }
    var tagItems by remember(comicId) { mutableStateOf<List<ListItem>>(emptyList()) }
    var tagResultStatus by remember(comicId) { mutableStateOf("") }
    // 网站上的标星标签（从接口读回来，本地不留一份"以为"的状态）
    var starredTags by remember(comicId) { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(comicId) {
        System.err.println("[详情] 进入页面 comicId=$comicId")
        runCatching { repository.album(comicId) }
            .onSuccess {
                detail = it
                favorite = it.isFavorite
                status = I18n.trf("%1\$s · %2\$d 话 · 相关 %3\$d 部", it.name.orEmpty(), it.series.size, it.relatedList.size)
                loading = false
                System.err.println("[详情] 数据到达：${it.name} 作者${it.author.size}人 标签${it.tags.size}个 章节${it.series.size}话 相关${it.relatedList.size}部")
            }
            .onFailure {
                if (it is CancellationException) return@onFailure
                status = I18n.tr("加载失败：") + it.message
                loading = false
                it.printStackTrace()
            }
    }

    // 追更初始态必须从接口取一次（详情接口不下发这个字段，Android 端也是单独请求一次）。
    // 早先只做本地翻转：作品其实已在追更列表里时，按钮会错误显示成"追更"，点一下反而变成取消追更。
    LaunchedEffect(comicId) {
        if (!repository.auth.isLoggedIn) return@LaunchedEffect
        val t = runCatching { repository.isTracked(comicId) }.getOrDefault(false)
        tracked = t
        Log.line("详情", "追更初始态：" + t)
    }

    /** 重读网站标星标签。增删之后也重读，让服务端状态如实反映（与 TagsScreen 同一做法，不本地翻转）。 */
    fun reloadStarredTags() {
        if (!repository.auth.isLoggedIn) return
        scope.launch {
            runCatching { repository.favoriteTags() }
                .onSuccess { list -> starredTags = list.map { t -> t.tag }.filter { it.isNotBlank() }.toSet() }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    Log.line("详情", "标星标签读取失败（留空继续）：" + it.message)
                }
        }
    }

    LaunchedEffect(comicId) { reloadStarredTags() }

    /**
     * 点标签 = 按标签搜索。
     *
     * 调用方给了 [onOpenTag] 就交给它路由（与 TagsScreen 的 onSearch 同路）；
     * 没给（Main.kt 当前没传）就在本页左栏就地铺开结果 —— 不做"点了没反应"的假按钮。
     */
    fun openTag(tag: String) {
        if (onOpenTag != null) {
            Log.line("详情", "按标签搜索（交给外层路由）：" + tag)
            onOpenTag(tag)
            return
        }
        tagQuery = tag
        tagItems = emptyList()
        tagBusy = true
        tagResultStatus = I18n.trf("正在按标签「%1\$s」搜索…", tag)
        scope.launch {
            runCatching { repository.search(query = tag) }
                .onSuccess { result ->
                    tagItems = result.page.items
                    tagResultStatus = I18n.trf("标签「%1\$s」：共 %2\$d 条，本页 %3\$d 条", tag, result.page.total, result.page.items.size) +
                        if (result.page.hidden > 0) I18n.trf("（本页屏蔽 %1\$d 条）", result.page.hidden) else ""
                    Log.line("详情", tagResultStatus)
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    tagItems = emptyList()
                    tagResultStatus = I18n.tr("按标签搜索失败：") + it.message
                    Log.error("详情", "按标签搜索失败 tag=" + tag, it)
                }
            tagBusy = false
        }
    }

    /** 屏蔽此标签：直接写本地屏蔽名单（与「屏蔽设置」是同一份存储，列表过滤在数据层）。 */
    fun blockTag(tag: String) {
        val store = repository.blockStore
        if (store == null) {
            tagMessage = I18n.tr("屏蔽存储未初始化，无法屏蔽标签")
            return
        }
        if (store.isBlocked(BlockKind.Tag, tag)) {
            tagMessage = I18n.trf("「%1\$s」已经在屏蔽名单里", tag)
            return
        }
        store.addTag(tag)
            Notices.success(I18n.tr("已屏蔽标签：") + tag)
        tagMessage = I18n.trf("已屏蔽标签：%1\$s（可在「屏蔽设置」里取消）", tag)
        Log.line("详情", tagMessage.orEmpty())
    }

    /** 标星 / 取消标星：updateFavoriteTags("add" | "remove")，与 Android 端同一接口。 */
    fun starTag(tag: String) {
        if (!repository.auth.isLoggedIn) {
            tagMessage = I18n.tr("请先登录再标星标签")
            return
        }
        val add = !starredTags.contains(tag)
        tagBusy = true
        tagMessage = null
        scope.launch {
            runCatching { repository.updateFavoriteTags(if (add) "add" else "remove", listOf(tag)) }
                .onSuccess {
                    tagMessage = (if (add) I18n.tr("已标星：") else I18n.tr("已取消标星：")) + tag
                    Notices.success(tagMessage.orEmpty())
                    Log.line("详情", tagMessage.orEmpty())
                    reloadStarredTags()
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    tagMessage = I18n.tr("标星操作失败：") + it.message
                    Notices.error(tagMessage.orEmpty())
                    Log.error("详情", "标星操作失败 tag=" + tag, it)
                }
            tagBusy = false
        }
    }

    /**
     * 取整部作品的下载链接并交给系统浏览器（照抄 Android `DetailViewModel.requestDownload`）。
     *
     * 需要登录，而且**失败不是 401**：实测未登录时是 HTTP 200 + `{"status":"0","msg":"請先登入"}`，
     * 所以判断落在 `DownloadPayload.isOk`（download_url 非空）上，不看 HTTP 状态码 ——
     * 把这种响应当成功会给出一个空链接。
     *
     * 桌面端没有 Android 的 DownloadManager，最接近的零依赖做法是把地址交给系统浏览器，
     * 下载由浏览器负责（断点续传、下载记录都在那边）。浏览器拉不起来时**如实报失败**，
     * 并把地址一并显示出来，不假装"已开始下载"。
     */
    fun downloadAlbum() {
        if (!repository.auth.isLoggedIn) {
            downloadMessage = I18n.tr("下载需要登录")
            return
        }
        downloadBusy = true
        downloadMessage = null
        scope.launch {
            runCatching { repository.albumDownload(comicId) }
                .onSuccess { payload ->
                    if (!payload.isOk) {
                        downloadMessage = payload.msg ?: I18n.tr("这个作品暂时不能下载")
                        Log.line("详情", "下载不可用：" + downloadMessage)
                    } else {
                        val title = payload.title.orEmpty()
                        val url = payload.downloadUrl.orEmpty()
                        val opened = runCatching {
                            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                        }
                        downloadMessage = if (opened.isSuccess) {
                            buildString {
                                append(I18n.tr("已开始下载"))
                                payload.title?.takeIf { it.isNotBlank() }?.let { append("：$it") }
                                payload.fileSize?.takeIf { it.isNotBlank() }?.let { append("（$it）") }
                            }
                        } else {
                            I18n.tr("获取到下载地址但打开系统浏览器失败：") +
                                (opened.exceptionOrNull()?.message ?: I18n.tr("未知原因")) + I18n.tr("；下载地址：") + url
                        }
                        Log.line("详情", "下载 title=$title 浏览器打开=" + opened.isSuccess)
                    }
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    downloadMessage = I18n.tr("获取下载地址失败：") + it.message
                    Log.error("详情", "获取下载地址失败", it)
                }
            downloadBusy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        // issue #13：只有从"随机"进入的详情页显示这个按钮 —— 不满意就直接换一个，
        // 不必退回随机页再点。动作由 Main.kt 提供（重新取一批随机作品并替换当前详情内容）。
        if (fromRandom && onRandomAgain != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRandomAgain) { Text(tl("换一个")) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onBack) { Text(tl("返回")) }
            // 状态/错误文案可选：用户常要把它贴给别人看（详情页加载失败、接口报错都走这里）
                // 加载中与失败分开表达：失败时不该转圈（此前两者共用同一个 status）
                if (loading) {
                    LoadingHint(tl("正在加载作品…"))
                } else {
                    SelectionContainer { Text(status, style = MaterialTheme.typography.titleMedium) }
                }
        }

        val d = detail ?: return@Column
        Row(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
            // 左栏：封面与基本信息
            Column(
                modifier = Modifier.width(280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val coverUrl = remember(d.id) { runCatching { repository.coverUrl(d.id) }.getOrNull() }
                val bitmap = rememberRemoteImage(coverUrl)
                // 封面进入的近似共享元素效果：从略小、透明放大到原尺寸。
                // 与真正的共享元素（SharedTransitionLayout + sharedElement）不同：它不是从列表封面位置
                // 连续过渡过来，而是在详情页内做一次有方向的入场。真正的共享元素需要把 scope 从根部
                // 一路传到两个页面，改动面大，留作后续。
                val coverIn by animateFloatAsState(
                    targetValue = if (bitmap != null) 1f else 0f,
                    animationSpec = tween(durationMillis = JmMotion.base, easing = Motion.Enter),
                    label = "detailCoverIn",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(Radius.lg))
                        .jmSharedElement(jmCoverKey(d.id))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (bitmap != null) {
                        Image(bitmap, contentDescription = d.name, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 0.94f + 0.06f * coverIn; scaleY = 0.94f + 0.06f * coverIn; alpha = coverIn }.graphicsLayer { scaleX = 0.94f + 0.06f * coverIn; scaleY = 0.94f + 0.06f * coverIn; alpha = coverIn }, contentScale = ContentScale.Crop)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !favBusy,
                        onClick = {
                            favBusy = true
                            favMessage = null
                            scope.launch {
                                runCatching { repository.toggleFavorite(d.id) }
                                    .onSuccess {
                                        favorite = !favorite
                                        favMessage = if (favorite) I18n.tr("已加入收藏") else I18n.tr("已取消收藏")
                                        System.err.println("[详情] 收藏切换：$favMessage")
                                    }
                                    .onFailure {
                                        if (it is CancellationException) return@onFailure
                                        favMessage = I18n.tr("操作失败：") + it.message
                                        System.err.println("[详情] $favMessage")
                                    }
                                favBusy = false
                            }
                        },
                    ) { Text(if (favorite) tl("已收藏") else tl("收藏")) }

                    // 点赞：与 Android 端一致 —— 已点过就不再打接口；成功后本地 liked=true、likes+1
                    Button(
                        enabled = !likeBusy,
                        onClick = {
                            if (d.liked) {
                                likeMessage = I18n.tr("已经点过赞了")
                            } else if (!repository.auth.isLoggedIn) {
                                likeMessage = I18n.tr("请先登录再点赞")
                            } else {
                                likeBusy = true
                                likeMessage = null
                                scope.launch {
                                    runCatching { repository.like(d.id) }
                                        .onSuccess { action ->
                                            if (action.isOk) {
                                                detail = d.copy(liked = true, likes = d.likes + 1)
                                                likeMessage = action.msg ?: I18n.tr("点赞成功")
                                            } else {
                                                likeMessage = action.msg ?: I18n.tr("点赞失败")
                                            }
                                            Log.line("详情", "点赞：" + likeMessage)
                                            val msg = likeMessage
                                            if (msg?.contains("成功") == true) Notices.success(msg) else Notices.show(msg ?: I18n.tr("点赞完成"))
                                        }
                                        .onFailure {
                                            if (it is CancellationException) return@onFailure
                                            likeMessage = I18n.tr("点赞失败：") + it.message
                                            Log.error("详情", "点赞失败", it)
                                        }
                                    likeBusy = false
                                }
                            }
                        },
                    ) { Text(if (d.liked) tl("已赞 ") + d.likes else tl("点赞")) }

                    // 未登录不显示：否则点了必然失败，还得多跳一次登录页（照抄 Android 端）
                    if (repository.auth.isLoggedIn) {
                        Button(
                            enabled = !trackBusy,
                            onClick = {
                                trackBusy = true
                                scope.launch {
                                    runCatching { repository.toggleTracking(d.id) }
                                        .onSuccess {
                                            tracked = !tracked
                                            Log.line("详情", "追更切换：" + (if (tracked) "已追更" else "已取消追更"))
                                            Notices.success(if (tracked) I18n.tr("已追更") else I18n.tr("已取消追更"))
                                        }
                                        .onFailure {
                                            if (it !is CancellationException) Log.error("详情", "追更切换失败", it)
                                        }
                                    trackBusy = false
                                }
                            },
                        ) { Text(if (tracked) tl("已追更") else tl("追更")) }
                    TextButton(onClick = { onOpenComments(d.id) }) { Text(tl("评论")) }
                    }

                    if (!repository.auth.isLoggedIn) {
                        Text(
                            tl("未登录"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                favMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }

                // 「继续观看」已移到右栏章节列表顶部（用户要求：与「从头开始」并排）。
                // 原处这份会造成同一个入口出现两次，已移除；进度的读取在右栏顶部就地完成。


                InfoLine(tl("标题"), d.name.orEmpty())
                InfoLine(tl("作者"), d.author.joinToString(" / "))
                // 标签：点标签按标签搜索、可屏蔽、可标星（审计缺口：此前只当纯文本显示）。
                // Android 是「点=搜索、长按=屏蔽」；桌面端没有长按，改成每个标签跟两个明确按钮，
                // 不藏隐藏手势（藏了没人发现，等于没做）。
                if (d.tags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            tl("标签（点标签按标签搜索，右侧可屏蔽或标星）"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        d.tags.forEach { tag ->
                            // 已屏蔽的标签显示状态而不是再给一个"屏蔽"按钮（按了也不会有新变化）
                            val blocked = repository.blockStore?.isBlocked(BlockKind.Tag, tag) == true
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { openTag(tag) }
                                        .padding(vertical = 6.dp),
                                )
                                if (blocked) {
                                    Text(
                                        tl("已屏蔽"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    TextButton(enabled = !tagBusy, onClick = { blockTag(tag) }) {
                                        Text(tl("屏蔽"), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                TextButton(enabled = !tagBusy, onClick = { starTag(tag) }) {
                                    Text(
                                        if (starredTags.contains(tag)) tl("取消标星") else tl("标星"),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                        tagMessage?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        // 就地铺开的标签搜索结果：点结果直接打开那一部的详情
                        tagQuery?.let { q ->
                            Text(
                                tagResultStatus.ifBlank { I18n.trf("标签「%1\$s」", q) },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            tagItems.forEach { item ->
                                Text(
                                    text = item.name ?: item.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenComic(item) }
                                        .padding(vertical = 4.dp),
                                )
                            }
                            TextButton(onClick = { tagQuery = null; tagItems = emptyList(); tagResultStatus = "" }) {
                                Text(tl("关闭标签搜索结果"), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                InfoLine(tl("页数"), d.totalPhotos.toString())
                if (!d.description.isNullOrBlank()) {
                    // 简介也是 HTML（Android 的详情页同样走 plainText）。
                    // plainText 在"剥完标签什么都不剩"时返回 null，此时渲染空串而不是回退原文。
                    Text(d.description.plainText().orEmpty(), style = MaterialTheme.typography.bodySmall)
                }

                // 下载（照抄 Android 详情页那条「下载整部作品」，位置同样在简介之后）。
                // 官方是独立下载页（`/comic/detail/download`），两端都只做成一件事：
                // 拿到服务端下发的 download_url 后交给系统去下，应用里不另造下载管理器。
                // 桌面端没有 Android 的 DownloadManager，等价物是系统浏览器。
                // 未登录时 Android 走 onNeedLogin("下载需要登录")；桌面端没有登录路由出口，
                // 与点赞/标星同一做法：把同一句文案就地显示出来。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !downloadBusy) { downloadAlbum() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        tl("下载整部作品"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        tl("交给系统浏览器下载"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                downloadMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }

                // 相关作品（横向滚动）：接口直接给的就是 ListItem，可直接用现成卡片
                if (d.relatedList.isNotEmpty()) {
                    Text(tl("相关作品"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        d.relatedList.forEach { item ->
                            Box(Modifier.width(140.dp)) {
                                ComicCover(repository, item) { onOpenComic(item) }
                            }
                        }
                    }
                }
            }

            // 右栏：章节列表（倒序，最新的在最上面）
            if (d.series.isEmpty()) {
                // 无章节作品（单话同人本很常见）：作品 id 自身就是可读单元。
                // 与 Android 端一致 —— 不补的话这类作品点了没反应。
                LazyColumn(modifier = Modifier.fillMaxSize().padding(start = Spacing.lg)) {
                    item {
                        Text(
                            text = tl("开始阅读"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenChapter(SeriesItem(id = d.id, sort = null, name = null), listOf(d.id)) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                        Text(tl("该作品没有章节列表，直接阅读整本"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(start = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // 顶部两个入口（用户要求）：从头开始 / 继续观看。
                    // 进度就地查一次，避免为此把变量上提到左右分栏之前（那是结构性改动，风险更大）。
                    item {
                        val ids = d.series.map { it.id }
                        val saved = remember(comicId, d.series.size) { progress.lastChapterId(comicId) }
                        val idx = saved?.let { ids.indexOf(it) } ?: -1
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = { onOpenChapter(d.series.first(), ids) }) { Text(tl("从头开始")) }
                            // 无记录时置灰（用户确认过：置灰而不是隐藏，便于一眼看出有没有进度）
                            Button(
                                enabled = idx >= 0,
                                onClick = { if (idx >= 0) onOpenChapter(d.series[idx], ids) },
                            ) { Text(tl("继续观看")) }
                        }
                    }
                    // 章节列表**正序**（用户要求：从上到下 1、2、3…，不要倒序）
                    items(d.series, key = { it.id }) { chapter ->
                        Text(
                            text = I18n.trf("第 %1\$s 话", chapter.sort ?: "?") + (chapter.name?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenChapter(chapter, d.series.map { it.id }) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 值文本可选：标题/作者这类信息，用户常要复制去搜索或分享。
        // 有意只在这一处加：按钮文案、标签、列表标题若也可选，会干扰点击与拖拽（选择性加，不全局铺开）。
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
