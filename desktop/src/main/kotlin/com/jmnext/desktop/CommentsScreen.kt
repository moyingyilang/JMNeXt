package com.jmnext.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.data.remote.dto.CommentItem
import com.jmnext.desktop.i18n.I18n
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 评论页（桌面端，1.9.x）。
 *
 * 三个接口都现成：comments(aid, page) 读、sendComment(aid, 文本, commentId?) 发/回、
 * deleteComment(commentId) 删。
 *
 * 两个数据上的要点（读 DTO 时确认的，不是猜的）：
 *  1. **回复是嵌套的**（CommentItem.replies）→ 渲染递归一层；
 *  2. `ForumPayload.total` 是**字符串**，要 toIntOrNull() —— 这个项目里同一字段
 *     在不同接口里类型不同已踩过多次（搜索、通知的 total 也是）。
 *
 * **写操作说明**：发表、回复、删除都是对服务端的写操作，只在用户点击时调用。
 * 开发期间我**一次都没有点过**（用的是用户账号），所以这三条路径**均未经我验证**。
 * 删除按钮只对自己的评论显示（uid 与当前账号比对），避免误删他人。
 */
@Composable
fun CommentsScreen(repository: JmRepository, aid: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val authState by repository.auth.state.collectAsState()
    val myUid = authState.member?.uid

    var items by remember(aid) { mutableStateOf<List<CommentItem>>(emptyList()) }
    var total by remember(aid) { mutableStateOf(0) }
    var page by remember(aid) { mutableStateOf(1) }
    var busy by remember(aid) { mutableStateOf(false) }
    var status by remember(aid) { mutableStateOf("正在加载评论…") }
    var input by remember(aid) { mutableStateOf("") }
    var replyTo by remember(aid) { mutableStateOf<CommentItem?>(null) }
    var notice by remember(aid) { mutableStateOf<String?>(null) }

    fun load(next: Int) {
        busy = true
        scope.launch {
            runCatching { repository.comments(aid = aid, page = next) }
                .onSuccess { payload ->
                    items = if (next == 1) payload.list else (items + payload.list).distinctBy { it.commentId }
                    total = payload.total?.toIntOrNull() ?: items.size
                    page = next
                    status = I18n.trf("已加载 %1\$d 条 / 共 %2\$d 条", items.size, total)
                    Log.line("评论", "$status aid=$aid")
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = I18n.trf("加载失败：%1\$s", it.message)
                    Log.error("评论", "加载失败 aid=$aid", it)
                }
            busy = false
        }
    }

    LaunchedEffect(aid) { load(1) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onBack) { Text(tl("返回")) }
            Text(tl("评论"), style = MaterialTheme.typography.titleMedium)
            StatusLine(status, busy)
        }

        // 发表区：回复时显示"正在回复谁"，可取消
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            replyTo?.let { target ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tl("正在回复 ") + target.authorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { replyTo = null }) { Text(tl("取消回复")) }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(if (replyTo == null) tl("写评论") else tl("回复")) },
                    singleLine = true,
                    modifier = Modifier.width(520.dp),
                )
                Button(
                    enabled = !busy && input.isNotBlank() && authState.loggedIn,
                    onClick = {
                        val text = input.trim()
                        val target = replyTo
                        busy = true
                        notice = null
                        scope.launch {
                            runCatching { repository.sendComment(aid = aid, comment = text, commentId = target?.commentId) }
                                .onSuccess {
                                    notice = if (target == null) "已发表" else "已回复"
                                    input = ""
                                    replyTo = null
                                    Log.line("评论", "发表成功（aid=$aid）")
                                    load(1)
                                }
                                .onFailure {
                                    if (it is CancellationException) return@onFailure
                                    notice = I18n.trf("发表失败：%1\$s", it.message)
                                    Log.error("评论", "发表失败", it)
                                }
                            busy = false
                        }
                    },
                ) { Text(if (replyTo == null) tl("发表") else tl("回复")) }
                if (!authState.loggedIn) {
                    Text(tl("需要登录"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            notice?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items, key = { it.commentId }) { c ->
                CommentRow(
                    repository = repository,
                    comment = c,
                    myUid = myUid,
                    depth = 0,
                    onReply = { replyTo = it },
                    onDelete = { target ->
                        busy = true
                        scope.launch {
                            runCatching { repository.deleteComment(commentId = target.commentId, aid = aid) }
                                .onSuccess {
                                    items = items.filterNot { it.commentId == target.commentId }
                                    total = (total - 1).coerceAtLeast(0)
                                    Log.line("评论", "已删除一条评论")
                                }
                                .onFailure {
                                    if (it is CancellationException) return@onFailure
                                    notice = I18n.trf("删除失败：%1\$s", it.message)
                                    Log.error("评论", "删除失败", it)
                                }
                            busy = false
                        }
                    },
                )
            }
            if (items.isNotEmpty() && items.size < total) {
                item {
                    Button(enabled = !busy, onClick = { load(page + 1) }) { Text(tl("加载更多")) }
                }
            }
        }
    }
}

/** 一条评论，含嵌套回复（递归一层就够 —— 服务端只下发展开的一层）。 */
@Composable
private fun CommentRow(
    repository: JmRepository,
    comment: CommentItem,
    myUid: String?,
    depth: Int,
    onReply: (CommentItem) -> Unit,
    onDelete: (CommentItem) -> Unit,
) {
    val avatar = rememberRemoteImage(comment.photo)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 20).dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    androidx.compose.foundation.Image(
                        bitmap = avatar,
                        contentDescription = comment.authorName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Text(comment.authorName.take(1), style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(comment.authorName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            comment.addtime?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 评论内容是 HTML：先转纯文本再渲染（与 Android 端一致，此前漏了这步）
        comment.content.plainText()?.let { body ->
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onReply(comment) }) { Text(tl("回复"), style = MaterialTheme.typography.labelSmall) }
            // 只对自己的评论显示删除：uid 与当前账号比对，避免误删他人
            if (myUid != null && comment.uid == myUid) {
                TextButton(onClick = { onDelete(comment) }) { Text(tl("删除"), style = MaterialTheme.typography.labelSmall) }
            }
        }

        // 嵌套回复
        comment.replies.forEach { reply ->
            CommentRow(
                repository = repository,
                comment = reply,
                myUid = myUid,
                depth = depth + 1,
                onReply = onReply,
                onDelete = onDelete,
            )
        }
    }
}
