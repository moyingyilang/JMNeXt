package com.jmnext.desktop

import com.jmnext.desktop.i18n.tl
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 左侧导航（2.0.0 桌面端）。
 *
 * 条目按 Android 端的路由清单来（见 desktop/PARITY.md），先做出外壳让观感可评，
 * 功能逐页接。桌面上左侧常驻导航比底部标签栏更合适：宽度够、不用来回切换。
 */
val NAV_ITEMS: List<Pair<String, String>> = listOf(
    "home" to "首页",
    "search" to "搜索",
    "category" to "分类",
    "week" to "周刊",
    "random" to "随机本子",
    "favorites" to "收藏",
    "history" to "历史",
    "tracking" to "追更",
    "tags" to "标签",
    "creator" to "画师与作品库",
    "notifications" to "通知",
    "block" to "屏蔽设置",
    "appearance" to "外观",
    "about" to "关于",
    "profile" to "我的",
)

@Composable
fun SideNav(selected: String, onSelect: (String) -> Unit, unreadNotifications: Int = 0) {
    Column(
        modifier = Modifier
            .width(176.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "JMNeXt",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
        NAV_ITEMS.forEach { (route, title) ->
            // 键盘与焦点（2.2.3 桌面端）：侧栏每项都可聚焦，Tab 逐项移动、Enter/Space 触发，
            // 聚焦时画 1dp 焦点框 —— 此前桌面端只能鼠标点，键盘用户根本进不去。
            var focused by remember { mutableStateOf(false) }
            val active = route == selected
            val label = tl(title)
            Text(
                // 未读通知数照 Android 的 ProfileScreen（EntryButton 的 badge 参数）：只在 > 0 时显示，
                // 大于 99 显示 99+。桌面侧栏是纯文字，没法画小圆点，就把数字跟在标题后面。
                text = if (route == "notifications" && unreadNotifications > 0) {
                    label + "（" + (if (unreadNotifications > 99) "99+" else unreadNotifications.toString()) + "）"
                } else {
                    label
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    )
                      // 只在聚焦时画描边：宽度写 0.dp 时 Compose 仍会画一条发丝线，
                      // 于是侧栏每一项都像被框住（截图里那圈蓝边就是这个）。
                      .then(
                          if (focused) {
                              Modifier.border(
                                  width = 1.dp,
                                  color = MaterialTheme.colorScheme.primary,
                                  shape = RoundedCornerShape(8.dp),
                              )
                          } else {
                              Modifier
                          },
                      )
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.Spacebar)
                        ) {
                            onSelect(route)
                            true
                        } else {
                            false
                        }
                    }
                    .clickable { onSelect(route) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
