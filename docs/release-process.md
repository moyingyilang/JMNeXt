# 版本号与出包命名

这份文档定三件事：**产品版本怎么走**、**构建标识怎么写**、**产物文件叫什么**。它们服务不同读者——版本号给用户和包管理器看，构建标识给「关于」页与排障看，文件名给下载页和自动化脚本看。

## 一、产品版本：纯 SemVer

产品版本就是 `MAJOR.MINOR.PATCH`，预发布加 `-beta.N` 或 `-rc.N`。**Android 与桌面端共用同一个号**，不带平台、架构、变体。

这条是硬约束：把平台写进版本号会让比较失效（`2.3.0-android` 与 `2.3.0-linux` 谁大？SemVer 只比较前三段，之后按字符串排，`android` 永远小于 `linux`），也会让「同号发布」这件事失去意义。

### 版本号怎么涨

| 改动 | 涨哪一段 |
| --- | --- |
| 小修复、小功能 | PATCH（`2.3.x`） |
| 中等修复、部分范式调整 | MINOR（`2.x.0`） |
| 大版本、大范式调整 | MAJOR（`x.0.0`） |

**发版前先把拟定的号告诉用户，等确认再打 tag。** 未定稿用 `-beta.N` 或 `-rc.N`，GitHub 上标 pre-release。

## 二、构建标识：SemVer 的构建元数据

平台、架构、变体全部塞进 `+` 之后：

```
<产品版本>+<平台>.<架构>.<变体>
```

例：

| 目标 | 构建标识 |
| --- | --- |
| Android 完整版 release | `2.3.0+android.universal.full.release` |
| Android 精简版 release | `2.3.0+android.universal.lite.release` |
| Android 完整版 debug | `2.3.0+android.universal.full.debug` |
| 桌面 aarch64 | `2.3.0+linux.arm64.standard` |

关键性质：**`+` 之后不参与版本比较**。所以 `2.3.0+android.…` 与 `2.3.0+linux.…` 相等，都大于 `2.2.9`，既带上了全部构建信息，又不破坏排序。

需要区分同版本的多次构建时，追加 `.build.<日期>.<短SHA>`：

```
2.3.0+linux.arm64.standard.build.20261114.43cd683
```

出现位置：桌面端「关于」页、Android 的 `versionName`、排障日志。**不写进 tag、不写进更新日志标题。**

### Android 的 versionName

`versionName` 直接取构建标识（`2.3.0+android.universal.full.release`），不再用 `.lite` / `.debug` 拼后缀——变体信息只有一处来源。

## 三、Android 的 versionCode

`versionName` 与 `versionCode` 解耦。`versionCode` 用可复算的公式，不再手工递增：

```
versionCode = MAJOR × 10000 + MINOR × 100 + PATCH
```

例：`2.3.0` → `20300`，`2.4.1` → `20401`。

- `MINOR`、`PATCH` 上限 99；超出说明该涨上一段。
- 预发布与正式版共用同一个 `versionCode`（预发布只是提前发，不占号段），靠 `versionName` 区分。
- 历史版本（`2.3.0` 之前）的 `versionCode` 是手工号，保持原值不动。

## 四、产物命名

```
JMNeXt-<产品版本>_<平台>.<架构>.<变体>.<扩展名>
```

`+` 在文件名里换成 `_`：`+` 合法但有些下载站与 URL 处理会把它当空格，实测踩过。**构建标识本身仍是 `+`**，只有文件名用 `_`。

| 产物 | 文件名 |
| --- | --- |
| Android 完整版 release | `JMNeXt-2.3.0_android.universal.full.release.apk` |
| Android 精简版 release | `JMNeXt-2.3.0_android.universal.lite.release.apk` |
| 桌面 deb | `JMNeXt-2.3.0_linux.arm64.standard.deb` |
| 桌面 rpm | `JMNeXt-2.3.0_linux.arm64.standard.rpm` |
| 桌面 AppImage | `JMNeXt-2.3.0_linux.arm64.standard.AppImage` |
| 桌面便携 tar.gz | `JMNeXt-2.3.0_linux.arm64.standard.tar.gz` |

**架构取值**：Android 目前没有 `jniLibs`、也没开 ABI 拆分，一个 APK 覆盖所有设备，记 `universal`；将来按 ABI 拆包就写 `arm64` / `x64`，规则不变。桌面用 `arm64` 与 `x64`（对应内核的 `aarch64` 与 `x86_64`）。

## 五、tag 规则

tag 名是 `v<产品版本>`，例 `v2.3.0`、`v2.4.0-beta.1`。**不含构建标识。**

历史遗留的 `v1.9.001` 这类带前导零的 tag 不符合 SemVer，已归一为 `v1.9.1`（对应的 Release 一并改名）。归一保持提交不变、顺序不变。

## 六、更新日志格式

每个版本固定结构：

```markdown
# 2.4.0（2026-12-01）

一句话摘要：这一版做了什么、为什么。

## 新增
- `[安卓]` ……
- `[桌面]` ……

## 变更
- ……

## 修复
- ……

## 移除
- ……

## 升级注意
- 需要用户手动处理的事。没有就省略整节。

## 已知问题
- 已知没修、但有记录的问题。没有就省略整节。
```

规则：

- **每条都要平台标记** `[安卓]` / `[桌面]` / `[通用]`。2.3.0 出过一次事故：40 个安卓提交被写成「桌面端多语言」，就是因为没有强制标注。
- 标题带发布日期。
- 同一件事只写一次，细节放 `docs/` 并在日志里给一行索引。

## 七、发版清单

1. 确认版本号（先问用户）
2. 更新 `CHANGELOG.md`：**先按目录统计提交构成**（`git log --oneline <上个 tag>..HEAD -- app/` 与 `-- desktop/`），避免只写最近碰过的平台
3. 构建 Android：`full` 与 `lite` 的 release
4. 构建桌面：`desktop/scripts/package-linux.sh`
5. 校验产物名与包内版本（脚本自带自检，任何一项不过就拒绝出包）
6. 打 tag `v<版本>`，写 Release 正文（与 CHANGELOG 同源）
7. 上传产物；要替换旧资产时先确认
