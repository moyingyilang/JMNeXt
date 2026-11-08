package com.jmnext.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志落盘（供 debug 追踪包使用）。
 *
 * 为什么自己写：logcat 会被系统回收、用户导不出来；把崩溃写到应用私有目录里，
 * 下次启动就能读到，也便于用户手动导出。
 *
 * 规则：
 * - 只保留最近 [MAX_FILES] 份，避免无限增长；
 * - 写入失败一律忽略（记录日志本身不该再引发崩溃）；
 * - 安装 `Thread.setDefaultUncaughtExceptionHandler` 时会**保留原有 handler**，
 *   写完继续调用它，保证系统仍能正常弹崩溃对话框 / 上报。
 */
object CrashLogger {

    private const val TAG = "JmCrash"
    private const val DIR = "logs"
    private const val MAX_FILES = 10

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    /** 上一次崩溃的文件（如果有），读完可以删除。 */
    fun lastCrash(context: Context): File? =
        dir(context).listFiles()?.filter { it.name.startsWith("crash-") }?.maxByOrNull { it.lastModified() }

    /** 是否有未读崩溃（启动时用来提示用户）。 */
    fun hasUnreadCrash(context: Context): Boolean = lastCrash(context) != null

    /** 把崩溃写成文件；返回写入的文件（失败返回 null）。 */
    fun record(context: Context, t: Throwable, extra: String? = null): File? = runCatching {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir(context), "crash-$stamp.txt")
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("time: ${Date()}")
            pw.println("version: ${com.jmnext.BuildConfig.VERSION_NAME} (${com.jmnext.BuildConfig.VERSION_CODE})")
            pw.println("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / API ${android.os.Build.VERSION.SDK_INT}")
            extra?.let { pw.println("extra: $it") }
            pw.println()
            t.printStackTrace(pw)
        }
        file.writeText(sw.toString())
        // 清理超出上限的旧文件
        dir(context).listFiles()?.filter { it.name.startsWith("crash-") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
        Log.w(TAG, "崩溃已写入 ${file.name}")
        file
    }.getOrNull()

    /**
     * 装上全局未捕获异常处理器。**保留原 handler**，写完再交给它。
     *
     * 只在追踪包里启用（[com.jmnext.BuildConfig] 的 DEBUG_LOG 标志），
     * 正式包不打扰用户、也不额外写盘。
     */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(app, throwable, extra = "thread=${thread.name}")
            previous?.uncaughtException(thread, throwable)
        }
    }
}
