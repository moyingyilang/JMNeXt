# 致谢与来源

这份文档记录 JMNeXt 在实现过程中**实际参考过**的外部来源，以及为什么参考。宁可写少，不写没真正用过的。

## 一、接口与协议

### `hect0x7/JMComic-Crawler-Python`（社区爬虫库）

<https://github.com/hect0x7/JMComic-Crawler-Python>

- **用途**：追踪官方客户端的接口变更。它的 release 说明会写明「更新 APP 版本至 x.y.z」，是少数能公开查到官方版本号的来源。
- **用到的具体内容**：PR #590（v2.7.10）—— 官方 2.1.12 的三项改动（`APP_VERSION`、章节 ID 取详情自动重定向父本子、收藏 Toggle 状态不匹配回滚）。我们的 `DEFAULT_CLIENT_VERSION`、`JmRepository.album()`、`toggleFavorite(expected=…)` 就是照这三点做的，边界与取舍写在 `docs/OFFICIAL-PARITY.md`。
- **没有用到的**：它的下载器、插件体系、CLI 与测试代码，我们一行都没抄。我们只用它当作"官方改了什么"的**信息源**。

### 官方 Web 端的打包产物（本项目内副本 `JMComic_SRC`）

用于**接口还原**：类型定义（`InterFace.ts`）、本地阅读进度的键设计（`Read.tsx` 把 `{comicId: [chapterId…]}` 存 localStorage）、搜索「最旧」排序在本地重排（`Search.tsx` 的 `isLocalOldest`）、以及广告**全部**来自两个客户端主动请求的审计结论（见 `shared/.../AdBlocker.kt` 注释）。

这类地方我们只在注释里写明"官方是这么做的"，代码是自己的实现。

## 二、动效设计

见 `docs/MOTION-SOURCES.md` 的完整记录。要点：

- **Material Design 3 · Easing and duration tokens** —— <https://m3.material.io/styles/motion/easing-and-duration/tokens-specs>（页面需 JS 渲染，**正文没取到**，只作方向参考）
- **Compose 动画自定义文档** —— <https://developer.android.com/develop/ui/compose/animation/customize>
- **Apple HIG 的 motion 部分** —— 只作方向参考，**没有原文数值**

**必须说清楚的一点**：项目里 `JmMotionTable` 的 14 个 token 是**我们自建的**，不是照抄 Material 或 Apple 的规格。四套风格（Material / Miuix / HyperOS / Plasma）的 `fast/base/slow` 比例与退出倍率都是自定的，依据是"退出要快于进入"这类可判定原则，而不是抄参数。

## 三、依赖与工具链

| 组件 | 用途 |
| --- | --- |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | 语言与序列化 |
| Compose Multiplatform / Compose Desktop | Android 与桌面 UI |
| OkHttp / Retrofit | 网络层 |
| Coil | 图片加载（含 `file://` 本地壁纸） |
| Ktor | 2.2.2 起的本地 WebUI 服务 |
| Gradle | 构建 |

各依赖的许可证以各自仓库的 LICENSE 为准；本项目不修改它们的源码。

## 四、同类项目（仅作对照，未取代码）

- `JMComic-APK` 等第三方客户端 —— 只在"官方接口长什么样"这一层互相印证；
- 本项目与官方**无关**，禁漫 / JMComic 及相关内容的权利归其权利人所有（见 README 的第三方重实现声明）。

## 五、我们没有做的事

- 不抄 GPL/闭源项目的代码；
- 不绕过官方付费或广告机制（本项目不含官方广告，也不含购买功能）；
- 不把用户凭证或阅读记录上传到任何第三方服务。
