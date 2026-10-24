# android-termux-builders4JMNeXt

从 **Termux** 出发，在 Linux 容器（chroot / proot）里构建 Android 项目、并以**产物形态**验收的一套脚本。
抽自 [JMNeXt](https://github.com/moyingyilang/JMNeXt)（原名 JMComic_Next）的日常构建流程。

## 它解决什么

在 Termux 上做 Android 开发，真正的麻烦不是写代码，而是这几件事反复发生：

1. **容器里看不到宿主机路径**：绑定挂载掉了，报错是 `cd: 目录不存在`，很容易误判成项目被删；
2. **`debug` 构建不跑 R8**：跨模块重复类、缺类、签名配置错误全查不出来，直到你要出包才炸；
3. **"看着成功其实没出包"**：构建失败时旧产物还在原地，`ls` 一看有文件就以为成功；
4. **`aapt2` 的架构**：build-tools 里可能同时有 x86_64 与 arm64 的 `aapt2`，在 arm64 设备上跑错那份会报
   `Exec format error`；而 `apksigner` 是脚本 + jar，Termux 侧也能跑；
5. **签名密钥**：`keystore.properties` 与 `*.jks` 绝不能入库，本工具只把 `~/.android` **挂载**进容器。

## 用法

```bash
cp env.example build.env   # 按自己的环境改：项目路径、容器根、JDK/Gradle、Android SDK、build-tools 版本
./10-enter.sh              # 进入构建环境 + 绑定挂载自检（含容器内可见性检查）
./20-build-android.sh      # 打 release 包（full/lite）+ 产物新鲜度自检（时间戳必须当天）
./30-verify-apk.sh         # 验收：包名、版本、签名（aapt2 在容器内跑）
```

`build.env` 已在 `.gitignore` 里；仓库只保留 `env.example`。

## 已验证 / 未验证

- **在作者机器实测通过**：`10-enter.sh`（容器内可见项目与 Android SDK）、`30-verify-apk.sh`
  （打出 `package: name='com.jmnext' versionCode='31' versionName='2.0.0'`，`apksigner` 通过）；
  `20-build-android.sh` 的流程与判据取自同一次真实出包（含 R8 修好后的完整构建）；
- **未验证**：其他机器 / 其他发行版 / proot 而非 chroot / x86_64 宿主机 / Windows 与 macOS 的对应做法；
- **已包含**：Compose Desktop 跨架构打包的**原理文档**（`docs/DESKTOP-PACKAGING.md`：目标架构 jmods 经 jlink 生成运行时、
  Skiko 原生库替换并删除宿主那份、jpackage 不能跨平台生成启动器故改用脚本启动器、Windows ZIP 的四样必备内容）与
  **产物验收脚本**（`50-verify-desktop.sh`，对真实产物实测通过）；
- **未包含**：desktop 的打包脚本本身（含项目专属命名与图标，仍留在项目仓库里）；其他架构组合（如 x86_64 宿主）未验证。

## 许可

**MPL-2.0**（见 `LICENSE`）：按文件传染 —— 修改了本仓库的文件，那些文件及其修改需以 MPL 公开；
把本工具当工具使用、或与自己的代码放在别的文件里，则不受约束。与 GPL/AGPL 兼容（MPL §3.3）。

---

## 本仓库里的这份是内置副本（vendored）

上游独立项目：<https://github.com/moyingyilang/android-termux-builders4JMNeXt>（**MPL-2.0**）。
本目录与上游保持一致（`10-enter.sh` … `40-package-desktop.sh` 逐字节相同），只有下面几处按本仓库情况调整：

| 项 | 上游 | 本仓库 |
| --- | --- | --- |
| 配置模板 | `env.example` | `build.env`（脚本按这个文件名读） |
| `50-verify-desktop.sh`、`verify-apk.sh` | 上游版本 | 有本地改动（适配本仓库的产物路径） |
| `desktop/scripts/package-*.sh` | 不在上游 | 本仓库自带（9 个，Linux/Windows 各形态），同样 MPL-2.0 |

**许可证边界**：应用代码（`app/`、`shared/`、`desktop/src`）是 **AGPL-3.0**；
构建/打包脚本是 **MPL-2.0**（文件级 copyleft，不把使用者卷进强传染性）。两者刻意并存。

### 本仓库侧的两个坑（上游脚本不会替你记）

1. **部分 desktop 打包脚本不自带 `cd`**：必须从 `desktop/` 目录运行，否则报
   `src/main/kotlin/com/jmnext/desktop/Version.kt: No such file or directory`；
2. **`package-windows-arm64.sh` 曾把版本正则写死为 `1\.9\.[0-9]+`**，在 2.1.x 上匹配不到会让
   `VERSION` 变空、脚本**静默退出码 1**（Windows arm64 ZIP 因此长期缺产物）。已改为通用正则 ——
   如果上游也有同类写法，建议同步修掉。
