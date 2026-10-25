# :uiweb —— Compose Multiplatform 的浏览器版 UI

目标：把 Android / desktop 的 Compose 界面搬到浏览器，补足 Apple 系（iPhone / iPad / Mac 的 Safari）。

## 为什么不用 `org.jetbrains.compose` Gradle 插件

在本项目（Gradle 9.8 + 根 `build.gradle.kts` 曾自注册 `clean`）里，该插件会报
`Cannot add task 'clean' as a task with that name already exists` 而直接失败。
因此这里**直接依赖 `org.jetbrains.compose.*` 制品**，配合官方 `org.jetbrains.kotlin.plugin.compose`。

版本必须成组使用：`runtime` / `foundation` / `ui` / `material3` 都是 **1.13.0-beta01**。
注意 `material3` 的稳定线落后（`1.12.2` 不存在），别只改一个。

## 为什么不用 `wasmJsBrowserDistribution`

它要 webpack，进而触发 `:kotlinWasmNodeJsSetup` 去解析 `org.nodejs:node:<版本>`。
这个制品不在 Maven Central，`gradle.properties` 的 `kotlin.js.nodejs.download=false` 也不生效，
所以在离线/受限环境里必然失败。

改用**链接 + 同步**任务，直接产出 `.wasm` 与 `.mjs`：

```sh
gradle :uiweb:wasmJsDevelopmentExecutableCompileSync
# 产物：uiweb/build/compileSync/wasmJs/main/developmentExecutable/kotlin/
#   JMNeXt-uiweb.wasm / .mjs / .import-object.mjs / .js-builtins.mjs / custom-formatters.js
```

## 为什么需要 import map

浏览器不认裸模块名（`@js-joda/core`），那是 webpack 的职责。产物里恰好有这一处引用，
所以 `index.html` 里用 import map 映射到本地 `vendor/js-joda.esm.js`（从 jsdelivr 取的 5.6.3 ESM 构建）。
**缺这一步的典型症状就是纯黑屏**，控制台报 `wasm not remapped to anything`。

## 怎么起服务

`tools/serve-web.mjs` 一个进程干三件事：

1. 静态托管产物目录；
2. 把 `/api/*`、`/img/*` **反向代理**到 Ktor（默认 `127.0.0.1:17800`）——
   于是前端只打同源，**不涉及 CORS**；
3. 对 `.wasm` / `.mjs` 做 **gzip**（22 MB 的 wasm 压到约 5.1 MB，实测）。

```sh
node uiweb/tools/serve-web.mjs <产物目录> 17803 17800
```

## 当前进度与缺口

已完成：登录、首页、详情（含收藏）、收藏列表、设置占位、阅读占位，页面栈用状态机切换。
未完成：阅读页的图片渲染与手势、设置项读写、iOS 安全区与地址栏适配、production 体积优化。

**未验证**：本环境没有浏览器，渲染从未目视；Safari / iOS 行为完全未验证，需用户在实机确认。
