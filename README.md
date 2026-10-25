# JMNeXt

用 Kotlin 与 Compose 实现的 JMComic 客户端。**Android 为主项目**，桌面端（Linux / Windows）由 Android 端
移植而来，两端共用同一个 `shared` 数据层。

项目定位为阅读客户端：浏览、搜索、阅读、登录、收藏、追更、评论、通知与本地屏蔽。
不包含购买功能，不含官方广告，也不含任何埋点。设计原则沿用既有表述 ——
「Stability and simplicity are paramount」：能少不写多，能显式不隐式。

[下载](#下载) · [快速开始](#快速开始) · [功能](#功能) · [验证状态](#验证状态) · [致谢](#致谢与来源) ·
[构建与开发](#构建与开发) · [版本线](#版本线) · [文档](#文档)

> **第三方重实现声明**：本项目是对 JMComic 客户端的第三方重新实现，出发点是对客户端本身的技术研究，
> 包括跨平台（Compose Multiplatform）、两端共用数据层，以及性能与体验上的工程实践。
> 本项目与官方无关；禁漫、JMComic 及相关内容的权利归其权利人所有。

## 下载

发布页：[github.com/moyingyilang/JMNeXt/releases](https://github.com/moyingyilang/JMNeXt/releases)。
当前最新版本为 **2.2.1**，共 **15 个附件**（Linux 8 个、Windows 5 个、Android 2 个）；**2.2.2（WebUI）开发中**。

| 平台 | 架构 | 格式 | 大小 | 状态 |
| --- | --- | --- | --- | --- |
| Linux | aarch64 | 便携 tar.gz / deb / rpm / AppImage | 70 / 56 / 70 / 71 MB | 可用 |
| Linux | x86_64 | 便携 tar.gz / deb / rpm / AppImage | 89 / 82 / 89 / 90 MB | 用户确认可用（2026-10-05） |
| Windows | x64 | 免装 Java 的 ZIP / 单体 exe | 88 / 88 MB | 可用（用户真机确认） |
| Windows | arm64 | 免装 Java 的 ZIP / 单体 exe | 88 / 88 MB | 真机未验证 |
| Windows | 通用 | 单体 exe（含两套运行时，按架构自动选择） | 176 MB | 用户确认可正常使用 |
| Android | arm64 / armv7 | APK（`full` / `lite`） | 3.3 / 3.3 MB | 已发布 |

附件名统一为「平台-架构-版本」，例如 `Linux-aarch64-2.1.8.deb`、`Windows-x64-2.1.8.zip`、
`Android-full-2.1.8.apk`。

平台说明：

- **Windows**：ZIP 解压后双击 `jmnext.bat`，包内自带 Temurin JRE 21，无需另装 Java；单体 exe 双击即运行；
  架构不确定时建议选择通用版。包内另有 `diag.bat`，用于在启动失败时收集系统信息与运行日志。
- **Linux**：便携包解压后运行 `./bin/jmnext`。仅支持 64 位 —— 渲染层 Skiko 不发布 32 位 Linux 原生库
  （armv7a 与 i686 均返回 404），32 位 JDK 同样缺失。此限制来自上游依赖，而非打包环节。
- **Android**：`full` 与 `lite` 是同一应用的两种裁剪，`applicationId` 不同，可同时安装。
  自 2.0.0 起 `applicationId` 为 `com.jmnext`（lite 为 `com.jmnext.lite`），
  从更早版本升级**无法覆盖安装，需重新安装**，旧包数据不会自动迁移。
- macOS 与 iOS 不再发布。

## 快速开始

Linux 便携包：

```bash
tar xzf Linux-aarch64-2.1.8.tar.gz && ./bin/jmnext
```

deb、rpm 与 AppImage（x86_64 的包名中架构段为 `x86_64`，deb 为 `_amd64.deb`）：

```bash
sudo dpkg -i Linux-aarch64-2.1.8.deb && jmnext
sudo rpm -i Linux-aarch64-2.1.8.rpm && jmnext
chmod +x Linux-aarch64-2.1.8.AppImage && ./Linux-aarch64-2.1.8.AppImage
```

系统缺少 `libfuse.so.2` 时，AppImage 可加 `--appimage-extract-and-run` 免挂载运行。
运行时需要图形环境（X11 / Wayland）与 OpenGL；纯无头环境会在创建窗口阶段失败，
这是 Compose Desktop 的既有行为，不属于本项目缺陷。

Windows 解压 ZIP 后双击 `jmnext.bat`，或直接双击单体 exe。Android 安装 APK 即可；
登录为可选项，未登录状态下仍可浏览、搜索与阅读。

## 功能

两端共用同一数据层，能力基本一致。桌面端在形态上另有取舍：左侧常驻导航、阅读页纵向连续滚动、
顶栏常驻账号状态，以及方向键 / PageUp / PageDown 翻页。

| 能力 | 说明 |
| --- | --- |
| 阅读 | 阅读页、章节选择、图片反切片还原、按字节上限的图片缓存、预取、横翻与纵滚 |
| 浏览 | 首页（分区与最新）、分类、周刊、随机、标签、画师与作品库 |
| 搜索 | 关键词搜索，排序 5 档、检索字段 5 档、年月筛选、搜索历史、热门标签、随机推荐 |
| 账号 | 登录、注册、找回密码 |
| 收藏与历史 | 收藏夹增删改、收藏列表、历史、追更 |
| 互动 | 评论列表与发表/删除、点赞、通知（未读角标、标记已读） |
| 本地屏蔽 | 关键词 / 标签 / 分类三类名单，命中即从列表隐藏，并在页面顶部提示被屏蔽条目数 |
| 外观 | 五套风格：WindowGlass / Translucent / FlatBlur / Miuix / Material；背景可选内置渐变、本地图片或在线壁纸（Bing 每日与三个二次元源，支持自动轮换） |
| 更新与安全 | 更新检查给出对应平台的安装包直链；本地凭据支持可选口令保护，详见 [desktop/SECURITY.md](desktop/SECURITY.md) |

五套风格不仅更换配色，圆角尺度、表面材质、描边、投影、字重与按压反馈一并更换。

**桌面端与 Android 端的对齐**：2.0.0 之前进行过一次以代码为证据的逐屏审计
（[docs/PARITY-AUDIT.md](docs/PARITY-AUDIT.md)），其中列出的缺口多数已在 2.0.x / 2.1.x 修复
（修复进度附提交号）。尚未完成的主要是首页的连载更新表（类型与星期筛选），该项已明确记档。

## 验证状态

下表是判断可用性的依据。「已通过验证」与「尚未验证」分开列出。

**已通过验证**

| 项 | 证据 |
| --- | --- |
| 键盘翻页 | 真机日志 12 行 |
| Windows 免装包可运行 | 群友真机确认（x64）；通用包经用户确认真机可用 |
| Linux x86_64 四件套 | 用户确认全部可用（2026-10-05） |
| 桌面端汉字显示 | 用户确认正常（2.1.6） |
| Android `full` / `lite` 编译 | `:app:compileFullDebugKotlin`、`:app:compileLiteDebugKotlin` |
| 联网能力（登录与列表） | 无界面冒烟任务 |
| 打包产物自检 | 应用 jar 唯一、启动类为全限定名、deb 内含图标条目、Windows ZIP 内含 skiko 原生库 |
| 发布附件完整性 | 每个版本的附件线上大小与本地逐项一致（波动 0.2% 至 0.3%，阈值 10%） |
| 算法与壁纸 | shared 单元测试与离线回放判据通过；Bing 与三个二次元壁纸源实测返回 200 |

**尚未验证**（不应视为已生效）

| 项 | 当前进度 |
| --- | --- |
| 界面观感与图标观感 | 开发环境无法渲染 Compose，需人工确认 |
| 1.9.153 一批 10 个改动 | 仅有编译证据，未进行界面运行验证 |
| 全部写操作（点赞、标记已读、标签增删、注册、找回密码、发表/删除评论等） | 仅验证到「接口返回即显示」，账号内是否生效未确认 |
| 预取 v2 与取消误报修复 | 是否降低翻页等待、是否消除 `The coroutine scope left the composition`，均未验证 |
| issue #2 的两条修复 | 待报告者复测 |
| 在线壁纸的界面与自动轮换 | 图源已实测，界面未运行验证 |
| Windows arm64 包 | 真机未验证 |
| 2.1.8 的 Windows ACL 与注册表表现 | 开发环境无 Windows，文档中提供了 `icacls` 自查命令 |

### 已知限制

- 桌面端若实现连载更新提醒，只能使用系统托盘或应用内提示，无法在程序未运行时提醒；
- 阅读页内容上下留出的 52dp / 76dp 为估算值，观感未逐项确认；
- x86_64 的 rpm 曾无法产出，现已可产出。rpm 4.18 会拒绝跨架构构建
  （`No compatible architectures found for build`）；用 qemu 运行也会在 `%install` 阶段失败
  （每个脚本段都需 `exec /bin/sh`，而环境未启用 binfmt_misc）。可行方案是让本机原生的
  aarch64 rpmbuild 加 `--target x86_64` 并显式指定 buildroot，
  见 `desktop/scripts/package-linux-x64.sh`；
- 桌面端的未完成项包括首页连载更新表与部分中等缺口，逐条见
  [docs/PARITY-AUDIT.md](docs/PARITY-AUDIT.md) 与 [docs/STATE.md](docs/STATE.md)。

## 版本线

版本号规则：小功能 +001、大功能 +010、特大可行性验证 +100、算法 +300；
版本发布后仍有待修项时使用修复版 `x.x.x.fix(n)`，不另起小版本号。

| 版本线 | 说明 |
| --- | --- |
| 1.1.x 至 1.8.x | Android 主线：功能对齐官方客户端、三类屏蔽、五套风格、动效、性能与编译链专项、lite 变体 |
| **1.9.00x 至 1.9.xxx** | **跨平台预构建序列**：每个版本的 CHANGELOG 均写明「无主项目更新」，不含 Android 端改动；桌面端在此序列中逐批对齐 Android |
| **2.0.0** | **正式版**：项目更名为 JMNeXt（大写 X 同时表示 cross / 跨平台与 extended / 扩展加强），包名、applicationId、展示名、产物名与日志名一并更换 |
| 2.0.1 至 2.1.8 | 交付与命名统一、功能补齐、动效与共享元素、加载态、统一包、issue 修复、安全与可诊断性 |

当前版本为 **2.1.8**，主题是修复与可诊断性：内存凭据处理（关闭 attach 导堆路径）、
本地密钥文件权限收紧与可选口令保护（PBKDF2-HMAC-SHA256 12 万次 + AES-GCM）、偏好节点迁移、
x64 exe 无文字问题修复、启动环境摘要与日志噪声治理、随机页「换一个」。
逐版本细节见 [CHANGELOG.md](CHANGELOG.md)。

## 构建与开发

### 项目结构与环境要求

| 模块 | 说明 | 构建方式 |
| --- | --- | --- |
| `app/` | Android 主项目，含 `full` 与 `lite` 两个变体 | 根 Gradle 构建 |
| `shared/` | 两端共用的数据层（网络、DTO、prefs、屏蔽规则、自学习算法） | 根 Gradle 构建，Android 侧使用 |
| `desktop/` | Compose Multiplatform 桌面端（打包脚本位于 `desktop/scripts/`） | **独立 Gradle 构建** |

根 `settings.gradle.kts` 仅 include `:app` 与 `:shared`；`desktop/` 拥有自己的 `settings.gradle.kts`，
通过 `kotlin.srcDir("../shared/src/main/kotlin")` **直接编译 shared 源码**，
因此桌面构建不需要 Android SDK，代价是 shared 会被编译两次。
其收益是桌面端可在纯 JVM 环境中编译通过，这本身也证明数据层与 Android 无关。

| 组件 | 版本 |
| --- | --- |
| JDK | 21（桌面端 jlink 运行时与 Windows 自带 JRE 均为 Temurin 21） |
| Gradle | 9.x（仓库根提供 wrapper；桌面构建另用系统 `gradle`） |
| Android SDK | `compileSdk 37` / `minSdk 24` / `targetSdk 36`，含 build-tools 与 platform-tools |
| 打包工具 | deb / rpm / AppImage 各自需要对应工具链，须在目标平台或具备交叉环境的机器上执行 |

在 aarch64 设备上构建 Android 端还需一项规避：`android.aapt2FromMavenOverride` 指向本机
build-tools 中的 arm64 `aapt2`（AGP 默认拉取 x86_64 版本，无法运行），已写入根 `gradle.properties`。

### 桌面端

桌面端**不走根构建**，需在 `desktop/` 目录中单独编译（`cd desktop` 后使用系统 `gradle`，
也可从仓库根使用 wrapper：`./gradlew -p desktop <任务>`）：

```bash
cd desktop
gradle createDistributable     # 产出可运行镜像
gradle run                     # 直接运行（需要图形环境）
gradle smoke                   # 无界面冒烟：登录与列表，凭据取 JM_USER / JM_PASS
gradle compileKotlin           # 仅做编译检查
```

打包（先执行**产物自检**，任一项不通过则拒绝出包）：

```bash
scripts/package-linux.sh                  dist                 # 本机架构（aarch64）：portable / deb / rpm / AppImage
scripts/package-linux-x64.sh              dist-x64             # 交叉产出 x86_64：portable / deb / rpm / AppImage
scripts/package-windows-x64.sh            dist-win64           # x64：免装 Java ZIP 与单体 exe
scripts/package-windows-arm64-x.sh        dist-win             # arm64：免装 Java ZIP
scripts/package-windows-universal-exe.sh  dist-win-universal   # 含两套运行时的通用 exe
scripts/package-linux-universal.sh        dist-universal       # 含两套运行时的通用 tar.gz
```

产物自检项：应用 jar 只能有一个（改动代码后 Compose 会生成新的哈希文件名，旧 jar 残留会导致旧类抢先加载）、
启动类必须为全限定名、Windows ZIP 内必须存在 `skiko-windows-*.dll`、按字节核对新旧标记
（`strings` 默认只输出 ASCII，不能用于核对中文标记）。

跨架构构建不需要交叉编译器：桌面端为纯 JVM，没有自写原生代码，仅需替换三项 ——
目标架构的 JDK（用其 jmods 运行 jlink）、目标架构的 Skiko 原生库
（需移除宿主架构的那一份，两份并存会加载到错误的一份），以及启动器
（jpackage 无法跨平台生成启动器，Linux 上改用等价的 shell 脚本）。
细节见 [docs/engineering-notes.md](docs/engineering-notes.md)。

### Android 端与测试

```bash
./gradlew :app:assembleFullDebug      # full 变体
./gradlew :app:assembleLiteDebug      # lite 变体
./gradlew :app:assembleFullRelease    # 发布包（需要签名配置）
```

任务名需写全，`:app:compileDebugKotlin` 会报 Ambiguous matches。

`shared` 提供单元测试（图片反切片、动作形状、壁纸核心语义、自学习算法核心与离线回放）；
桌面端没有单元测试，仅有上述无界面冒烟任务：

```bash
./gradlew :shared:test
./gradlew :shared:test --tests "com.jmnext.selftune.*"
```

发版前另使用 `verify-apk.sh` 做断言式验收（aapt2 输出含 `package:` 行、包内存在 manifest 与 arsc）。

## 界面风格

设计令牌取自 [moyingyilang.github.io](https://moyingyilang.github.io) 的
Fluent（Windows 11 Acrylic / Mica）与 MIUI 毛玻璃设计语言，两端观感统一。

| 令牌 | 浅色 | 深色 |
| --- | --- | --- |
| 强调色 | `#0f6cbd` | `#60cdff` |
| 正文 | `#16181d` | `#f3f4f7` |
| 次级 / 三级文字 | `#4a4f5a` / `#767c88` | 白 72% / 白 50% |
| 描边 | `rgba(15,23,42,0.09)` | 白 10% |
| 圆角 | 8 / 12 / 18 px | 同左 |

深色模式不是浅色的反相，而是另一套取值。桌面端跟随系统深浅色设置。

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/README.md](docs/README.md) | 文档索引 |
| [docs/STATE.md](docs/STATE.md) | **新接手者优先阅读**：现状总表、发布流程、验证边界、未完成清单 |
| [docs/PARITY-AUDIT.md](docs/PARITY-AUDIT.md) | 桌面端与 Android 端的功能对齐审计，逐条附代码行号与修复提交号 |
| [docs/requirements.md](docs/requirements.md) | 需求归并表与落实状态 |
| [docs/MOTION.md](docs/MOTION.md) | 动效约定与共享元素 |
| [docs/lite-plan.md](docs/lite-plan.md) | lite 变体方案与验收指标 |
| [desktop/SELFTUNE.md](desktop/SELFTUNE.md) | 自学习算法的设计、判据与离线回放实测 |
| [desktop/SECURITY.md](desktop/SECURITY.md) | 凭据存储的威胁模型 |
| [docs/engineering-notes.md](docs/engineering-notes.md) | 工程笔记：构建问题、静默失败问题与判断依据 |
| [CHANGELOG.md](CHANGELOG.md) | 各版本变更，含未验证项声明 |

## 致谢

- **[tiann](https://github.com/tiann) 的 [KernelSU](https://github.com/tiann/KernelSU)**：
  悬浮底栏的设计参考来源。底栏浮于内容之上的胶囊形态、`indexAt` 的手指点位判定、spring 吸附参数，
  以及「拖动期间不切页、松手才吸附」均按其做法重做；未照搬其液体玻璃效果。
- **[raoxwup](https://github.com/raoxwup) 的 [haka_comic](https://github.com/raoxwup/haka_comic)**（GPL-3.0）：
  屏蔽设计的致敬来源。该项目按「标签命中 / 分类黑名单 / 标题含关键词」三条规则过滤列表。
  本项目为 Kotlin / Compose，与其 Flutter / Dart 实现没有共用代码
  （见 [shared/src/main/kotlin/com/jmnext/data/BlockRules.kt](shared/src/main/kotlin/com/jmnext/data/BlockRules.kt)）。
- **[moyingyilang](https://github.com/moyingyilang) 的 [moyingyilang.github.io](https://moyingyilang.github.io)**：
  同作者的设计站点，本项目的 UI 设计语言与上述设计令牌来自该站。
- **[@NTdebug145](https://github.com/NTdebug145)**：先后提交了 #13（随机页缺少「换一个」）、
  #14（凭据可在内存中被读出）、#15（注册表残留空的旧节点）、#16（全新安装未弹出口令保护询问），
  并在 #14 下公开了针对本项目凭据存储的 PoC。其中 #15、#16 揭示了上一版实现中的真实缺陷；
  PoC 将「attach 导堆」与「读明文密钥并自行解码」两条路径区分明确，
  推动了 [desktop/SECURITY.md](desktop/SECURITY.md) 的实证记录与一次性安全提示的加入。
- 感谢所有提交 issue 与在真机上验证修复的使用者，本项目的多项修复来自这些复现步骤与日志。

## 许可

本项目以 **AGPL-3.0-only** 授权，全文见 [LICENSE](LICENSE)。
禁漫、JMComic 及相关内容的权利归其权利人所有；本应用与官方无关，仅提供客户端实现。

## 相关项目

下列项目均由同一作者 [moyingyilang](https://github.com/moyingyilang) 维护。

| 项目 | 技术 | 定位 |
| --- | --- | --- |
| 本仓库 `JMNeXt` | Kotlin + Compose Multiplatform | Android（主项目）与桌面（JVM，**稳定线**） |
| [JMNeXt4QtDesktop](https://github.com/moyingyilang/JMNeXt4QtDesktop) | C++20 + Qt 6 | 桌面**原生线**：**faster, lighter, smaller 的 JMNeXt 桌面版**。桌面端没有 ART 一类限制，原生实现可做到约 5–20MB 体积、约 50ms 启动、无需 JVM。目前处于早期阶段，功能对齐判据见其 `docs/PORT-SPEC.md` |
| [TriComiX](https://github.com/moyingyilang/TriComiX) | Kotlin + Compose Multiplatform | **三源统一**客户端：在 JMNeXt 之后迭代开发，一个核心加多个可插拔内容源（EH / Pica / JM），界面引用本项目 |
| [android-termux-builders4JMNeXt](https://github.com/moyingyilang/android-termux-builders4JMNeXt) | Shell | 在 Termux / aarch64 上构建与验收本项目的脚本（MPL-2.0） |

以上项目相互独立：本仓库继续按单源维护与发布，不引入多源架构，也不进行架构迁移。
在 Qt 线与 TriComiX 完成逐项对齐之前，桌面端建议优先使用本仓库的发布包。

### 接口与动效的来源

- **hect0x7/JMComic-Crawler-Python**（<https://github.com/hect0x7/JMComic-Crawler-Python>）：
  它的 release 说明会写明官方 APP 版本号；PR #590（v2.7.10）是我们跟随官方 2.1.12 三项改动的直接依据。
- **官方 Web 端打包产物**：用于接口还原（类型定义、本地进度键、搜索本地排序、广告来源审计）。
- **Material Design 3 与 Compose 动效文档**：仅作方向参考，项目里的 14 个动效 token 是自建的。

完整清单（含"实际用了哪些、没用到哪些"）见 [docs/ACKNOWLEDGEMENTS.md](docs/ACKNOWLEDGEMENTS.md)。

## 最近三个版本

| 版本 | 内容 |
| --- | --- |
| **2.2.1** | 阅读历史与收藏改为**本地为主**（按账号隔离、断网可见、1000 条上限）；新增「跟随手机壁纸」 |
| **2.2.0** | 动效体系化（14 个交互 token、尊重系统"减少动画"）；跟随官方 2.1.12（版本号、章节 ID 重定向、收藏回滚） |
| **2.1.9** | 漫画黑白/墨水屏渲染；阅读页背景跟随壁纸；壁纸轮换修复与秒级档位；反切片改条带流式（峰值内存 21.6MB → 7.8MB） |

各版本的详细说明与**未验证项**见 [CHANGELOG.md](CHANGELOG.md)。
