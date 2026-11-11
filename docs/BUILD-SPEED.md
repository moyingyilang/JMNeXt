# 编译速度：实测与日常用法

本机是 Termux + Debian chroot（单容器），构建在容器内跑。以下数字都是**同一台机、热 daemon**
下用 `date +%s` 前后取差得到的墙钟时间，不是 Gradle 自报的 task 时间。

## 日常用 `scripts/build-fast.sh`

```sh
./scripts/build-fast.sh check      # 只编 Kotlin（先看有没有错，最省时）
./scripts/build-fast.sh apk        # 编 + 打 full debug
./scripts/build-fast.sh apk lite   # 编 + 打 lite debug
./scripts/build-fast.sh release    # 打 release（full + lite）
```

默认带 `--offline`：依赖已在缓存里时不再联网。需要拉新依赖时加 `--online`。

## 实测（2026-11-11，`:app:assembleFullDebug`）

| 场景 | 耗时 |
| --- | --- |
| 全部 up-to-date，离线 | 3 到 6 秒 |
| 只改一个 Kotlin 文件 | 15 到 24 秒（`compileFullDebugKotlin` 单独跑约 11 到 20 秒） |
| 只改一个资源文件 | 8 秒 |
| 联网但全部 up-to-date | 3 到 29 秒（波动大，网络元数据检查是主要噪声） |
| 首次（daemon 冷、配置缓存未建） | 1 分 42 秒（78 tasks） |

结论：

- **联网与离线的差异不稳定**，不要用单次数字下判断；要对比就连测两轮。
- 真正的成本在「改 Kotlin 后重编」这一段（11 到 24 秒），与打包无关。
- 所以日常用 `check` 验错，确认后再 `apk`，别每轮都 `assembleDebug`（它会 full + lite 一起编）。

## 已开的相关开关

| 开关 | 值 | 作用 |
| --- | --- | --- |
| `org.gradle.configuration-cache` | true | 配置阶段结果复用 |
| `org.gradle.caching` | true | 任务产物复用（容器内缓存约 3.1 G） |
| `org.gradle.parallel` | true | 模块并行 |
| `org.gradle.daemon` | true | 常驻 daemon（`--no-daemon` 实测慢 7 到 16 秒） |
| `kotlin.compiler.execution.strategy` | daemon | Kotlin 编译常驻 |
| `android.nonTransitiveRClass` | true | R 类不传递 |
| lint | 不进日常构建 | `lintVitalAnalyzeRelease` 单任务曾占 release 总时长 77% |

## 2026-11-11 的 A/B（结果：无效）

改一个 Kotlin 文件后跑 `compileFullDebugKotlin`，各 3 轮：

| 配置 | 三轮耗时 |
| --- | --- |
| 开 `vfs.watch` + `configuration-cache.parallel` | 47 / 16 / 7 秒 |
| 关（默认） | 6 / 8 / 9 秒 |

差异落在噪声里（第一轮含配置缓存重建），关掉反而更稳，所以**两项都没有启用**。
日常真正有效的是：`--offline`、只编单变体、先 `check` 再 `apk`。

## 没有收益的（别再试）

- `--no-daemon`：每次 fork 新 JVM 多花 7 到 16 秒；
- `org.gradle.workers.max=2`：实测更慢（受限的是内存与散热，不是并行度）；
- kapt → KSP：本项目完全没有 kapt/KSP。

## 未做

- gradle-profiler 的分场景基准（改 ABI、改依赖、clean）尚未跑；
- 远程构建缓存（需服务器，单机无收益）。
