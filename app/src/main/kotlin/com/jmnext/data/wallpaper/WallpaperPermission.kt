package com.jmnext.data.wallpaper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 读系统壁纸需要的运行时权限。
 *
 * 为什么需要：`WallpaperManager.getDrawable()` 名义上不需要权限，但
 * - 回退链里的 `getWallpaperFile()` 要 `READ_EXTERNAL_STORAGE`；
 * - MIUI / HyperOS 对第三方读壁纸还有额外收紧。
 *
 * 实测症状：五条路径全返回 null，且日志里没有任何 SecurityException —— 因为
 * `getDrawable` 在没权限时就是静默返回 null。所以**必须先申请权限再读**，
 * 而不是等失败再看日志。
 *
 * Android 13（API 33）起没有能读系统壁纸的普通权限，返回 null 表示无需申请。
 */
object WallpaperPermission {

    /** 当前系统上需要申请的权限；null 表示无需申请。 */
    fun required(): String? =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Manifest.permission.READ_EXTERNAL_STORAGE
        } else {
            null
        }

    fun granted(context: Context): Boolean {
        val perm = required() ?: return true
        return context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }
}
