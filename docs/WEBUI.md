# WebUI（2.2.2）

浏览器里用的界面：桌面端起一个**只监听本机**的 HTTP 服务，手机或电脑的浏览器打开即可浏览、搜索、阅读、收藏。

## 怎么启动

```sh
jmnext-desktop --web                # → http://127.0.0.1:17800
jmnext-desktop --web --port=18888   # 端口被占会自动顺延，实际端口会打印
jmnext-desktop --web --lan          # 局域网，会打印 6 位口令
```

## 两套界面，同一套接口

按 `User-Agent` 分流（服务端一次判断，前端不做运行时探测）：

| 访问者 | 布局 | 阅读形态 |
| --- | --- | --- |
| 桌面浏览器 | 侧栏 + 网格 | 横向双页（左右键 / 空格 / 点左右半屏） |
| 手机浏览器 | 底部 tab + 单列 | 纵向滚动（`loading=lazy` + `IntersectionObserver` 记进度） |
| iPad / Android 平板 | 走桌面布局（屏幕够宽） | 横向双页 |

两套共用 `app.js` / `reader.js` / `base.css`，只有布局类与渲染形态不同。

## 为什么必须服务端代理（不是架构偏好）

1. **反切片只能在服务端做**：JM 的图是打乱的分片，浏览器里要么用 Canvas + Worker 重写算法，要么由服务端还原；
2. **防盗链**：图片要求特定 Referer/UA，浏览器直连必被拦；
3. **凭证不能进浏览器**：JWT 只存服务端内存，浏览器只有一个不透明的会话 cookie。

所以**前端永远拿不到 JM 的原始图片地址**，只拿 `/img/<chapter>/<index>`。
`WebAppTest.appScriptCoversAllPages` 里有一条断言专门守这个（脚本里出现 JM 域名就失败）。

## 安全边界

| 规则 | 实现 |
| --- | --- |
| 默认只听 `127.0.0.1` | `WebConfig(lan = false)` |
| 局域网必须显式开启 | `--lan` → `0.0.0.0` |
| 局域网必须过口令 | 6 位数字，`POST /api/gate` 换 `HttpOnly; SameSite=Strict` cookie |
| 凭证不落盘 | 每会话一个 `MemoryAuthStore`，JWT 只在内存 |
| 会话不持久化 | 服务重启即失效 |

## 模块与文件

```
web/
  build.gradle.kts            Ktor 3.0.3（CIO）+ 依赖 :shared
  src/main/kotlin/com/jmnext/web/
    WebApp.kt                 module()：路由挂载、UA 分发、静态资源
    WebConfig.kt / WebServer.kt  配置、端口顺延、启动入口
    GateRoutes.kt             局域网口令
    WebSession.kt             MemoryAuthStore / SessionRegistry（每会话一个 repo）
    WebRoutes.kt              登录、首页、搜索、图片代理
    ReaderRoutes.kt           /api/read、/api/progress、/api/history
    DetailRoutes.kt           详情、收藏、设置
    JvmUnscramble.kt          ImageIO 反切片（几何取自 shared 的 ImageUnscramble.bands）
    ImageCache.kt             磁盘缓存 + HttpURLConnection 取图
    FileKeyValueStore.kt      Properties 版 KeyValueStore（整表重写）
  src/main/resources/web/
    desktop.html / mobile.html / static/{base.css,app.js,reader.js}
```

桌面端通过 `kotlin.srcDir("../web/src/main/kotlin")` 与 **`resources.srcDir("../web/src/main/resources")`** 引入 ——
后者漏了会让入口页 500（HTML 不在 classpath），这个坑踩过一次。

## 已验证

- `:web:test` **33 条**：路由、会话与鉴权、UA 分流、图片代理与缓存、反切片纯函数、局域网口令、前端静态内容；
- **真实起服务**：`--web --port=17997` → `/api/health` 200、`/` 200、`/static/base.css` 200、桌面 UA 得 `data-layout="desktop"`、iPhone UA 得 `"mobile"`。

## 未验证（重要）

1. **浏览器里的真实渲染与交互从未目视** —— 环境没有浏览器。翻页手势、`IntersectionObserver`、CSS 断点、表单提交都只经过静态内容检查；
2. **真实登录与真实取图未跑过** —— 需要账号与网络。反切片只在合成图上测过（4 条纯函数测试），没有在真实漫画页上验证过还原效果；
3. **局域网可达性未验证** —— 只验了配置值（`0.0.0.0`）与口令路由，没有第二台设备证明"开了能访问、关了访问不到"；
4. **桌面端 GUI 无 WebUI 菜单项** —— 目前只能靠命令行参数启动。

这几条在 2.2.2 的 CHANGELOG 里也写明了，没有当成"已完成"。
