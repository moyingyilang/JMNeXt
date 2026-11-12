# Android 与桌面端功能对齐审计（2.0.0 前）

> 这是 2.0.0 之前的缺口快照，多数已经修完，「结论」那节别直接引用。
> 还差什么看 STATE.md 第十四、十五节（主要剩首页的连载更新表）。

**目的**：查清桌面端到底还缺哪些 Android 已有的功能。起点是用户提出"你确定 Android 版所有功能全移植过去了吗"
—— 此前我只有自己零散记的 `desktop/PARITY.md`，那份日志已被证明**既漏报也误报**（见文末对照）。

**方法（以代码为准，不靠记忆）**：只读核对全部源文件，逐条给出 `文件:行号` 作为证据。
- Android 侧：26 个 screens 文件、`ui/JmNavHost.kt` 路由、`data/prefs/AppPrefs.kt` 可配置项、
  `LiteFeatures.kt`、`AndroidManifest.xml`、`res/`；
- 桌面侧：全部 38 个 `.kt`；
- 共享侧：`shared` 的 data/prefs 与 `JmRepository.kt` 公开 API；
- 交叉参照：`desktop/PARITY.md`（行号级索引）。

**未核实（不得当成已完成）**：运行时行为（本次未构建、未运行）；写操作的真实结果；桌面 `folder_list`
是否稳定拿到夹名；`PARITY.md` 引用的真机日志数据来源。

**一个重要前提**：桌面端**不是**旧日志说的"8 个文件 / 6%"。15 个侧栏入口**全部有实现**
（首页/搜索/分类/周刊/随机/收藏/历史/追更/标签/画师与作品库/通知/屏蔽设置/外观/关于/我的）。

## 严重缺口（8 项，会明显影响使用）

| 缺口 | Android 证据 | 桌面证据 |
| --- | --- | --- |
| 首页无分区「更多」页（含连载更新表：类型 + 星期筛选）；最新列表只加载第 1 页；无刷新；无"你追的更新"标记 | `HomeScreen.kt:75/165/247/324/345`、`MoreListScreen.kt:228/319` | `Main.kt:117-167`、`HomeSections.kt:38`（注释自述未做） |
| 搜索只剩关键词：缺 5 档检索字段、5 档排序、年月筛选、搜索历史、热门标签、随机入口、标签屏蔽"允许一次" | `SearchScreen.kt:356/453/461/471/619/670/773/798/838`、`SearchFilters.kt:29/44` | `SearchScreen.kt:45` |
| 无下载 | `DetailScreen.kt:1054-1058`、`JmRepository.kt:595 albumDownload` | 全项目无 |
| 标签收藏只能看不能改 | `TagFavoritesScreen.kt:122/226`、`JmRepository.kt:582 updateFavoriteTags` | `TagsScreen.kt:47`（本地统计 + 只读） |
| 不能注册 / 找回密码 | `AuthScreen.kt:169`、`JmRepository.kt:300/319` | `LoginScreen.kt:38`（只有登录） |
| 无连载更新提醒（系统通知） | `data/SerialNotify.kt`、`AppPrefs.kt:77`、Manifest 权限/receiver | 无 |
| 详情页不能点赞（只有阅读页能） | `DetailScreen.kt:689/806` | 仅 `ReaderScreen.kt:386` |
| 通知不能标记已读 | `NotificationsScreen.kt:48`、`JmRepository.kt:750` | `NotificationScreen.kt:52` 无 |

## 中等缺口（11 项）

