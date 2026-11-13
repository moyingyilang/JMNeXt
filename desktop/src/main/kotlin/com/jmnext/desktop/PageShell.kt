package com.jmnext.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jmnext.desktop.i18n.tl

/**
 * 未实现页面的外壳（2.0.0 桌面端）。
 *
 * 先把版式与层级做出来给人看，再填功能 —— 这样"观感"可以在功能之前被评。
 * 每个外壳都写清这一页**将要**做什么（取自 Android 端同名页面），
 * 避免看起来像已完成却其实是空的。
 */
@Composable
fun PageShell(title: String, planned: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(tl("这一页还没接数据"), style = MaterialTheme.typography.titleMedium)
            Text(tl(planned), style = MaterialTheme.typography.bodyMedium)
            Text(
                tl("桌面端目标是与 Android full 版功能对齐，页面清单见 desktop/PARITY.md。"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 各页面"将要做什么"的说明，内容取自 Android 端对应页面的职责。 */
val PAGE_PLANS: Map<String, String> = mapOf(
    "search" to "按关键词搜作品；命中屏蔽规则时在顶部提示被挡掉了多少条，并允许临时放行一次。",
    "category" to "按分区与分类筛选作品，支持排序与翻页。",
    "week" to "周刊：按类型看每周榜单。",
    "random" to "随机推荐一批作品；支持网格/列表两种排布，并按收藏标签的偏好排序。",
    "favorites" to "我收藏的作品；分作品与作者两栏。",
    "history" to "观看历史（服务端记录到作品粒度）。",
    "tracking" to "追更列表：有新话的作品排前面。",
    "tags" to "收藏标签统计，并按标签筛选作品。",
    "creator" to "画师与作品库：列出画师与作品两类（可分别搜索）；点作品看作品信息与可看内容。与主页漫画列表不同源 —— 它是 JM 的作品库（同人志那类）。",
    "notifications" to "系统通知：连载更新等；未读数量显示在导航上。",
    "block" to "屏蔽名单：关键词、标签、分区三类；命中即从所有列表隐藏。",
    "about" to "版本与变体、检查更新、项目说明与许可（与 Android 端同一套内容）。",
    "profile" to "账号信息、签到、设置项（界面风格、动效、悬浮栏等）。",
)
