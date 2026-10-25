# :uiweb —— Compose Multiplatform 的浏览器版 UI

目标：把 Android / desktop 的 Compose 界面搬到浏览器，补足 Apple 系（iPhone / iPad / Mac 的 Safari）。

## 版本必须成组（这是最大的坑）

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| runtime / foundation / ui | **1.12.0** | |
| material3 | **1.12.0-alpha03** | 稳定版号落后，1.12.0 不存在 |
| skiko（编译期） | **0.150.1** | 由 compose 1.12.0 决定，用 `gradle :uiweb:dependencies` 实测 |

**skiko 运行时必须与之一致**：从 Maven 下 `skiko-js-wasm-runtime-0.150.1.jar`，
解出 `skiko.mjs`、`skiko.wasm`、`skikod8.mjs`、`js-reexport-symbols.mjs` 放进产物目录。

为什么强调：0.13x 之后 skiko 运行时**不再随 klib 发布**（`skiko-wasm-js` klib 里只有 linkdata 与 `pre-setup.mjs`），
`skiko-js-wasm-runtime` 只发布到 0.150.2，npm 上也没有可用包。版本对不上会在浏览器里报符号错误。
**不要用 1.13.0-beta01**：它依赖 skiko 0.156.0，而 0.156.0 的运行时拿不到。

## 为什么不用 `org.jetbrains.compose` Gradle 插件

在 Gradle 9.8 下与根 `build.gradle.kts` 自注册的 `clean` 冲突，直接失败。
本模块改用官方 `kotlin.plugin.compose` + 直接依赖 `org.jetbrains.compose.*` 制品。

## 为什么不用 `wasmJsBrowserDistribution`

它要 webpack，进而触发 `:kotlinWasmNodeJsSetup` 去解析 `org.nodejs:node:<version>`；
该制品不在 Maven Central（在 nodejs.org/dist），`kotlin.js.nodejs.download=false` 也不生效。
改用链接任务直接产出 `.wasm` 与 `.mjs`：

```sh
gradle :uiweb:wasmJsDevelopmentExecutableCompileSync
# uiweb/build/compileSync/wasmJs/main/developmentExecutable/kotlin/
```

## 内存要求

链接 wasm 需要 4 GB 以上的堆。`gradle.properties` 现在给 `-Xmx6g`，
并设 `kotlin.compiler.execution.strategy=in-process`（避免再起一个 Kotlin daemon 重复吃内存）。
1.5 GB 时会报 `OutOfMemoryError: GC overhead limit exceeded`。

## 浏览器侧必须自己补齐的两处

1. **import map**：产物里有裸模块 `@js-joda/core`，浏览器不认。`index.html` 里映射到
   `vendor/js-joda.esm.js`（jsdelivr 的 5.6.3 ESM 构建）。**缺这一步的症状是纯黑屏。**
2. **skiko 运行时文件**：见上表。

## 起服务

`tools/serve-web.mjs` 一个进程做四件事：

```sh
node uiweb/tools/serve-web.mjs <产物目录> 17803 17800
```

1. 静态托管产物（`.wasm`/`.mjs` 走 **gzip**，22 MB → 约 5.1 MB）；
2. `/api/*`、`/img/*` 反向代理到 Ktor —— 前端只打同源，**不涉及 CORS**；
3. `/legacy/*` 剥前缀转发给 Ktor 的 `/`，即**手写版 WebUI**（可用兜底）；
4. `/` 是 wasm 版。

## 自检工具

```sh
node uiweb/tools/check-imports.mjs <产物目录>
```

递归解析所有 import（静态 + 动态），逐个确认文件存在、裸模块有 import map 覆盖。
前面两次“黑屏/加载失败”都是这类问题，这个脚本能提前查出来。

## 当前进度

已完成：登录、首页、详情（含收藏）、收藏列表、设置占位、阅读占位；页面栈用状态机。

未完成：阅读页图片渲染与手势、设置项读写、iOS 安全区与地址栏适配、production 体积优化。

**未验证**：本环境没有浏览器，wasm 渲染从未目视；Safari / iOS 行为完全未验证，须用户实机确认。
因此 `/legacy` 提供手写版作为可用兜底。
