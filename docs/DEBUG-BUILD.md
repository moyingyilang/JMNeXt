# 调试包（tracer）与日志

「调试包」不是另一套构建，是同一份代码换了可观测性配置：

| 平台 | 产物 | 日志位置 | 与正式包的差别 |
| --- | --- | --- | --- |
| Android | `Android-full-tracer.apk` / `Android-lite-tracer.apk` | 应用私有目录 `files/logs/crash-*.txt` | 包名 `com.jmnext.tracer`、可调试、不混淆、`BuildConfig.DEBUG_LOG=true`、崩溃落盘 |
| Windows / Linux | 与正式包相同的 zip / tar.gz | `~/jmnext.log`（tee 标准输出与错误） | 运行时加 `--debug-log` 或环境变量 `JMNEXT_VERBOSE=1` 开详细日志 |

## Android tracer

- 与正式包**可并存**（`applicationId` 后缀 `.tracer`），不影响日常使用；
- 崩溃写入 `filesDir/logs/crash-<时间>.txt`，字段含：时间、版本号与 versionCode、
  厂商/机型/API 级别、线程名、完整堆栈；只保留最近 10 份；
- 保留系统原有 `UncaughtExceptionHandler`，写完继续交给它（系统仍会正常处理崩溃）；
- 正式包 `DEBUG_LOG=false`，不写盘、不额外开销。

安装：

```sh
adb install -r Android-full-tracer.apk
```

取日志：

```sh
adb shell run-as com.jmnext.tracer ls files/logs
adb shell run-as com.jmnext.tracer cat files/logs/crash-*.txt
```

（无 adb 时，用文件管理器进入 Android/data 下的应用目录取，或由用户在设置页导出。）

## 桌面端（Windows / Linux）

桌面端**本来就有**日志设施，不额外出包：

- `Log.init()` 把 `System.out` / `System.err` 以 tee 方式同时写到终端与 `~/jmnext.log`；
- 启动即记录环境摘要（系统/版本/架构/运行时/图形后端/字体）；
- 未捕获异常会额外写一段「崩溃」区块（线程名、异常类型、环境摘要、完整堆栈）；
- 详细日志：`JMNEXT_VERBOSE=1`（或 `-Djmnext.verbose=true`）；
- 同一类错误用 `errorOnce` 聚合，避免同一 URL 反复失败把日志刷成上千行。

因此「debug 分支」在桌面端＝**同一份产物 + 打开详细日志**，不再单独出构建，
避免多出四套几乎相同的包（也更不容易出现"用户下错包"）。

## 为什么 Android 需要单独的包

Android 的崩溃日志在**应用私有目录**，普通用户拿不到；而 `logcat` 会被系统回收。
所以需要一个可长期安装、崩溃自动落盘的变体；桌面端日志本来就写在用户主目录，用同一份包即可。
