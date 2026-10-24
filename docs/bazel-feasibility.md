# 迁移到 Bazel 的可行性评估

> 评估时间：本项目 v2.x 阶段。只基于**仓库里实际存在的东西**判断，不凭对 Bazel 的印象。

## 一、要迁的东西长什么样

| 模块 | 类型 | 关键构建特性（证据） |
| --- | --- | --- |
| `:app` | Android 应用 | AGP + Compose + kotlinx.serialization；**product flavor**（`full`/`lite`，`app/build.gradle.kts:91-104`）；`buildConfigField("LITE")`；按 flavor/type 拼 `versionNameSuffix`（`:127`）；`composeCompiler {}` 配置（`:190`）；`androidComponents` 块（`:123`）；flavor 专属资源覆盖（`app/src/lite/res/...`） |
| `:shared` | 纯 Kotlin/JVM | `kotlin("jvm")` + serialization（`shared/build.gradle.kts:6-8`），无 Android 依赖 —— **最适合 Bazel 的部分** |
| `:desktop` | Kotlin/JVM + Compose Desktop | `org.jetbrains.compose` 1.12.1（`desktop/build.gradle.kts:7`）+ 自定义 `fatJar`/`smoke` 任务（`:103`/`:72`） |
| 打包 | 与 Gradle **无关** | `desktop/scripts/package-*.sh` 用 dpkg-deb/rpmbuild/mksquashfs/jpackage 出 deb/rpm/AppImage/exe —— 这部分迁移成本为零 |
| 代码生成 | **只有编译器插件** | 无 KSP、无注解处理（全仓库搜不到 `ksp(`）；只有 Compose 与 serialization 两个 **Kotlin 编译器插件** |

依赖由 `gradle/libs.versions.toml` 统一管理（约数十项）。

## 二、逐块可行性

| 能力 | Bazel 侧现状 | 可行 | 代价 |
| --- | --- | --- | --- |
| Kotlin/JVM 编译 | `rules_kotlin` | 是 | 小 |
| kotlinx.serialization 插件 | rules_kotlin 支持 Kotlin 编译器插件 | 是 | 小（需按 Kotlin 2.4.20 对齐插件 jar） |
| Compose 编译器插件 | 同上（`kotlin-compose` 本质是编译器插件） | 是 | 小到中（版本必须与 Kotlin 严格匹配） |
| Compose Desktop（skiko 等平台原生 jar） | `rules_jvm_external` 可锁定 maven 依赖 | 是 | 中（平台专属 artifact 需人工 pin） |
| 单元测试（JUnit） | rules_kotlin / java_test | 是 | 小 |
| **Android 资源 + R 类 + manifest 合并** | `rules_android` 有 `android_binary`，但资源与 manifest 处理能力长期落后 AGP | 勉强 | **大**（且出问题时要自己写 action） |
| **product flavor（full/lite）** | 无原生概念，要用 `--define`/`config_setting` + `select()` 手工模拟 | 勉强 | **大**（还要复制 flavor 专属资源目录语义） |
| `buildConfigField` | 无；要自己生成 BuildConfig 源 | 勉强 | 中 |
| AGP 的 `androidComponents` / `composeCompiler {}` 微调 | 无对应物（很多是 AGP 内部行为） | **不可** | — |
| APK 签名/v1-v3、aapt2 版本钉死（本项目因 SDK 差异钉过 aapt2） | 需自行接 toolchain | 勉强 | 中 |
| Android 仪器测试 / Compose UI 测试 | rules_android 支持弱 | **不建议** | 大 |
| Android Studio 内的开发体验 | 无完整 AS 支持（AS 以 Gradle 为一等公民） | **不可** | — |

## 三、收益评估（关键）

Bazel 的强项是「**多语言/超大单体仓库 + 远程缓存 + 严格 hermetic**」。本项目的实际形态：

- 只有 **2 个 Gradle 模块 + 1 个桌面模块**，单一语言（Kotlin），没有原生代码、没有跨语言依赖图；
- 构建耗时主要在 **AGP 的资源/打包与 Compose 编译**，这两块 Bazel 都不比 AGP 快（反而更慢，因为社区规则没 AGP 的增量优化）；
- 现在已经能吃 Gradle 的 **configuration cache + build cache**（收益与远程缓存同源）；
- 迁移后要同时维护两套构建直到完全切换，而 **发版脚本、CI、AS 调试**都绑在 Gradle 上。

**结论：净收益接近零，净成本很高。**

## 四、若仍然要做：推荐的渐进路线

1. **只迁 `:shared`**（纯 JVM，收益/风险最好），用 `rules_kotlin` + `rules_jvm_external`；`:app`/`:desktop` 继续 Gradle 消费它产出的 jar —— 但这样会多一个产物同步环节，**通常不值**；
2. 或者**只给 `:desktop` 做 Bazel**（目标是一个可复现的 fatJar），打包脚本不变；
3. 无论哪条，先写一组**等价性断言**（同一 commit 下 Gradle 与 Bazel 产出的 APK 校验和/清单一致），否则无法判断迁移是否引入回归。

## 五、如果目标是"构建更快更稳"，更便宜的替代方案

| 目标 | 更省的做法 |
| --- | --- |
| 增量构建慢 | 打开 Gradle **configuration cache + build cache**，把 `org.gradle.parallel/caching` 写进 `gradle.properties` |
| CI 时间 | 缓存 `~/.gradle`，只跑受影响的模块（`--configure-on-demand`） |
| 可复现 | 钉 Toolchain/JDK、锁定依赖（已有 `libs.versions.toml`）、固定 aapt2 与 compileSdk |
| 构建脚本复杂度 | 把 `app/build.gradle.kts` 里的 flavor/命名逻辑抽到 build-logic 约定插件 |

## 六、结论

**不建议现在迁 Bazel。** 具体理由（按重要性）：

1. Android 侧的两大特性 —— **资源/manifest 处理**与 **product flavor** —— 在 Bazel 里都要手工模拟，且 `rules_android` 的成熟度不足以承载"日常开发 + 发版"；
2. 项目规模与语言数量都远低于 Bazel 的收益阈值；
3. `androidComponents`、`composeCompiler {}` 这类 AGP 内部行为**没有对应物**，会直接改变产物行为；
4. 迁移期间需要长期双跑，成本由单人维护承担。

**唯一我会考虑的场景**：将来出现「多个应用共享同一套 data 层、且构建时间成为瓶颈」时，**先迁 `:shared`**（纯 Kotlin），把 Android 留在 Gradle。