| 缺口 | Android 证据 | 桌面证据 |
| --- | --- | --- |
| 首页无随机/快捷签到浮钮 | `RandomFab.kt:62/127` | 无 |
| 签到缺日历与历史 | `ProfileScreen.kt:1213-1243`、`DailyHistorySection.kt:47` | `ProfileScreen.kt:96-134`（只有按钮） |
| 详情标签不可点、不能屏蔽、不能收藏标签 | `DetailScreen.kt:932/985/1025/1091` | `DetailScreen.kt:190-193`（纯文本） |
| 追更初始态不准 | `DetailScreen.kt:627`、`JmRepository.kt:556 isTracked` | `DetailScreen.kt:72-75`（本地翻转） |
| 无「移入收藏夹」 | `FavoritesScreen.kt:498-510`、`editFavoriteFolder type=move` | `FavoritesScreen.kt:52`（只有 add/edit/del） |
| 创作者库看不到作品内容 | `CreatorWorkScreen.kt:92/168-200 creatorWorkContent` | `CreatorScreen.kt:206`（只用 creatorWorkInfo） |
| 随机页无网格/列表版式切换 | `RandomListScreen.kt:100/209-214`、`AppPrefs.kt:91` | `RandomScreen.kt:55`（仅网格） |
| 分类页缺分组标签 blocks 与热门标签兜底 | `CategoryScreen.kt:499/545` | `CategoryScreen.kt:48` 无 |
| 外观缺档：无「跟随系统」、无动态取色、无动效性格 3 档、**无在线壁纸源与自动更换间隔** | `ProfileScreen.kt:461-496/597-621`、`WallpaperStore.kt:57` | 见下「本次已处理」 |
| 阅读默认形态未进设置页 | Android 有设置项 | `ReaderScreen.kt:90-93`（私有 prefs） |
| 通知未读角标未进导航 | `ProfileScreen.kt:267-276` | 无 |

## 轻微缺口（8 项）

屏蔽设置缺 2 到 20 长度校验与重复提示（`BlockSettingsScreen.kt:48-49/148` vs 桌面 `BlockScreen.kt:87` 无校验，
`:159 alreadyBlocked` 是 `@Suppress("unused")` 死代码）；登录页无"为什么需要登录"提示（`JmNavHost.kt:621`）；
详情章节列表不分页；横翻无缩放；阅读页侧栏与底栏功能重复（`PageRail.kt:56` 与 `ReaderBottomBar.kt:44`）；
`PageShell.kt:25` / `PAGE_PLANS:51` 是死代码（`Main.kt:332-335` 不可达）；缺动效体系；无"我的"页入口聚合。

## 本次已处理（审计期间用户点出的）

**在线壁纸**：桌面端此前只有"预设渐变 + 本地图片 + 模糊压暗"，缺 Android 的整套在线来源
（模式选择、Bing 每日、二次元、自动轮换、署名、自定义地址）。现已补：
`shared/.../data/wallpaper/WallpaperCore.kt`（平台无关语义 + 5 个单测）、
`desktop/.../WallpaperRemote.kt`（取图、缓存轮换、跨天重取、冷却、三源兜底、署名）、
`desktop/.../WallpaperRemoteSection.kt`（界面），并接进 `AppearanceScreen`。

## PARITY.md 对照（以代码为准）

**已过时（日志说未做、代码已做）**：`:11-30` 页面清单表（几乎全写未做，实际全有实现）；
`:15` "阅读缺翻页模式/进度/预加载/重试"（均已做）；`:14` "详情缺收藏/评论/相关/追更"（已做，仍缺下载）；
`:52-62` 收藏页缺口（文件夹切换/新建/改名/删除/取消收藏均已做）；`:59` "共享层没有文件夹列表接口"
（现从响应 `folder_list` 取）；`:87-106` 历史删除已回退（现已实现 `HistoryScreen.kt:110-131`）；
`:219` "追更仍独立页"（桌面已并入 `FavoritesScreen.kt:103-128`，**但 Android 也有独立追更路由
`JmNavHost.kt:690`，原判断不准**）；`:648` 点赞未接（已接 `ReaderScreen.kt:386/446`）；
`:737` 模式与章节置灰（已实现）；`:940-953` 两个横翻缺口（已做）；
`:686-703` 缓存按张数（已改 `RemoteImage.kt:47 CACHE_MAX_BYTES=256MB`）；`:1050` 键盘翻页（已做）。

