package com.jmnext.desktop

/**
 * 桌面端版本号（2.1.9，修复内存 attach 读取与日志噪声）。
 *
 * 为什么不用 BuildConfig 那种自动生成：桌面端是独立的 Gradle 构建，
 * 没有 AGP 的 BuildConfig。这里先读 jar 清单里的 Implementation-Version，
 * 读不到再退回下面的常量 —— 这样打包时若在 manifest 里写了版本就自动一致，
 * 没写也不会显示空白。
 *
 * **发版时要同时改这里与 scripts/package-linux.sh 里的版本**，否则"检查更新"会误判。
 */
val DESKTOP_VERSION: String = runCatching {
    Class.forName("com.jmnext.desktop.VersionKt")
        .`package`?.implementationVersion
        ?.takeIf { it.isNotBlank() }
}.getOrNull() ?: "2.1.9"
