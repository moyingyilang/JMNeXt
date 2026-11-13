# 打包与构建工具评估（2026-11）

记一次评测的结论，免得以后重复踩。所有数字都是本机（aarch64 手机，8 核，15G 内存但常驻占用高）实测。

## 一、构建：并行不是答案

| 场景 | 耗时 |
| --- | --- |
| 热构建（daemon 常驻，改一个 Kotlin 文件） | 5–6 秒 |
| 冷启动（`gradle --stop` 后重来） | 30 秒 |
| `--parallel --max-workers=6` | 45 秒 |
| `--max-workers=4` | 15 秒 |

冷启动里真正编译只占约 4.8 秒，其余耗在 daemon 启动与配置阶段；资源与清单那几个任务都报 13 秒左右，是在等同一件事，不是各自算得久。

结论：保持 daemon 常驻、不加 `--no-daemon`、不追并发。Gradle 文件里已按此清理了一处冲突的 `org.gradle.parallel=false`（AndroidIDE 遗留）与重复的 `caching` 行。

## 二、Compose Hot Reload：已接入桌面端

`desktop/build.gradle.kts` 加了 `org.jetbrains.compose.hot-reload` 1.2.0，任务组里有 `hotRun`、`hotReloadMain` 等。插件首次解析需要联网（本地缓存里只有实现 jar，没有插件标记），之后可离线。

## 三、Nucleus：能装，但不是“打包 drop-in”

实测（独立 spike 工程，未动主项目）：

- 插件 `dev.nucleusframework` 2.5.18 能在 Gradle 9.8 + Kotlin 2.4.20 + Compose 1.12.1 下解析并注册任务组（`packageDeb`、`createDistributable` 等）
- 但它要求把入口从 `application { Window(...) }` 改成 `nucleusApplication(backend = Tao) { DecoratedWindow(...) }`，配置块从 `compose.desktop.application` 改成 `nucleus.application`，并引入 `nucleus.nucleus-application` 与 `nucleus.decorated-window-tao`
- 官方兼容表要求 Compose **1.12.0**（我们跑 1.12.1），Nucleus 2.5.x “不保证”在其它小版本上工作
- 打包链路是：jpackage 生成运行时镜像 → **electron-builder**（node）出 deb/rpm/nsis/AppImage。spike 里 `packageDeb` 依次卡在 “Please specify project homepage” 与 “Please specify author email”，说明它要 npm 风格的包元数据

结论：Nucleus 是“换运行时 + 换入口 + 换打包器”的迁移，不是把现有 `package-*.sh` 换掉。它没有解决我们最痛的点（交叉架构出包），反而引入 node 工具链。

## 四、远程构建缓存：暂不引入

Silo / FosterStack / Cloudflare Worker 这类远程缓存的价值在多机或 CI。我们单机在 chroot 里构建，本地 build cache 已启用；再加一层 HTTP 缓存只会多一次网络往返。等有 GitHub Actions 再评估。

## 五、真要改打包，先做这件事

把“手工拼装”换成“一次生成、多方派生”：

1. `jpackage --type app-image` 生成一份自带运行时的目录
2. 从这份目录派生 tar.gz / deb / rpm / AppImage
3. 交叉架构仍用 jlink 拼运行时，但只拼一次并缓存

这比换框架风险小，也和现有脚本兼容。

## 六、真正省时间的地方：压缩算法（已改，实测 5 倍）

`package-linux.sh` 分段计时（aarch64，146M 应用镜像）：

| 阶段 | 改前 | 改后 |
| --- | --- | --- |
| 构建 | 0–3 秒 | 0–3 秒 |
| 自检 | 1 秒 | 1 秒 |
| 便携 tar.gz | 13 秒 | 13 秒 |
| deb | 68 秒 | 2 秒 |
| rpm | 101 秒 | 9 秒 |
| AppImage | 10 秒 | 10 秒 |
| 合计 | 194 秒 | 38 秒 |

原因很简单：deb 与 rpm 默认用 xz -6 压 146M，慢在压缩；改用 zstd（`dpkg-deb -Zzstd -z3`、rpm 的 `_binary_payload w3.zstdio`）后几乎瞬间。

代价是体积：

| 产物 | xz | zstd |
| --- | --- | --- |
| deb | 54M | 66M |
| rpm | 58M | 66M |

现代 Debian/Ubuntu 与 Fedora 都支持 zstd 负载；要兼容很老的发行版时，把这两处改回 xz 即可（脚本里只改一行）。

验证：`dpkg-deb -c` 可列出 197 个条目、`rpm -qip` 可读、payload 分别是 `data.tar.zst` 与 zstd。
