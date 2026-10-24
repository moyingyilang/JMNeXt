#!/usr/bin/env bash
# SPDX-License-Identifier: MPL-2.0
# 组装「Windows on ARM 免装 Java」的 ZIP。
#
# 需要两样东西，都不写死在命令里、用变量给出：
#   1) 目标平台的单体 jar：由 gradle fatJar -Ptarget=windows-arm64 产出
#      （内含 skiko-windows-arm64.dll，见 desktop/build.gradle.kts 的 fatJar 任务）；
#   2) jlink 做出来的 Windows 运行时：用 $JRE_DIR 指定，默认 /root/win-arm64-runtime。
#      它必须含 bin/java.exe —— 这一点脚本会先检查，不像 Windows 就直接停下，
#      免得打出一个"看着像成品、其实跑不起来"的包。
#
# 用法：在 desktop/ 目录下执行 scripts/package-windows-arm64.sh [输出目录]
set -euo pipefail

JRE_DIR="${JRE_DIR:-/root/win-arm64-runtime}"
OUT_DIR="${1:-dist-win}"
# 版本号从唯一来源读取（Version.kt），不再硬编码 ——
# 起因：1.9.004 那次只更新了两个 Linux 打包脚本，漏了这个值，
# 于是打出来的 Windows ZIP 名字仍是 1.9.003。
VERSION="$(grep -oE '[0-9]+.[0-9]+.[0-9]+' src/main/kotlin/com/jmnext/desktop/Version.kt | tail -1)"
[ -n "$VERSION" ] || { echo "没能从 Version.kt 读出版本号，中止"; exit 1; }
JAR="build/libs/jmnext-windows-arm64.jar"
ZIP_NAME="jmnext-${VERSION}-windows-arm64.zip"

[ -f "$JAR" ] || { echo "缺少单体 jar：$JAR"; echo "先执行：gradle fatJar -Ptarget=windows-arm64"; exit 1; }
[ -f "$JRE_DIR/bin/java.exe" ] || { echo "运行时不像 Windows：缺 $JRE_DIR/bin/java.exe"; exit 1; }
[ -f scripts/win-launcher.bat ] || { echo "缺少启动器 scripts/win-launcher.bat"; exit 1; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/jmnext"
cp "$JAR" "$STAGE/jmnext/"
cp -a "$JRE_DIR" "$STAGE/jmnext/runtime"
cp scripts/win-launcher.bat "$STAGE/jmnext/jmnext.bat"

mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR/$ZIP_NAME"
# 优先用 zip；没有就用 JDK 自带的 jar 工具打包（两者产出的都是标准 ZIP）
if command -v zip >/dev/null 2>&1; then
  ( cd "$STAGE" && zip -qr "$OLDPWD/$OUT_DIR/$ZIP_NAME" jmnext )
else
  echo "没有 zip，改用 JDK 的 jar 工具打包（会慢一些，46M 运行时）"
  ( cd "$STAGE" && jar cfM "$OLDPWD/$OUT_DIR/$ZIP_NAME" jmnext )
fi
echo "已生成 $OUT_DIR/$ZIP_NAME（$(du -h "$OUT_DIR/$ZIP_NAME" | cut -f1)）"
