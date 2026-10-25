# 发版运行手册

一次发版的固定步骤，以及踩过的坑。**照这个做，别凭记忆。**

## 0. 前置

- 版本号三处一起改：`app/build.gradle.kts`（versionCode + versionName）、
  `desktop/src/main/kotlin/com/jmnext/desktop/Version.kt`、
  `desktop/build.gradle.kts`（`packageVersion` 与 `Implementation-Version`）。
- `CHANGELOG.md` 顶部加新版本段落（按已发布的口吻写，含"未验证"清单）。
- 桌面端打包脚本靠 **读 `Version.kt` 的最后一行**取版本号，所以三处必须同时改，
  否则会出现"包名 2.2.2、内容 2.2.1"。

## 1. 出包（15 个）

| 平台 | 命令 | 产物 |
| --- | --- | --- |
| Android | `:app:assembleFullRelease :app:assembleLiteRelease` | 2 个 APK |
| Windows fat jar | `gradle fatJar -Ptarget=windows-arm64 fatJar -Ptarget=windows-x64` | 2 个 jar |
| Linux aarch64 | `scripts/package-linux.sh` | 4 个 |
| Linux x86_64 | `scripts/package-linux-x64.sh` | 4 个 |
| Windows | `package-windows-arm64.sh`、`-x64.sh`、`-arm64-exe.sh`、`-x64-exe.sh`、`-universal-exe.sh` | 5 个 |

**Windows 的 exe 脚本必须先有对应的 fat jar**，否则会立刻失败。

## 2. 归集与核对

`stage-*.sh` 把产物映射成 15 个规范名，并打印缺失清单。**实到不等于 15 就停，不要发。**

## 3. tag 与上传

```sh
git tag -a vX.Y.Z -m "..."
git push origin main vX.Y.Z
```

## 4. 三个反复踩的坑

### 坑一：`--notes-file` 忘了生成

`gh release create --notes-file .work/release-X.Y.Z.md` 在文件不存在时会**直接失败**，
Release 根本没建。此时后续 `gh release upload` 全部报 `release not found`，
而如果脚本把输出管道接到 `tee`，**`$?` 是 tee 的退出码（永远 0）**，
于是每条都打印"成功" —— 表面 15/15，实际 Release 里一个都没有。

**核对必须查远端**：

```sh
gh release view vX.Y.Z -R <owner>/<repo> --json assets --jq '.assets|length'   # 必须 15
```

不要相信脚本打印的"成功"。

### 坑二：`pkill -f <串>` 会杀掉自己

命令行里含同一个串时，`pkill -f` 会匹配到自己。用字符类绕开：

```sh
pkill -f "release-22[2]"      # 匹配 release-222，但不匹配本行
```

### 坑三：杀流水线要连子进程

只杀父脚本会留下 Gradle 进程占着配置缓存锁，下次构建报
`Timeout waiting to lock Configuration Cache`。杀父进程后要连 `pkill -P` 一起杀，
必要时删 `.gradle/configuration-cache/configuration-cache.lock`。

## 5. 发完核对

1. 远端附件数 = 15，逐个名字对得上；
2. `git rev-parse vX.Y.Z^{commit}` 指向含新 versionCode 的提交；
3. `gh release view --json body --jq '.body|length'` 不为 0（正文没丢）；
4. 装 Android 包装到手机，核对包名 / versionCode / versionName / 大小。
