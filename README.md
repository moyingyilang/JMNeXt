# JMNeXt

JMComic 的第三方客户端，Kotlin + Compose 写的。Android 是主项目，桌面端（Linux / Windows）从 Android 移植出去，两边共用一个 `shared` 数据层。

起因很简单：官方客户端的广告和推荐位太吵，桌面端又没有。顺手也想拿 Compose Multiplatform 练个手，于是就变成了现在这样。

## 能干什么

- 看：阅读页、章节切换、预取、横翻与纵滚，图片反切片还原
- 找：首页分区、分类、周刊、随机、标签、画师作品库；搜索支持排序、检索字段、年月筛选、历史与热门
- 账号：登录、注册、找回密码。不登录也能浏览和阅读
- 存：收藏夹、追更、历史。进度以本地为主，也能通过 WebDAV 同步到你自己的网盘
- 说：评论、点赞、通知（未读角标、标记已读）
- 挡：关键词 / 标签 / 分类三类屏蔽，命中就从列表里消失
- 换皮：五套风格（WindowGlass / Translucent / FlatBlur / Miuix / Material），背景可以是渐变、本地图或在线壁纸

桌面端形态上有点不一样：左侧常驻导航、顶栏常驻账号状态、方向键和 PageUp/PageDown 翻页。

## 下载

都在 [Releases](https://github.com/moyingyilang/JMNeXt/releases)。附件名是 `<平台>-<架构>-<版本>`，Android 固定叫 `Android-full-<版本>.apk`，lite 同理。每个版本 21 个附件：15 个标准包 + 2 个 Android 追踪包 + 4 个桌面调试包。

Linux 便携包：

```bash
tar xzf Linux-aarch64-2.2.4.tar.gz && ./bin/jmnext
```

deb、rpm、AppImage：

```bash
sudo dpkg -i Linux-aarch64-2.2.4.deb && jmnext
sudo rpm -i Linux-aarch64-2.2.4.rpm && jmnext
chmod +x Linux-aarch64-2.2.4.AppImage && ./Linux-aarch64-2.2.4.AppImage
```

AppImage 提示缺 `libfuse.so.2` 时加 `--appimage-extract-and-run`。运行时要有图形环境和 OpenGL，纯无头环境会在建窗口那步失败——这是 Compose Desktop 的脾气，不是本项目的问题。

Windows 解压后双击 `jmnext.bat`，或者直接双击单体 exe。包里自带 JRE，不用另装 Java。

Android 装上就能用。`full` 和 `lite` 的包名不同，可以同时装；数据不共享，这点是故意的。

## 哪些没做完、哪些没验证

- 首页的「连载更新表」（按类型和星期筛）还没做
- Windows arm64 的包没在真机跑过
- 界面观感和启动图标没有逐机型核对
- 点赞、标记已读、评论这些写操作，只确认到「接口返回什么就显示什么」，账号里到底生效没有，没核对
- 桌面端十几个包只验了产物形态，我一个都没实际运行过

架构和构建那堆细节写得比这里细，见 [docs/STATE.md](docs/STATE.md)；不过那份是 1.9.x 时期写的快照，看的时候留个心眼。

## 怎么编

需要 JDK 21 和 Gradle 9。仓库根只管 Android 和 shared，桌面端是独立的 Gradle 构建：

- `app/`：Android 主项目，full / lite 两个变体
- `shared/`：两端共用的数据层（网络、DTO、prefs、屏蔽规则）
- `desktop/`：Compose Multiplatform 桌面端

`desktop/` 用 `kotlin.srcDir("../shared/src/main/kotlin")` 直接吃 shared 的源码，所以不需要 Android SDK，代价是 shared 会被编两遍。好处是桌面端能在纯 JVM 上编过，这本身就说明数据层没偷偷依赖 Android。

日常出 Android 包：

```bash
./scripts/build-fast.sh apk       # 先 check 再出包，默认离线
```

桌面端的打包脚本在 `desktop/scripts/`，四个平台各一个，Windows 的 exe 需要先有对应的 fat jar。

构建慢在哪、哪些开关试过没用，写在 [docs/BUILD-SPEED.md](docs/BUILD-SPEED.md)。

## 版本

跟官方客户端版本走。官方 2.1.13 对应我们的 2.2.4：上报版本与两套 i18n 字典一起更新。

有更新就两端一起出包，版本号保持一致——哪怕这次只改了 Android。这条是硬性要求，写在发版手册里。

逐版本细节见 [CHANGELOG.md](CHANGELOG.md)。

## 文档

| 文档 | 一句话 |
| --- | --- |
| [CHANGELOG](CHANGELOG.md) | 每个版本改了什么、没验证什么 |
| [docs/BUILD-SPEED.md](docs/BUILD-SPEED.md) | 构建实测数字与日常用法 |
| [docs/RELEASE-RUNBOOK.md](docs/RELEASE-RUNBOOK.md) | 发版步骤与踩过的坑 |
| [docs/OFFICIAL-PARITY.md](docs/OFFICIAL-PARITY.md) | 跟官方版本的对齐记录 |
| [docs/PARITY-AUDIT.md](docs/PARITY-AUDIT.md) | Android 与桌面端的功能缺口审计 |
| [docs/VIRTUAL-DISPLAY.md](docs/VIRTUAL-DISPLAY.md) | 虚拟屏抓帧与隔离截图 |
| [docs/branding/README.md](docs/branding/README.md) | 图标与关于页素材的设计说明 |

## 致谢

界面与数据层的实现离不开 Kotlin、Compose、Skiko 这些上游。官方客户端提供协议，社区库里的一些 PR 帮我们对齐过版本号，见 [docs/ACKNOWLEDGEMENTS.md](docs/ACKNOWLEDGEMENTS.md)。

## 许可

AGPL-3.0-only。禁漫、JMComic 及相关内容的权利归权利人所有；本项目与官方无关，只是另一份客户端实现。
