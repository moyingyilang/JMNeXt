package com.jmnext.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jmnext.data.JmRepository
import com.jmnext.data.remote.dto.ListItem
import com.jmnext.desktop.i18n.I18n
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.getValue

/**
 * 作品卡片（2.0.0 桌面端）：封面 + 标题 + 作者。
 *
 * 抽成公共组件是因为首页、搜索、分类、周刊、随机、收藏、历史、追更都要用它 ——
 * 各页各写一份的话，改一次间距要动八处。
 *
 * 封面比例 3:4 与 Android 端一致（服务端封面模板就是 3x4）。
 */
@Composable
fun ComicCover(repository: JmRepository, item: ListItem, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val coverUrl = remember(item.id) { runCatching { repository.coverUrl(item) }.getOrNull() }
    val bitmap = rememberRemoteImage(coverUrl)
    // 图片淡入（数值取自 Motion）：从空/占位到实图不啪地出现
    val imgAlpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.IMAGE_MS, easing = Motion.Standard),
        label = "coverAlpha",
    )

    Column(modifier = modifier.jmClickable { onOpen() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp))
                .jmSharedElement(jmCoverKey(item.id))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().alpha(imgAlpha),
                    contentScale = ContentScale.Crop,
                )
            }
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

/**
 * 屏蔽提示条（与 Android 端一致的约定）。
 *
 * 列表出口统一在数据层过滤，被挡掉的条数由 PagedList.hidden 带上来。
 * 必须显示出来：否则用户只会看到"结果比预期少"，而不知道是本地规则挡的。
 */
@Composable
fun BlockedNotice(hidden: Int) {
    if (hidden <= 0) return
    Text(
        text = I18n.trf("有 %1\$d 条结果被屏蔽规则挡掉（可在「屏蔽设置」里调整）", hidden),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
