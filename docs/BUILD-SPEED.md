# 编译速度的几条实测

都是在这台机器上量的（Termux 里的 Debian 容器），用 date 取前后差，不是 Gradle 自己报的任务时间。

平时这么用就行：

    ./scripts/build-fast.sh check      # 只编 Kotlin，先看有没有错
    ./scripts/build-fast.sh apk        # 编完打 full debug 包
    ./scripts/build-fast.sh release    # 打 release

默认带 --offline。依赖都在缓存里了，联网那点检查反而拖时间；真要拉新依赖时加 --online。

量下来的数：

- 什么都没改，全 up-to-date：3 到 6 秒
- 只改一个 Kotlin 文件：15 到 24 秒（里面单跑 compileFullDebugKotlin 是 11 到 20 秒）
- 只改一个资源：8 秒
- 第一次跑（daemon 冷、配置缓存还没建）：1 分 42 秒，78 个任务

有个坑：联网和离线的差别不稳定。有一次全 up-to-date 联网跑了 29 秒，另一次同样条件是 3 秒。要比较就连测两轮，别拿单次结果下结论。

真正费时间的是改完 Kotlin 重编那一段，跟打包没关系。所以平时先 check 验错，确认了再 apk；别每轮都 assembleDebug，那是 full 和 lite 一起编。

已经开着的开关：配置缓存、构建缓存（容器里约 3.1 G）、parallel、常驻 daemon、Kotlin 编译 daemon、nonTransitiveRClass。lint 没进日常构建，lintVitalAnalyzeRelease 单个任务曾经占到 release 总时长的 77%。

试过没用的：vfs.watch 和 configuration-cache.parallel。改一个 Kotlin 文件各跑三轮，开着是 47/16/7 秒，关着是 6/8/9 秒，差别在噪声里，关着还稳一点，所以没留。--no-daemon 也别用，每次 fork 新 JVM 要多花 7 到 16 秒。workers.max=2 实测更慢，机器是热和内存先到头，不是并行度不够。

还没做的：gradle-profiler 的分场景基准（改 ABI、改依赖、clean）。远程构建缓存对单机没意义。
