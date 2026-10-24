# buildkit：本项目的构建与出包工具链

这套脚本负责把源码变成可发布的产物 —— Android APK、桌面端 deb/rpm/AppImage/exe/ZIP。
它们和源码一起开源（与仓库同为 **AGPL-3.0**，见仓库根 `LICENSE`）。

## 为什么单独抽出来

2.0.0 期间出过两次"改了代码但打出来的包是旧的"，都靠人肉发现，代价是用户白跑一趟。
所以这里的每一步都带**产物自检**：不过检查就拒绝出包。这不是可选项。

## 文件

| 文件 | 作用 |
| --- | --- |
| `build.env` | **配置模板**：项目根目录、容器根、容器内 JDK/Gradle、Android SDK、要挂载的宿主机目录、打包任务名。复制成本地 `build.env` 后按自己的环境改（**不要提交个人路径**） |
| `10-enter.sh` | 进入构建容器（chroot/proot），把需要的宿主机目录 bind 进去 |
| `20-build-android.sh` | 打 Android release（full + lite，走 R8），并检查 APK 是不是**今天**产出的 |
| `30-verify-apk.sh` | 验包名与签名 |
| `40-package-desktop.sh` | 跑桌面端打包脚本，产出 linux/windows 各形态 |
| `50-verify-desktop.sh` | 验桌面端产物（能启动、版本号正确等） |
| `verify-apk.sh` | 单独验 APK 的辅助脚本 |

桌面端各形态的打包细节在 `desktop/scripts/package-*.sh`（Linux 出 tar.gz/deb/rpm/AppImage，
Windows 出 ZIP / 免装 Java ZIP / 单体 exe，含 x64、arm64、架构自适应三类）。

## 已知约束

- **必须在 Linux 容器里跑**：需要 glibc 的 JDK 与 `dpkg-deb` / `rpmbuild` / `mksquashfs` / `jpackage`；
- `build.env` 里的 `PROJECT_DIR` 等路径**与具体机器绑定**：本仓库内的模板用的是通用示例，
  换机器要改；脚本发现"宿主机上找不到项目"就是这个没改；
- **部分 desktop 打包脚本要求工作目录是 `desktop/`**（它们不像 `package-linux.sh` 那样自己 `cd`），
  从仓库根直接跑会报找不到 `src/main/kotlin/.../Version.kt`；
- 构建产物只放 `dist*/` 与 `build/`（已在 `.gitignore`），**不入库**；发版时作为 Release 附件。

## 许可证

与仓库一致：**AGPL-3.0**。

> 
