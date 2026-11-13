package com.jmnext.desktop
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jmnext.data.FavoriteTags
import com.jmnext.data.JmRepository
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 标签页（桌面端，1.9.x 全量移植中）。
 *
 * 这里有两组**不同**的东西，页面上必须分开写清，否则会混：
 *
 *  1. **本地扫描的标签统计** —— 扫描你的收藏作品，统计每个标签出现多少次。
 *     数据在本地（FavoriteTags），不联网；扫描要逐部作品请求，所以慢，
 *     只在按下按钮时执行，结果缓存起来（并记下时间）。
 *  2. **网站上的标星标签** —— JM 网站上你自己标记的标签（favoriteTags 接口），
 *     是个短名单，与上面的统计无关。
 *
 * 点标签 → 回到搜索页按该标签搜索（这是桌面端当前能做的筛选路径）。
 */
@Composable
fun TagsScreen(repository: JmRepository, onSearch: (String) -> Unit) {
    val store = remember { FavoriteTags(PreferencesKeyValueStore("jm_favorite_tags")) }
    var counts by remember { mutableStateOf(store.cached()) }
    var cachedAt by remember { mutableStateOf(store.cachedAt()) }
    var starred by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (counts.isEmpty()) "还没有本地统计，点右侧按钮扫描一次" else "本地统计 ${counts.size} 个标签") }
    // 添加标星用的输入框（审计发现桌面端此前"只能看不能改"）
    var draft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    /** 重新读取网站标星标签。增删之后也要重读，让服务端状态如实反映（不本地翻转）。 */
    fun reloadStarred() {
        scope.launch {
            runCatching { repository.favoriteTags() }
                .onSuccess { list ->
                    starred = list.map { t -> t.tag }.filter { it.isNotBlank() }
                    Log.line("标签", "标星标签 ${starred.size} 个")
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    Log.line("标签", "标星标签读取失败：${it.message}")
                }
        }
    }

    /** 标星标签的增删：接口 type 取 add / remove（与 Android 端一致；tags 由共享层拼成逗号分隔）。 */
    fun updateStar(type: String, tag: String) {
        if (tag.isBlank()) return
        busy = true
        scope.launch {
            runCatching { repository.updateFavoriteTags(type, listOf(tag)) }
                .onSuccess {
                    Log.line("标签", (if (type == "add") "已标星 " else "已取消标星 ") + tag)
                    reloadStarred()
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    status = "标星操作失败：" + it.message
                    Log.error("标签", "标星操作失败 type=" + type + " tag=" + tag, it)
                }
            busy = false
        }
    }

    LaunchedEffect(Unit) { reloadStarred() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(tl("标签"), style = MaterialTheme.typography.titleLarge)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                enabled = !busy && repository.auth.isLoggedIn,
                onClick = {
                    busy = true
                    status = "正在扫描收藏（逐部作品请求，可能要一会儿）…"
                    scope.launch {
                        runCatching { store.refresh(repository) }
                            .onSuccess {
                                counts = it
                                cachedAt = store.cachedAt()
                                status = "扫描完成：${it.size} 个标签"
                                Log.line("标签", "扫描完成，${it.size} 个标签")
                            }
                            .onFailure {
                                if (it is CancellationException) return@onFailure
                                status = "扫描失败：${it.message}"
                                Log.error("标签", "扫描失败", it)
                            }
                        busy = false
                    }
                },
            ) { Text(if (busy) tl("扫描中…") else tl("扫描收藏更新统计")) }

            if (!repository.auth.isLoggedIn) {
                Text(
                    tl("需要登录（收藏列表属于账号数据）"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        Text(
            status + (if (cachedAt > 0) "　上次扫描：${java.time.Instant.ofEpochMilli(cachedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().take(16).replace('T', ' ')}" else ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
        )

        // 一、本地扫描统计（按次数降序）
        if (counts.isNotEmpty()) {
            Text(tl("收藏里的标签（本地统计）"), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                counts.entries.sortedByDescending { it.value }.forEach { (tag, n) ->
                    Row(
                        modifier = Modifier
                            .glassPanel(alpha = Appearance.effectiveAlpha, corner = 999)
                            .clickable { onSearch(tag) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(tag, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            n.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // 二、网站标星标签
        if (starred.isNotEmpty()) {
            Text(
                tl("网站上的标星标签"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 22.dp),
            )
            // 添加标星：桌面端此前"只能看不能改"（功能对齐审计发现的缺口）
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(tl("标签名")) },
                    singleLine = true,
                    modifier = Modifier.width(240.dp),
                )
                Button(
                    enabled = !busy && draft.isNotBlank(),
                    onClick = { updateStar("add", draft.trim()); draft = "" },
                ) { Text(tl("添加标星")) }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                starred.sorted().forEach { tag ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            tag,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clickable { onSearch(tag) }
                                .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                        )
                        // 取消标星：接口 type 取 remove（与 Android 端 updateFavoriteTags("remove", ...) 一致）
                        TextButton(enabled = !busy, onClick = { updateStar("remove", tag) }) { Text(tl("移除")) }
                    }
                }
            }
        }
    }
}
