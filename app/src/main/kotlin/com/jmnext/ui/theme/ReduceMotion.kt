package com.jmnext.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 系统是否要求"减少动态效果"。
 *
 * 判据是 `Settings.Global.ANIMATOR_DURATION_SCALE`：用户把开发者选项里的动画缩放设为 0、
 * 或某些无障碍配置下，这个值为 0 —— 此时我们所有位移类动效都应退化成极短的交叉淡入
 * （见 [reducedMotion]），而不是继续做 300ms 的推入。
 *
 * 为什么不做成"完全不动的开关"：完全没有变化会让页面切换失去因果线索，用户会以为没点上。
 * 120ms 的淡入既尊重了设置，又保留了"发生了变化"的反馈。
 *
 * 实现上顺手处理了两件容易被忽略的事：
 * 1. **监听设置变化**：用户改完缩放回到应用，不该还需要重启才生效；
 * 2. 读不到时**默认 false**（跟随系统 = 开启动画），不能因为读值失败就把所有人的动效关掉。
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(readAnimatorScale(context) == 0f) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = readAnimatorScale(context) == 0f
            }
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
            )
        }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return reduce
}

/** 读取系统的动画时长缩放；读不到时返回 1f（正常动画）。 */
fun readAnimatorScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
}.getOrDefault(1f)

/**
 * 取当前风格的 token 表，并**按系统偏好退化**。
 *
 * 用法：`val motion = rememberJmMotion()`，然后 `motion.navForward.tween<Float>()`。
 */
@Composable
fun rememberJmMotion(): JmMotionTable {
    val reduce = rememberReduceMotion()
    val spec = JmTheme.spec.motion
    return remember(spec, reduce) { spec.toJmMotionTable(reduceMotion = reduce) }
}
