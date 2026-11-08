package com.jmnext.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 读取**系统当前壁纸**并落成缓存文件，供既有图片链路使用。
 *
 * 为什么落文件：背景层是 `AsyncImage(model = url)`，天然支持 `file://`；
 * 落文件能复用既有的模糊/压暗/裁剪链路，不必为这一个来源另写一套渲染。
 *
 * ## 为什么用三条取图路径
 * `WallpaperManager.drawable` 在部分机型/系统版本上返回 null（动态壁纸、被主题替换、
 * 或系统对第三方读取做了限制）。只走这一条会表现为“读不到壁纸”。
 * 因此按 `drawable` → `bitmap` → `peekDrawable` 依次尝试，谁成功用谁。
 *
 * ## 已知限制
 * 1. 系统**没有“壁纸已更换”回调**，只能在回到前台或用户点「重新读取」时重取；
 * 2. 三条路径都失败时返回 null，调用方退回纯色，不白屏；
 * 3. 缓存文件名带时间戳，避免 Coil 把同 URL 的旧图当成新图；
 * 4. 超大壁纸先等比缩到 [MAX_EDGE]，否则 4K 壁纸的 ARGB 位图要吃 30MB+。
 */
object SystemWallpaperSource {

    private const val TAG = "JmWallpaper"
    private const val DIR = "system-wallpaper"
    /** 最长边上限。背景会被模糊/压暗，超过这个尺寸对观感没有额外收益。 */
    private const val MAX_EDGE = 1600

    /**
     * 这台机器上「跟随手机壁纸」为什么可能不可用。
     *
     * 实测（HyperOS V816 / Android 17）：壁纸由 `com.miui.miwallpaper/ImageWallpaper`
     * 这个**系统壁纸服务**托管，并非框架静态壁纸。此时 WallpaperManager 的五条读取路径
     * 全部静默返回 null —— 不是权限问题（清单已声明，且 Android 13+ 本来也没有对应权限），
     * 而是第三方应用拿不到该服务里的图。
     *
     * 返回 null 表示没有检测到这类限制。
     */
    fun unsupportedReason(context: Context): String? {
        val info = runCatching {
            WallpaperManager.getInstance(context).wallpaperInfo
        }.getOrNull() ?: return null
        val pkg = info.component?.packageName ?: return null
        return when {
            pkg.contains("miwallpaper") ->
                "当前壁纸由系统壁纸服务（$pkg）托管，第三方应用读不到；" +
                    "请改用「自定义地址」或「纯色」。"
            pkg.isNotEmpty() && !pkg.contains("android") ->
                "当前是动态/主题壁纸（$pkg），无法作为静态背景读取；请改用「自定义地址」或「纯色」。"
            else -> null
        }
    }

    fun load(context: Context): String? {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val out = File(dir, "wall-${System.currentTimeMillis()}.png")
        val manager = WallpaperManager.getInstance(context)

        val bitmap = readBitmap(manager)
        if (bitmap == null) {
            Log.w(TAG, "五条取图路径都失败（drawable/peek/peekFast/file 均为空）")
            return null
        }

        return try {
            val scaled = scaleDown(bitmap)
            FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            dir.listFiles()?.filter { it != out }?.forEach { it.delete() }
            "file://${out.absolutePath}"
        } catch (t: Throwable) {
            Log.w(TAG, "写缓存失败：${t.javaClass.simpleName} ${t.message}")
            out.delete()
            null
        }
    }

    /**
     * 依次尝试 API 上真实存在的五条路径（已用 javap 核对 android.jar）：
     * getDrawable(FLAG_SYSTEM) → getDrawable() → peekDrawable() → peekFastDrawable() → getWallpaperFile()。
     * 前四条返回 Drawable，最后一条直接给出壁纸文件的 fd，绕开 Drawable 解码失败的情况。
     */
    private fun readBitmap(manager: WallpaperManager): Bitmap? {
        drawableOf { manager.getDrawable(WallpaperManager.FLAG_SYSTEM) }
            ?.let { Log.i(TAG, "系统壁纸取自 getDrawable(FLAG_SYSTEM)"); return it }
        drawableOf { manager.drawable }
            ?.let { Log.i(TAG, "系统壁纸取自 getDrawable()"); return it }
        drawableOf { manager.peekDrawable() }
            ?.let { Log.i(TAG, "系统壁纸取自 peekDrawable()"); return it }
        drawableOf { manager.peekFastDrawable() }
            ?.let { Log.i(TAG, "系统壁纸取自 peekFastDrawable()"); return it }

        // 最后一条：直接读壁纸文件。某些 ROM 上 Drawable 解码失败但文件是好的。
        runCatching {
            manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
                android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
            }
        }.onFailure { Log.w(TAG, "getWallpaperFile 失败：${it.javaClass.simpleName} ${it.message}") }
            .getOrNull()?.let { Log.i(TAG, "系统壁纸取自 getWallpaperFile()"); return it }

        Log.w(TAG, "五条路径全部失败：drawable/peek/peekFast/file 均不可用")
        return null
    }

    /** 把某个 Drawable 来源画成位图；取不到或画失败返回 null。 */
    private inline fun drawableOf(block: () -> android.graphics.drawable.Drawable?): Bitmap? =
        runCatching {
            val d = block() ?: return@runCatching null
            val w = d.intrinsicWidth.coerceAtLeast(1)
            val h = d.intrinsicHeight.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, w, h)
            d.draw(Canvas(bmp))
            bmp
        }.onFailure { Log.w(TAG, "Drawable 解码失败：${it.javaClass.simpleName}") }.getOrNull()

    /** 把 drawable 画进 ARGB 位图；drawable 为空时返回 null。 */
    private fun drawableOf(
        manager: WallpaperManager,
        explicit: android.graphics.drawable.Drawable? = null,
    ): Bitmap? = runCatching {
        val d = explicit ?: manager.drawable ?: return@runCatching null
        val w = d.intrinsicWidth.coerceAtLeast(1)
        val h = d.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        bmp
    }.onFailure { Log.w(TAG, "drawable 读取失败：${it.javaClass.simpleName}") }.getOrNull()

    private fun scaleDown(src: Bitmap): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= MAX_EDGE) return src
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
