# 动效约定（MOTION）

改动动效前先读这里。**注意：两端的 token 体系不同，不要以为只有一套。**

## 一、Android：token 在主题里（既有体系）

定义在 `app/src/main/kotlin/com/jmnext/ui/theme/ThemeStyle.kt`，**按视觉风格各带一套**动效参数
（`motion.base`、`motion.fast`、`motion.springy` 等），因此换风格时动效手感会跟着变 —— 这是刻意的设计。

使用处共 5 个文件，其中：
- `ui/JmNavHost.kt`：页面转场（含 `tabSlideDurationMs(motion)`）与顶部栏；
- `ui/components/Glass.kt`：玻璃层动画（`springy` 用弹簧、否则 `fast`）；
- `ui/components/FloatingBottomBar.kt`：底部栏拖拽/吸附；
- `ui/SharedTransition.kt` + `ui/JmNavHost.kt` 的 `SharedTransitionLayout`：**共享元素（封面 → 详情）已经实现**。

结论：**Android 侧不需要新建 token**。此前诊断里"Android 动效散落、缺 token、无共享元素"的判断是错的 ——
那是按 `tween(`/`spring(` 的出现次数统计得出的，没看出它们是 `motion.*` 驱动的风格化数值。

## 二、桌面端：本轮新建的 `Motion.kt`

`desktop/src/main/kotlin/com/jmnext/desktop/Motion.kt`，单一套：

| token | 值 | 用在哪 |
| --- | --- | --- |
| `QUICK_MS` | 120 | 点击、悬停、按下反馈（要跟手） |
| `NORMAL_MS` | 220 | 淡入淡出、展开收起 |
| `PAGE_MS` | 280 | 整页切换（要看清过程） |
| `IMAGE_MS` | 260 | 图片从占位到实图 |
| `Standard` / `Enter` / `Exit` | 三条贝塞尔 | 常规 / 进场 / 退场 |
| `gentleSpring()` | 低阻尼、中低刚度 | 需要轻微回弹处 |

桌面端**没有**"按风格切换动效"的需求（风格只换配色与模糊），所以只有一套。
两端数值**不要求相同**：Android 的历史数值是按手机手感调的，桌面是新定的；要统一时先有真机对比再说。

## 三、本轮改动与状态

| 项 | Android | 桌面端 |
| --- | --- | --- |
| 图片淡入 | 全局 `ImageLoader.crossfade(true)`（Coil 内部时长） | `ComicCover` 走 `IMAGE_MS` |
| 列表项动效 | 既有（`animateItem` 4 处） | 本轮新增 9 处 `animateItem()` |
| 交互反馈 | 既有（Material3 涟漪） | 本轮新增 `Modifier.jmClickable()`（悬停放大 + 按下回缩） |
| 页面转场 | 既有（`JmNavHost` + `motion.*`） | 本轮新增 `Main.kt` 的 `AnimatedContent` |
| 共享元素（封面→详情） | **已有**（`SharedTransitionLayout` + `sharedElement`） | **未做**（下一项） |

## 四、未验证说明

以上我只验证到"**编译通过、调用链正确**"。**手感（快慢、幅度、是否跟手）没有任何真机/真桌面观察证据**，
需要使用者确认。另外有一类编译器查不到的坑：`AnimatedContent` 块内若误用外层状态而非动画提供的
`target`，动画会"空转"（新旧两帧渲染同一页面）——本轮已在桌面端踩到并修正。

## 五、桌面端共享元素（已接线）

**状态：已接线。** 根部由 `SharedPageHost` 建一层 `SharedTransitionLayout`，并通过 CompositionLocal
提供两个作用域（共享作用域 + 页面动画作用域）；列表封面与详情封面各用**同一个** `jmCoverKey(漫画 id)`
登记，因此打开详情时封面从列表位置连续过渡到详情位置。

实现位置：

| 位置 | 做了什么 |
| --- | --- |
| `Main.kt` | `SharedPageHost(this@AnimatedContent) { when (val s = target) { ... } }` —— 包住整个路由分支 |
| `SharedElement.kt` | 提供 `LocalSharedScope` / `LocalPageVisibility` 与 `Modifier.jmSharedElement(key)`；拿不到作用域时退化为"什么都不做" |
| `ComicCover.kt` | 封面 modifier 链加 `.jmSharedElement(jmCoverKey(item.id))` |
| `DetailScreen.kt` | 详情封面加 `.jmSharedElement(jmCoverKey(d.id))` |

**两个坑（都吃过）**：
1. modifier 链**中间的行不能带逗号**（逗号只属于参数表最后一项），否则整段语法错误；
2. 只做一半（提供了共享作用域但漏了页面动画作用域）**编译照样通过、功能静默失效** ——
   因为缺作用域时是安静退化为空操作。所以接线必须两处都在，且用 grep 断言而不是只看编译。

**未验证**：封面过渡的实际观感（位置、缩放、快慢是否自然）没有任何真机/真桌面观察记录。
若观感不对，回退到 2.1.0 的近似效果即可。

---

## 六、体系化升级（2.1.9 之后）

这一节是**索引**：原先的曲线体系（`JmEasing`）与桌面端 `Motion.kt` 仍然有效，
但"用多长、进还是出、哪些必须更快"现在集中到了 token 层，三份文档各管一段：

| 文档 | 管什么 |
| --- | --- |
| 本文（`MOTION.md`） | 历史：曲线体系的由来、桌面端 `Motion.kt` 的建立过程 |
| `MOTION-EXPRESSIVE.md` | **契约**：三条判断标准、11 类交互的时长/曲线表、三条可判定硬约束、帧率实测数据（含失败记录） |
| `MOTION-SOURCES.md` | **来源与边界**：哪些原则有依据、哪些我其实没验证到 |

代码落点：

| 层 | 文件 | 说明 |
| --- | --- | --- |
| token | `ui/theme/JmMotion.kt` | `JmMotionTable(MotionSpec)`：从各风格 fast/base/slow 派生 14 个交互 token |
| 退化 | `ui/theme/ReduceMotion.kt` | 读系统动画缩放，退化为 120ms 淡入 |
| 反馈 | `ui/MotionFeedback.kt` | `Modifier.jmPressScale()`（0.97 缩放，共用 interactionSource） |
| 守门 | `test/.../JmMotionTest.kt` | 四套风格 × 三条硬约束 + 风格身份不被抹平 |
| 守门 | `test/.../MotionAuditTest.kt` | 禁止在 UI 里写死 `tween(...)`/`spring(...)`（白名单逐条写理由） |
| 桌面 | `desktop/.../Motion.kt` | `MotionTokens`：与 Android 同比例（0.72/0.87/0.83/0.75） |

**已接入的动效**：页面转场（含 reduce-motion 退化）、阅读器工具栏显隐、共享元素 hero、随机页旋转、卡片按压、卡片进入。
**未接入**：`dialogIn/Out`、按钮类按压、`listExit`（需要有"移除"场景才有意义）。

**测量现状**：滚动 / 底栏 / 前进 / 返回有实测数字（95 分位 10 / 12 / 14 / 16ms，后两项样本量为 1），
阅读器工具栏与 hero 的帧率**仍未测到** —— 用 input 事件测转场已被证伪（九次失败，记录在 `MOTION-EXPRESSIVE.md`），
下一步应改用 instrumentation（`mainClock` + `FrameTimingMetric`）。
