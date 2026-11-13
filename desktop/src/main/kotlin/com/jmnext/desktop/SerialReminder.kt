package com.jmnext.desktop

import com.jmnext.data.JmRepository
import java.awt.Color
import java.awt.Graphics2D
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 连载更新提醒（桌面端）。
 *
 * 语义照 Android 的 `app/.../data/SerialNotify.kt`：
 *  - 数据来源是**服务端通知的未读数**（`notificationsUnread().total`），**不拿阅读时间去猜**
 *    （Android 的注释写明：那样既不准，又多一次判断）；
 *  - 只有"未读数比上次看到的多"才提醒（指纹去重，上次的数值存本地）；
 *  - **未登录不轮询**；读取失败只记一行日志、保留上次状态，不弹错误提醒。
 *
 * 托盘用 AWT 的 `SystemTray`（零新依赖，与本项目"不引入大依赖"的取向一致）。
 * `SystemTray.isSupported()` 为假、或安装托盘图标/发消息抛异常时，**降级为窗口内提示**
 * （通过 `onNotice` 回调交给界面）：不假装托盘可用，也不让程序崩溃。
 *
 * **限制（必须知道）**：桌面端**只能在程序运行时提醒**。Android 用 AlarmManager 可以在后台唤醒进程，
 * 桌面端没有等价的系统级唤醒机制，**进程退出后无法提醒**。
 */
object SerialReminder {
    /** 照 Android 的间隔：半天一次（Android 注释写明"交给系统调度、实际可能更长"，那是有意的）。 */
    private const val INTERVAL_MS = 12L * 60 * 60 * 1000

    /**
     * 启动后先查一次（与 Android 的差异，如实记在这里）：
     * Android 的第一次检查也在 12 小时之后，因为它可以在后台长期存在；
     * 桌面端只在运行时有效，若也等 12 小时，实际等于永远不提醒，所以这里先查一次再进入 12 小时间隔。
     */
    private const val FIRST_DELAY_MS = 20_000L

    private const val KEY_ENABLED = "enabled"
    private const val KEY_SEEN = "seen"

    private val prefs by lazy { PreferencesKeyValueStore("jm_serial_notify") }
    private val trayIcons = HashMap<SystemTray, TrayIcon>()

    /** 开关默认**关**（与 Android 的 `AppPrefs.serialNotify` 默认 false 一致）：由用户在设置里主动打开。 */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.putBoolean(KEY_ENABLED, value)

    /** 上次提醒时的未读数；只有它变多才再提醒（指纹去重，照 Android）。 */
    private var seen: Int
        get() = prefs.getInt(KEY_SEEN, 0)
        set(value) = prefs.putInt(KEY_SEEN, value)

    fun start(scope: CoroutineScope, repository: JmRepository, onNotice: (String) -> Unit) {
        scope.launch {
            delay(FIRST_DELAY_MS)
            while (isActive) {
                checkOnce(repository, onNotice)
                delay(INTERVAL_MS)
            }
        }
    }

    private suspend fun checkOnce(repository: JmRepository, onNotice: (String) -> Unit) {
        if (!enabled) return
        if (!repository.auth.isLoggedIn) return
        val count = runCatching { repository.notificationsUnread().total }.getOrElse {
            Log.line("提醒", "读取未读数失败（保留上次状态）：${it.message}")
            return
        }
        if (count <= 0 || count <= seen) return
        seen = count
        val text = "你追的连载有 $count 条更新"
        Log.line("提醒", text)
        if (notifyTray(text) != true) onNotice(text)
    }

    /** 返回 true 表示托盘已发出；false 或 null 表示需要降级到窗口内提示。 */
    private fun notifyTray(text: String): Boolean? {
        if (!SystemTray.isSupported()) {
            Log.line("提醒", "系统托盘不可用，降级为窗口内提示")
            return null
        }
        return runCatching {
            val tray = SystemTray.getSystemTray()
            val icon = trayIcons.getOrPut(tray) {
                val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                val g: Graphics2D = image.createGraphics()
                g.color = Color(0x3D, 0x7E, 0xC8)
                g.fillOval(0, 0, 16, 16)
                g.dispose()
                TrayIcon(image, "JMNeXt").also { t ->
                    t.isImageAutoSize = true
                    tray.add(t)
                }
            }
            icon.displayMessage(com.jmnext.desktop.i18n.I18n.tr("连载更新"), text, TrayIcon.MessageType.INFO)
            true
        }.getOrElse {
            Log.line("提醒", "托盘提醒失败，降级为窗口内提示：${it.message}")
            null
        }
    }
}
