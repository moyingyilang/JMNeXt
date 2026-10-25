package com.jmnext.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 读取**系统当前壁纸**并落成一个缓存文件，供既有图片链路使用。
 *
 * 为什么落文件而不是直接返回 Bitmap：背景层用的是 `AsyncImage(model = url)`，
 * 它天然支持 `file://`。落文件可以复用既有的模糊/压暗/裁剪链路，不必为这一个来源另写一套渲染。
 *
 * 已知限制（写在代码里而不是文档里，免得以后有人当成 bug）：
 * 1. **`WallpaperManager` 没有"壁纸已更换"的回调** —— 系统换壁纸后我们不会自动知道，
 *    只能在回到前台或用户点「重新读取」时重新取；
 * 2. 读不到（无权限、系统限制、动态壁纸）时返回 null，调用方**退回主题纯色**，不能白屏；
 * 3. 缓存文件带时间戳，避免 Coil 的 memoryCache 把旧图当成新图（同一个 URL 内容变了它不会重取）。
 */
object SystemWallpaperSource {

    private const val TAG = "JmWallpaper"
    private const val DIR = "system-wallpaper"

    /**
     * 取系统壁纸并写成缓存文件。
     *
     * @return `file://` 形式的 URL；读不到时返回 null（调用方应退回纯色）
     */
    fun load(context: Context): String? {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val out = File(dir, "wall-${System.currentTimeMillis()}.png")
        return try {
            val manager = WallpaperManager.getInstance(context)
            val drawable = manager.drawable ?: return null
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            // 只保留最新一张，避免缓存目录无限增长
            dir.listFiles()?.filter { it != out }?.forEach { it.delete() }
            "file://${out.absolutePath}"
        } catch (t: Throwable) {
            Log.w(TAG, "读取系统壁纸失败：${t.javaClass.simpleName}")
            out.delete()
            null
        }
    }
}
