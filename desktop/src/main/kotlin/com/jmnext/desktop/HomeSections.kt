package com.jmnext.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.data.remote.dto.ListItem
import com.jmnext.data.remote.dto.PromoteSection
import com.jmnext.desktop.i18n.tl
import kotlinx.coroutines.CancellationException

/**
 * 首页的推荐分区（桌面端，1.9.x 全量移植中）。
 *
 * 与 Android 端的结构一致：`promote()` 一次返回若干分区，每个分区自带一批作品。
 * 这里每个分区渲染成「标题 + 横向滚动的封面」；标题旁的「更多」走
 * `onOpenSection(section.id, section.title)`，由 Main.kt 接到 MoreListScreen（promoteList 分页）。
 *
 * 桌面上横向滚动比手机上更自然（有滚轮与触控板横滑），所以整块做成横排。
 */
@Composable
fun PromoteHeader(repository: JmRepository, onOpenComic: (ListItem) -> Unit, onOpenSection: (String, String) -> Unit) {
    var sections by remember { mutableStateOf<List<PromoteSection>>(emptyList()) }

    LaunchedEffect(Unit) {
        // 首页列表与推荐分区同时起步，而主机发现（JmHostDiscovery）可能还没完成，
        // 此时调用 promote() 会失败。日志实证过这一条：
        //   [首页] 推荐分区加载失败：API 主机尚未初始化：请先执行 JmHostDiscovery
        // 所以这里做有限次重试，而不是一失败就放弃。
        var attempt = 0
        while (attempt < 15) {
            val result = runCatching { repository.promote() }
            result.onSuccess { list ->
                // 首页只放前 4 个分区：再多会把最新列表挤到很下面，
                // 而首页的主要用途还是"看最新"。
                sections = list.filter { s -> s.content.isNotEmpty() }
                Log.line("首页", "推荐分区 ${list.size} 个，展示 ${sections.size} 个（第 ${attempt + 1} 次尝试）")
                return@LaunchedEffect
            }
            val e = result.exceptionOrNull()
            if (e is CancellationException) return@LaunchedEffect
            attempt++
            kotlinx.coroutines.delay(400)
        }
        // 重试用尽：推荐拉不到不该影响首页显示最新，记一行即可
        Log.line("首页", "推荐分区重试 $attempt 次仍失败，跳过（不影响最新列表）")
    }

    if (sections.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        sections.forEach { section ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
            ) {
                  SectionTitle(
                      text = section.title ?: tl("(未命名分区)"),
                      modifier = Modifier.weight(1f),
                  )
                  TextButton(onClick = { onOpenSection(section.id, section.title.orEmpty()) }) {
                      Text(tl("更多"))
                  }
              }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                section.content.forEach { item ->
                    Box(Modifier.width(140.dp)) {
                        ComicCover(repository, item) { onOpenComic(item) }
                    }
                }
            }
        }
    }
}
