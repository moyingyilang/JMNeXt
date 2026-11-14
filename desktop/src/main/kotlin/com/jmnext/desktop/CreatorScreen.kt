package com.jmnext.desktop

import com.jmnext.desktop.i18n.I18n
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.data.remote.dto.CreatorAuthor
import com.jmnext.data.remote.dto.CreatorWork
import com.jmnext.data.remote.dto.CreatorWorkContent
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 画师与作品库（桌面端，1.9.x 全量移植的最后一块）。
 *
 * 这是**独立于漫画列表的内容源**（JM 的"作品库"，同人志那类），所以单独一页。
 * Android 端的页面标题就是"画师与作品库"，我照原名用，不再叫"创作者库"——
 * 之前那个名字用户看不懂，问过我一次。
 *
 * 三种视图在同一页里切换（桌面端不做多级路由，省得来回跳）：
 *  画师列表 → 点画师 → 该作者的作品；作品列表 → 点作品 → 作品信息（含相关作品）。
 *
 * 分页沿用其它页的做法：追加时按 id 去重（服务端翻页会重叠，重复 key 会让 Compose 崩）。
 */
@Composable
fun CreatorScreen(repository: JmRepository) {
    var mode by remember { mutableStateOf("author") }   // author / work
    var query by remember { mutableStateOf("") }
    var authors by remember { mutableStateOf<List<CreatorAuthor>>(emptyList()) }
    var works by remember { mutableStateOf<List<CreatorWork>>(emptyList()) }
    // 标签级屏蔽：作者与作品两个列表各自过滤（放在列表声明之后，两者元素类型不同故各自计算）
    val hiddenIds = rememberHiddenTagIds()
    val (blockedAuthors, visibleAuthors) = splitBlockedByTag(authors, hiddenIds) { it.id.toString() }
    val (blockedWorks, visibleWorks) = splitBlockedByTag(works, hiddenIds) { it.id.toString() }
    LaunchedEffect(authors.map { it.id }, works.map { it.id }) {
        authors.forEach { TagBlocker.request(it.id.toString()) }
        works.forEach { TagBlocker.request(it.id.toString()) }
    }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(I18n.tr("正在取画师列表…")) }
    var selectedAuthor by remember { mutableStateOf<CreatorAuthor?>(null) }
    var workInfo by remember { mutableStateOf<Pair<String, List<CreatorWork>>?>(null) }
    // 作品内容（creatorWorkContent）与它的加载标记；缺口：此前只取 creatorWorkInfo
    var workContent by remember { mutableStateOf<CreatorWorkContent?>(null) }
    var contentLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(next: Int) {
        busy = true
        scope.launch {
            runCatching {
                if (mode == "author") {
                    repository.creatorAuthors(page = next, query = query.trim())
                } else {
                    repository.creatorWorks(page = next, searchValue = query.trim())
                }
            }
                .onSuccess { r ->
                    if (mode == "author") {
                        val list = r.items.filterIsInstance<CreatorAuthor>()
                        authors = if (next == 1) list else (authors + list).distinctBy { it.id }
                        status = I18n.trf("画师 %1\$d 位 / 共 %2\$d", authors.size, r.total)
                    } else {
                        val list = r.items.filterIsInstance<CreatorWork>()
                        works = if (next == 1) list else (works + list).distinctBy { it.id }
                        status = I18n.trf("作品 %1\$d 部 / 共 %2\$d", works.size, r.total)
                    }
                    total = r.total
                    page = next
                    Log.line("作品库", "$status" + if (query.isNotBlank()) "（搜索：$query）" else "")
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = I18n.tr("加载失败：") + it.message
                    Log.error("作品库", "加载失败 mode=$mode", it)
                }
            busy = false
        }
    }

    /**
     * 打开一个作品：信息 + 内容一起取。
     *
     * 内容那一半**单独 runCatching**：作品库里有些条目只有信息、没有图片（实测 `total_page: 0`、
     * `images: []`），那不该让整页变成错误。信息失败才算真失败。
     */
    fun openWork(id: String, title: String?) {
        status = I18n.tr("正在取作品内容（信息 + 图片，可能要一会儿）…")
        contentLoading = true
        scope.launch {
            val infoR = runCatching { repository.creatorWorkInfo(id) }
            if (infoR.exceptionOrNull() is CancellationException) { contentLoading = false; return@launch }
            val contentR = runCatching { repository.creatorWorkContent(id) }
            if (contentR.exceptionOrNull() is CancellationException) { contentLoading = false; return@launch }

            val info = infoR.getOrNull()
            val content = contentR.getOrNull()
            workInfo = (info?.title ?: title ?: id) to info?.relatedWorks.orEmpty()
            workContent = content
            contentLoading = false
            status = when {
                info == null -> I18n.tr("取作品信息失败：") + (infoR.exceptionOrNull()?.message ?: I18n.tr("原因未知"))
                content == null -> I18n.tr("作品内容没取到：") + (contentR.exceptionOrNull()?.message ?: I18n.tr("原因未知")) + I18n.tr("（信息已显示）")
                content.images.isEmpty() && content.content.isNullOrBlank() -> I18n.tr("已取到作品信息；这个作品没有可看的内容")
                else -> I18n.trf("作品内容已取到：%1\$d 张图", content.images.size)
            }
            Log.line("作品库", status)
        }
    }

    LaunchedEffect(mode) { page = 1; load(1) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = { if (mode != "author") { mode = "author"; selectedAuthor = null; workInfo = null } }) {
                Text((if (mode == "author") "· " else "") + tl("画师"))
            }
            Button(onClick = { if (mode != "work") { mode = "work"; selectedAuthor = null; workInfo = null } }) {
                Text((if (mode == "work") "· " else "") + tl("作品"))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(if (mode == "author") tl("搜索画师") else tl("搜索作品")) },
                singleLine = true,
                modifier = Modifier.width(300.dp),
            )
            Button(enabled = !busy, onClick = { page = 1; load(1) }) { Text(tl("搜索")) }
            StatusLine(status, busy)
        }

        // 加宽：点了画师就显示他的作品；点了作品就显示作品信息
        selectedAuthor?.let { a ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(I18n.trf("画师：%1\$s", a.name ?: a.id), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = {
                    selectedAuthor = null
                    workInfo = null
                    load(1)
                }) { Text(tl("返回列表")) }
            }
        }
        workInfo?.let { (title, related) ->
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 6.dp)) {
                Text(I18n.trf("作品：%1\$s", title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (related.isNotEmpty()) {
                    Text(I18n.trf("相关作品 %1\$d 部（见下方）", related.size), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { workInfo = null; workContent = null }) { Text(tl("关闭")) }
            }
        }

        // 作品内容（缺口：此前只调 creatorWorkInfo，页面上看不到任何内容）。
        // 照 Android 的 CreatorWorkScreen：信息与内容一起取，**内容那一半失败不算整页失败**
        // （实测有的作品回 total_page 0 / images 空，那要显示成"没有可看的内容"而不是报错）。
        if (workInfo != null) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (contentLoading) {
                    item { Text(tl("正在取作品内容…"), style = MaterialTheme.typography.labelSmall) }
                }
                val wc = workContent
                if (wc != null) {
                    item {
                        Text(
                            I18n.trf("共 %1\$d 页 / %2\$d 张图", wc.totalPage, wc.images.size) +
                                (wc.addDate?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(wc.images.size) { index ->
                        val raw = wc.images[index].image
                        val url = remember(raw) { runCatching { repository.creatorContentUrl(raw) }.getOrNull() }
                        val bmp = rememberRemoteImage(url)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (bmp != null) {
                                val ratio = if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 3f / 4f
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.width(560.dp).aspectRatio(ratio),
                                )
                            } else {
                                Text(
                                    I18n.trf("图片未取到：%1\$s", raw),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    wc.content?.takeIf { it.isNotBlank() }?.let { text ->
                        item { Text(text, style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (wc.images.isEmpty() && wc.content.isNullOrBlank()) {
                        item {
                            Text(
                                tl("这个作品没有可看的内容（作品库里有条目只有信息、没有图片）"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!contentLoading && wc == null) {
                    item {
                        Text(
                            tl("作品内容没取到（可能这个作品没有内容，或接口失败；顶部状态栏里有原因）"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 相关作品：点一张直接换到那一部（照 Android，相关作品在同一页里可以继续点下去）
                val related = workInfo?.second.orEmpty()
                if (related.isNotEmpty()) {
                    item {
                        Text(
                            I18n.trf("相关作品 %1\$d 部", related.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            related.forEach { r ->
                                Column(Modifier.width(140.dp).clickable { openWork(r.id, r.title) }) {
                                    val rbmp = rememberRemoteImage(r.image)
                                    Box(
                                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                                            .clip(RoundedCornerShape(Radius.lg))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        if (rbmp != null) {
                                            Image(rbmp, contentDescription = r.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        }
                                    }
                                    Text(r.title ?: r.id, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = { workInfo = null; workContent = null }) { Text(tl("返回作品列表")) }
                }
            }
            return@Column
        }

        BlockedByTagBanner(blockedAuthors) { it.id.toString() }
        BlockedByTagBanner(blockedWorks) { it.id.toString() }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (mode == "author") {
                items(visibleAuthors, key = { it.id }) { a ->
                    Column(
                        modifier = Modifier.clickable {
                            selectedAuthor = a
                            workInfo = null
                            status = I18n.trf("正在取 %1\$s 的作品…", a.name ?: a.id)
                            scope.launch {
                                runCatching { repository.creatorWorksByAuthor(a.id) }
                                    .onSuccess { r ->
                                        works = r.items
                                        total = r.total
                                        status = I18n.trf("%1\$s 的作品 %2\$d 部", a.name ?: a.id, r.items.size)
                                    }
                                    .onFailure {
                                        if (it is CancellationException) return@onFailure
                                        status = I18n.tr("取该作者作品失败：") + it.message
                                    }
                            }
                        },
                    ) {
                        val avatar = remember(a.avatar) { a.avatar }
                        val bmp = rememberRemoteImage(avatar)
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bmp != null) {
                                Image(bmp, contentDescription = a.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Text((a.name ?: "?").take(1), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        Text(a.name ?: a.id, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
                        a.updateDate?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            } else {
                items(visibleWorks, key = { it.id }) { w ->
                    Column(
                        modifier = Modifier.clickable { openWork(w.id, w.title) },
                    ) {
                        val bmp = rememberRemoteImage(w.image)
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(Radius.lg))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            if (bmp != null) {
                                Image(bmp, contentDescription = w.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        Text(w.title ?: w.id, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
                        Text(
                            listOfNotNull(w.authorName, w.date).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            if ((mode == "author" && authors.size < total) || (mode == "work" && works.size < total)) {
                item {
                    Button(enabled = !busy, onClick = { load(page + 1) }) { Text(tl("加载更多")) }
                }
            }
        }
    }
}
