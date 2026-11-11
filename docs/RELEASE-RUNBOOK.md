# 发版手册

照这个做，别凭记忆。发版最容易错的地方不是打包，是"版本号写了几处、只改了三处"。

## 前置：把版本号对齐

三处一起改：

- `app/build.gradle.kts`：`versionCode` 和 `versionName`
- `desktop/src/main/kotlin/com/jmnext/desktop/Version.kt`：常量 + 文件头注释
- `desktop/build.gradle.kts`：`packageVersion` 与 `Implementation-Version`

桌面端的打包脚本是拿 `grep` 从 `Version.kt` 里抠版本号的，所以漏改这一处，包名会和内容对不上。

CHANGELOG 顶部补一段，按已经发出去的口吻写，把没验证的也写进去。

## 出包

Android 四个：

    gradle :app:assembleFullRelease :app:assembleLiteRelease
    gradle :app:assembleFullTracer :app:assembleLiteTracer

桌面端走 `desktop/scripts/package-*.sh`，或者用 builder 仓库的 `40-package-desktop.sh` 一次跑完。Windows 的 exe 脚本要求先有对应的 fat jar，没有会立刻失败。

产物按 `<平台>-<架构>-<版本>` 起名，Android 是 `Android-full-<版本>.apk`，lite 和 tracer 同理。

## 先发草稿，传完再解除

    gh release create vX.Y.Z --draft --title "..." --notes-file .work/notes-X.Y.Z.md 产物...

草稿阶段可以反复补传，用户看不到半成品。所有平台的包都齐了再 `gh release edit vX.Y.Z --draft=false`。

## 三个坑

### 坑一：notes 文件不存在，release 静默没建

`gh release create --notes-file` 在文件缺失时直接失败，Release 根本没建。后面所有 `gh release upload` 都报 `release not found`；如果脚本把输出接了 `tee`，`$?` 是 tee 的退出码，永远 0，于是每条都打印成功，看着 15/15，其实一个都没传。

核对要查远端，不要信脚本打印：

    gh release view vX.Y.Z --json assets --jq '.assets|length'

### 坑二：tag 可能指在旧提交上

发版前一定比一次：`git rev-parse vX.Y.Z^{commit}` 和构建产物时的 HEAD 是否一致。改过历史（比如 squash）之后旧 tag 还留着，就会出现"页面是 2.2.3、源码是 2.2.3、tag 指向一个已经不存在的提交"。

删掉重打就行，别在草稿阶段就建 tag。

### 坑三：`pkill -f` 会杀掉自己

命令行里带同一个串就会自匹配。要么用字符类绕开（`pkill -f "release-22[2]"`），要么老老实实记 pid。

杀流水线要连子进程一起杀，只杀父脚本会留下 Gradle 占着配置缓存锁，下次构建报 `Timeout waiting to lock Configuration Cache`。

## 发完核对

1. 远端附件数和预期一致，名字逐个对得上
2. tag 指向的提交 == 构建时 HEAD，且含新 versionCode
3. release 正文非空
4. 装一个 Android 包到手机，核对包名、versionCode、versionName、大小