**三处明确写错**：① "追更在 Android 不是独立页"（实际是）；② 把"亮度控制"当成缺口
（**Android 根本没有亮度控制**，全项目 grep 无 brightness）；③ "共享层没有文件夹列表接口"（实际有来源）。

**日志遗漏、核对代码才发现的缺口**：搜索整套筛选与年月/历史/热门标签；无下载；不能注册/找回密码；
标签收藏只读；`creatorWorkContent` 未接；无连载更新通知；首页无分页；屏蔽缺长度校验。

## 结论

核心还剩：首页（更多 / 分页 / 刷新 / 更新标记）、搜索筛选、下载、标签收藏写操作、注册与找回密码、
连载更新提醒、详情点赞、通知标记已读。**写操作两端都只接了接口，全都没有运行验证。**

## 修复进度（按用户要求"按顺序全部修复"，逐项带提交）

**已完成并提交**（每项都编译通过；提交号可回溯）：

| 项 | 内容 | 提交 |
| --- | --- | --- |
| 1 | 桌面端在线壁纸（模式/Bing 每日/二次元/自动轮换/署名/自定义地址 + 尺寸段改写 + 缓存轮换） | `209479a` |
| 2a | 详情页点赞 + 追更初始态从接口取（`isTracked`） | `c8adc38` |
| 2b | 通知标记已读（`markNotificationRead`） | `eb1fc5a` |
| 2c | 标签收藏写（`updateFavoriteTags` add/remove + 重载） | `33827de` |
| 2d | 登录页补注册与找回密码（三种模式） | `cf3e491` |
| 3a | 搜索整套筛选（排序 5 档 / 字段 5 档 / 年月 / `old` 本地二次排序） | `cd0dafc` |
| 3b | 搜索历史（本地 20 条、最近在前、大小写不敏感去重） | `7a6efa7` |
| 3c | 热门标签 + 随机推荐建议面板（未搜索时显示） | `3bd995e` |
| 4b/4c/4d | 首页分页 + 刷新 + 追更更新集合（来自服务端追更通知） | `cdc8a88` |
| 4d' | 首页刷新/加载更多按钮 + 逐卡「更新」角标 | `f65ab27` |

**未完成（按顺序，接手时从这里继续）**：

| 项 | 内容 | 已摸清的接口/依据 |
| --- | --- | --- |
| 4a | 首页分区「更多」页 + 连载更新表（类型 + 星期筛选） | 共享层 `promoteList(id, page)`、`weekIssues()` / `weekList(issueId, type, page)` / `weeklyUpdate(...)`；Android 页面在 `ui/screens/more/MoreListScreen.kt`，路由 `more/{id}?title=…`（`JmNavHost.kt:148-152`）；桌面导航在 `Main.kt` 的 `Screen.Page(route)` + `when` 分支 |
| 5 | 中等缺口 8 项：随机/签到浮钮、签到日历与历史、详情标签可点可屏蔽、移入收藏夹、创作者作品内容（`creatorWorkContent`）、随机页版式切换、分类分组标签、阅读默认形态进设置页 | 审计表里有逐条 `文件:行号` 证据 |
| 6 | 下载（`albumDownload`） | Android `DetailScreen.kt:1054-1058`、`JmRepository.kt:595` |
| 7 | 连载更新提醒（系统通知） | Android `data/SerialNotify.kt`、`AppPrefs.kt:77`、Manifest 权限/receiver |
| 8 | 算法接进桌面端与 Android 端（预取深度/并发）→ 发 1.9.443 基线（+300）→ 文档整理 → 改名 JMNeXt → 2.0.0 | 算法核心与调参器已完成并单测通过（`a6ba409`、`738ff1e`），离线回放方向判据通过（`8606083`） |
| 另立 | 标签级屏蔽整条链路（`TagBlockResolver` + `TagCache` + 「允许一次」） | 桌面端**完全没有**这套；只加"允许一次"按钮毫无作用（没有东西被标签挡住） |

