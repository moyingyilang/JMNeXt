# 版本号与出包命名

这份文档定两件事：**版本号怎么走**，以及**产出的文件叫什么**。两者分开，因为它们的读者不同——版本号给用户和包管理器看，文件名给下载页和自动化脚本看。

## 一、版本号：只留一个语义化版本

**决定**：产品版本就是 SemVer 三段式 `MAJOR.MINOR.PATCH`，预发布加 `-beta.N` 或 `-rc.N`。Android 与桌面端**共用同一个号**，不带平台、架构、变体。

不按「JMNeXt + semver + 平台 + 架构 + 变体」把平台写进版本号，理由有三条：

1. **比较会坏**。`2.3.0-android-arm64` 与 `2.3.0-linux-aarch64` 谁大？SemVer 只能比较前两段，第三段之后是字符串排序，`android` 永远排在 `linux` 前，于是 Android 看起来“总是更新”。
2. **升级判断会坏**。Android 的 `versionName` 是给人看的，`versionCode` 才是升级依据；桌面端靠包管理器比较版本。把平台塞进版本号，等于让两者都要先解析字符串再比较，回归风险白送。
3. **同一个功能要在两端同时发**。平台写进版本号后，两端一旦有一边漏发，号就对不齐，用户看到的“版本”不再是产品状态。

平台、架构、变体放进**文件名**与**构建元数据**（见下），不进版本号。

### 预发布

| 后缀 | 含义 | GitHub 标记 |
| --- | --- | --- |
| `-beta.N` | 功能未定，可能回退 | pre-release |
| `-rc.N` | 功能已定，只等验证 | pre-release |

正式版不带后缀。预发布不进 F-Droid 之类的稳定源。

### 版本号怎么涨

沿用用户定的规则：

| 改动 | 涨哪一段 |
| --- | --- |
| 小修复、小功能 | PATCH（`2.3.x`） |
| 中等修复、部分范式调整 | MINOR（`2.x.0`） |
| 大版本、大范式调整 | MAJOR（`x.0.0`） |

**发版前先把拟定的号告诉用户，等确认再打 tag。**

## 二、Android 的 versionCode

`versionName` 与 `versionCode` 分离。`versionName` 是 SemVer（带变体后缀），`versionCode` 用可推导的公式，不再手工递增：

```
versionCode = MAJOR × 10000 + MINOR × 100 + PATCH
```

例：`2.3.0` → `20300`，`2.4.1` → `20401`。

约束：

- `PATCH` 上限 99、`MINOR` 上限 99。超出说明该涨上一段了。
- 预发布与正式版**共用**同一个 `versionCode`：预发布只是提前发，不占号段。要连着发 `-rc.1` 与 `-rc.2` 时，`versionCode` 不变，靠 `versionName` 区分。
- 公式值必须严格大于上一版的 `versionCode`。从手工号（如 `50`）切到公式号（`20300`）时，跳变是预期的，一次性完成。

### 变体后缀

| 变体 | versionName |
| --- | --- |
| full / release | `2.3.0` |
| full / debug | `2.3.0.debug` |
| lite / release | `2.3.0.lite` |
| lite / debug | `2.3.0.litedebug` |

规则保持不变，只把基号从硬编码改成读取同一处版本定义。

## 三、构建元数据

SemVer 允许 `+` 之后挂构建元数据，且**不参与版本比较**——正好放那些「同版本不同产物」的区分信息：

```
2.3.0+build.20261114.92f7147
```

组成：`build.<日期>.<提交短 SHA>`。出现位置：

- 桌面端「关于」页的细节行
- CI 产物文件名（可选）
- **不写进** `versionName`、不写进 tag、不写进包管理器的版本字段

## 四、产物命名

统一格式：

```
JMNeXt-<version>-<platform>-<arch>-<variant>.<ext>
```

四段都小写，用连字符；`+` 构建元数据仅在需要区分同版本多次构建时追加。

| 段 | 取值 |
| --- | --- |
| version | 纯 SemVer，含预发布后缀，不含 `+` 元数据 |
| platform | `android` / `linux` / `windows` / `macos` |
| arch | `arm64` / `x64` / `universal`（没有原生库时） |
| variant | Android 用 `<flavor>-<buildType>`（`full-release`、`lite-release`、`full-debug`）；桌面端单变体用 `standard` |

### 实例

| 产物 | 文件名 |
| --- | --- |
| Android 完整版 release | `JMNeXt-2.3.0-android-universal-full-release.apk` |
| Android 精简版 release | `JMNeXt-2.3.0-android-universal-lite-release.apk` |
| 桌面 deb（aarch64） | `JMNeXt-2.3.0-linux-arm64-standard.deb` |
| 桌面 rpm | `JMNeXt-2.3.0-linux-arm64-standard.rpm` |
| 桌面 AppImage | `JMNeXt-2.3.0-linux-arm64-standard.AppImage` |
| 桌面 便携 tar.gz | `JMNeXt-2.3.0-linux-arm64-standard.tar.gz` |

**arch 说明**：Android 侧目前没有 `jniLibs`、也没开 ABI 拆分，一个 APK 覆盖所有设备，所以写 `universal`。将来若按 ABI 拆包，改成 `arm64` / `x64` 即可，命名规则不用动。

桌面的 `arm64` 对应内核 `aarch64`：文件名用 `arm64`（与 Android 一致、与下载页一致），包内元数据仍写 `aarch64`。

## 五、更新日志格式

`CHANGELOG.md` 每个版本固定结构：

```markdown
# 2.4.0（2026-12-01）

一句话摘要：这一版做了什么、为什么。

## 新增
- `[安卓]` ……
- `[桌面]` ……
- `[通用]` ……

## 变更
- ……

## 修复
- ……

## 移除
- ……

## 升级注意
- 需要用户手动处理的事（数据迁移、包名变化、权限变化）。没有就省略整节。

## 已知问题
- 已知没修、但有明确记录的问题。没有就省略整节。
```

规则：

- **每条都要平台标记**。这是 2.3.0 的教训：40 个安卓提交被写成“桌面端多语言”，因为写的时候没有强制标注平台。
- 标题带发布日期，不写「本次更新」这类没有信息量的前缀。
- 与上一版无关的内容不写；「顺手改了文档」这类没有用户可见影响的改动不必进日志。
- 一节里同一件事只写一次，细节放 `docs/` 并在这里给一行索引。

## 六、发版清单

1. 确认版本号（先问用户）
2. 更新 `CHANGELOG.md`：按上面的结构，**先按目录统计提交构成**（`git log --oneline <上个 tag>..HEAD -- app/` 与 `-- desktop/`），避免只写最近碰过的平台
3. 构建 Android：至少 full-release 与 lite-release
4. 构建桌面：`scripts/package-linux.sh`
5. 校验产物命名与内容（脚本自带自检，任何一项不过就拒绝出包）
6. 打 tag `v<version>`，写 Release 正文（与 CHANGELOG 同源）
7. 上传产物；需要替换旧资产时先确认