**跑法提醒（接手必读）**：
- 构建必须进容器：`/data/data/com.termux/files/home/jmc/.work/enter.sh`（chroot 里才有 JDK 与 Gradle）；
- 桌面模块是**独立构建**（`cd desktop && gradle compileKotlin`），根项目只有 `:app` 与 `:shared`；
- Android 端核验任务名要写全：`:app:compileFullDebugKotlin` 与 `:app:compileLiteDebugKotlin`（写 `compileDebugKotlin` 会报 Ambiguous matches）；
- 构建失败时用 `grep -E "^e: "` 看 Kotlin 错误（**不要加 `tail`** —— 报错在输出前部，帮助文本在尾部）；
- 改文件：整行替换或行间插入，**不要在单行内插换行**（曾把 `.clickable { … }` 切断）；改前先断言目标行内容，改后按内容 grep 计数核对；
- `/tmp` 在 Termux 侧**不可写**，临时文件放 `.work/`。

## 一条容易误判的事实：标签级屏蔽是 Android 端已经做过的

本会话早期有三个针对 **Android 端**的任务（会话代理记录里可见）：
`List tag blocking via background fetch`、`Implement list tag blocking`、`Search tag-block notice and allow-once`。
也就是说：**标签级屏蔽与搜索页的「允许一次」在 Android 端已经实现过**，相关代码在
**更正（实测）**：`TagBlockResolver.kt`（208 行）与 `TagCache.kt`（81 行）实际在 **`app/src/main/kotlin/com/jmnext/data/`**，**不在 shared** —— 桌面端只直编 shared 源码，所以这套代码桌面端**编译不到**；`app/.../ui/LocalTagBlocker.kt`（12 行）用 CompositionLocal 把它交给界面，`app/src/test/.../TagBlockResolverTest.kt`（393 行）是它的单测。

而**桌面端完全没有这套**：共享层的 `BlockRules.hides(item)` 只按标题/作者/分类过滤，
标签不在其中（注释写明"接口不给"）；标签级判定需要逐条拉取作品标签再筛，桌面端没有这条链路。

**所以：在桌面端只加一个「允许一次」按钮是没有意义的** —— 没有东西被标签挡住，按钮点了也不会有变化。
要做就得连整条链路一起做（拉标签的缓存 + 判定 + 一次性放行），这是一件独立的事，不是"补一个按钮"。

## 第 6、7 项的实情（避免把工作量估错）

**第 6 项 下载**：共享层只有 `albumDownload(aid): DownloadPayload`（`JmRepository.kt:595`），
Android 端也只在 `DetailScreen.kt:1054-1058` 用了它 —— 也就是说 **Android 端并没有"下载管理器"
（没有队列、没有离线存储、没有下载页）**，只是把下载信息/入口显示出来。
所以桌面端这一步的实际工作是"接上同样的信息与入口"，**比"做一套下载系统"小得多**；
若用户想要真正的离线下载，那是一件新功能（两端都要做），不是"移植缺口"。

**第 7 项 连载更新提醒**：Android 端 `data/SerialNotify.kt`（6040 字节）用
**AlarmManager + BroadcastReceiver + NotificationCompat** 实现，Manifest 里有
`POST_NOTIFICATIONS` 权限与 `.data.SerialNotifyReceiver`。它的代码注释里写明了一个取舍：
"**为什么是 AlarmManager 而不是 WorkManager** —— 项目里原本没有 WorkManager，
不为这一个功能引入新依赖"，这与本项目"不引入大依赖"的取向一致。

桌面端要做同等功能时要注意两点：
1. Compose Desktop 没有 Android 那套通知，可用 **AWT `SystemTray`**（零新依赖）或应用内横幅；
2. **桌面端只能在程序运行时提醒**（除非做成后台服务/定时任务，那是另一件事）——
   Android 可以靠 AlarmManager 在后台唤醒，桌面端不行。这个差异必须对用户讲清楚，不能假装等价。
