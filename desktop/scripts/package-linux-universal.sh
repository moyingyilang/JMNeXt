#!/bin/bash
# SPDX-License-Identifier: MPL-2.0
# Linux **架构自适应 tar.gz**：一个包内含 aarch64 与 x86_64 两套运行时，启动时按 uname -m 自动选择。
#
# 为什么只做 tar.gz：deb/rpm 的架构写在**包元数据**里（Architecture: arm64/x86_64），里面装着架构相关的
# JRE，不能声明成 all；AppImage 的**外层运行时本身**就是架构相关的。这两类只能保持按架构分发。
# tar.gz 是纯文件，可以放两套，再配一个选择脚本。
#
# 做法：不动构建 —— 直接合并两个已有的按架构 tar.gz（它们的产物已各自验收过），加一个 run.sh。
#
# 用法：在容器内、desktop/ 目录下运行 scripts/package-linux-universal.sh [输出目录]
set -euo pipefail
OUT_DIR="${1:-dist-universal}"
VERSION=$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+' src/main/kotlin/com/jmnext/desktop/Version.kt | tail -1)
. "$(dirname "$0")/lib-gate.sh"
GATE_EXPECT="$OUT_DIR/Linux-universal-$VERSION.tar.gz"
if ! gate_begin linux-universal "$OUT_DIR" dist/Linux-aarch64-$VERSION.tar.gz dist-x64/Linux-x86_64-$VERSION.tar.gz; then exit 0; fi

ARM="dist/Linux-aarch64-$VERSION.tar.gz"
X64="dist-x64/Linux-x86_64-$VERSION.tar.gz"
[ -s "$ARM" ] || { echo "  缺 $ARM（先跑 package-linux.sh）"; exit 1; }
[ -s "$X64" ] || { echo "  缺 $X64（先跑 package-linux-x64.sh）"; exit 1; }

W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
echo "== 1/4 展开两个按架构的包 =="
mkdir -p "$W/u/arm64" "$W/u/x64"
tar xzf "$ARM" -C "$W/u/arm64"
tar xzf "$X64" -C "$W/u/x64"
for a in arm64 x64; do
  R=$(find "$W/u/$a" -maxdepth 3 -type d -name runtime -print -quit)
  [ -n "$R" ] || { echo "  $a 包里找不到 runtime 目录"; exit 1; }
  printf "  %-6s runtime 里的 java: %s\n" "$a" "$(file -b "$(find "$R" -name java -type f -print -quit)" 2>/dev/null | cut -d, -f1-2)"
done

echo "== 2/4 合并两套（app-image 的布局是 bin/ + lib/app + lib/runtime）=="
ARMROOT="$W/u/arm64"; X64ROOT="$W/u/x64"
[ -d "$ARMROOT/lib/app" ] && [ -d "$ARMROOT/lib/runtime" ] || { echo "  arm64 包结构不符（缺 lib/app 或 lib/runtime）"; exit 1; }
[ -d "$X64ROOT/lib/app" ] && [ -d "$X64ROOT/lib/runtime" ] || { echo "  x64 包结构不符"; exit 1; }
# 各自的运行时与 app 目录都带上架构后缀，避免互相覆盖
mv "$ARMROOT/lib/app" "$ARMROOT/lib/app-arm64"
mv "$ARMROOT/lib/runtime" "$ARMROOT/lib/runtime-arm64"
cp -a "$X64ROOT/lib/app" "$ARMROOT/lib/app-x64"
cp -a "$X64ROOT/lib/runtime" "$ARMROOT/lib/runtime-x64"
# arm64 那一半的运行时换成 jlink 出来的（含 bin/java）。
# 原因：jpackage 生成的 app-image 里 lib/runtime 是**给它的 ELF 启动器**用的，没有 bin/java；
# 而统一包改用 shell 启动器（要 exec .../bin/java），所以必须换成带可执行文件的运行时。
# 生成方式（模块清单直接取自该运行时的 lib/modules，避免多带或漏带模块）：
#   /opt/jdk21-linux/bin/jlink --module-path /opt/jdk21-linux/jmods \
#     --add-modules java.base,java.datatransfer,java.desktop,java.logging,java.prefs,java.xml,jdk.crypto.ec \
#     --output /root/arm64-runtime --strip-debug --no-header-files --no-man-pages
ARM64_RUNTIME=${ARM64_RUNTIME:-/root/arm64-runtime}
[ -x "$ARM64_RUNTIME/bin/java" ] || { echo "  缺 $ARM64_RUNTIME/bin/java（先用 jlink 生成，命令见脚本注释）"; exit 1; }
rm -rf "$ARMROOT/lib/runtime-arm64"
cp -a "$ARM64_RUNTIME" "$ARMROOT/lib/runtime-arm64"
printf "  runtime-arm64 已换成 jlink 运行时：%s\n" "$(file -b "$ARMROOT/lib/runtime-arm64/bin/java" | cut -d, -f1-2)"
# 权限归一化：jlink 在容器 umask 下会把库生成成 600（没有 x 位），加载器会报
# "libjli.so: cannot open shared object file"。必须显式补上可执行/可读位，否则运行时启动不了。
chmod -R a+r "$ARMROOT/lib/runtime-arm64" "$ARMROOT/lib/runtime-x64"
chmod -R a+x "$ARMROOT/lib/runtime-arm64/bin" "$ARMROOT/lib/runtime-x64/bin"
chmod 755 "$ARMROOT/lib/runtime-arm64/bin/java" "$ARMROOT/lib/runtime-x64/bin/java"
printf "  runtime-arm64 的 libjli.so 权限: %s\n" "$(stat -c %a "$ARMROOT/lib/runtime-arm64/lib/libjli.so")"
printf "  runtime-x64   的 libjli.so 权限: %s\n" "$(stat -c %a "$ARMROOT/lib/runtime-x64/lib/libjli.so")"
# jpackage 生成的 ELF 启动器只对本架构有效，统一换成按架构选择的脚本
rm -rf "$ARMROOT/bin"; mkdir -p "$ARMROOT/bin"

echo "== 3/4 生成按架构选择的启动脚本 =="
cat > "$ARMROOT/bin/jmnext" <<'SH'
#!/bin/sh
# 架构自适应启动器：按 uname -m 选一套运行时与一套 app 目录（两套都是打进同一个包的）。
set -eu
DIR=$(cd "$(dirname "$0")/.." && pwd)
case "$(uname -m)" in
  x86_64|amd64) A=x64 ;;
  aarch64|arm64) A=arm64 ;;
  *) echo "不支持的架构：$(uname -m)（本包内含 x86_64 与 aarch64 两套）" >&2; exit 1 ;;
esac
[ -d "$DIR/lib/runtime-$A" ] || { echo "缺运行时：$DIR/lib/runtime-$A" >&2; exit 1; }
[ -d "$DIR/lib/app-$A" ] || { echo "缺应用目录：$DIR/lib/app-$A" >&2; exit 1; }
# 显式设置库搜索路径：包内 libjli.so 存在、bin/java 的 rpath 也写着 $ORIGIN/../lib，
# 但在 bind mount 这类路径下 $ORIGIN 解析不可靠（实测不设就报 "libjli.so: cannot open shared object file"），
# 设上即可正常启动。写死这一行比依赖 rpath 稳。
LD_LIBRARY_PATH="$DIR/lib/runtime-$A/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export LD_LIBRARY_PATH
exec "$DIR/lib/runtime-$A/bin/java" \
  -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 \
  -Dskiko.library.path="$DIR/lib/app-$A" \
  -cp "$DIR/lib/app-$A/*" \
  com.jmnext.desktop.MainKt "$@"
SH
chmod 755 "$ARMROOT/bin/jmnext"
cat > "$ARMROOT/run.sh" <<'SH'
#!/bin/sh
# 双击/命令行都可用：转到 bin/jmnext（真正的架构自适应启动器）
exec "$(cd "$(dirname "$0")" && pwd)/bin/jmnext" "$@"
SH
chmod 755 "$ARMROOT/run.sh"

echo "== 4/4 打包 =="
mkdir -p "$OUT_DIR"
ABS="$(cd "$OUT_DIR" && pwd)/Linux-universal-$VERSION.tar.gz"
tar czf "$ABS" -C "$ARMROOT" .
Z="$OUT_DIR/Linux-universal-$VERSION.tar.gz"
printf "  %s  %s 字节\n" "$(basename "$Z")" "$(stat -c %s "$Z")"

echo "== 验收 =="
CHK="$W/chk"; mkdir -p "$CHK"; tar xzf "$Z" -C "$CHK"
for must in run.sh bin/jmnext lib/app-arm64 lib/app-x64 lib/runtime-arm64 lib/runtime-x64; do
  if [ -e "$CHK/$must" ]; then printf "  有 %-18s\n" "$must"; else printf "  **缺 %s**\n" "$must"; exit 1; fi
done
for a in arm64 x64; do
  J=$(find "$CHK/lib/runtime-$a" -name java -type f -print -quit 2>/dev/null)
  printf "  runtime-%-6s java: %s\n" "$a" "$(file -b "$J" 2>/dev/null | cut -d, -f1-2)"
done
if grep -q "uname -m" "$CHK/bin/jmnext"; then echo "  bin/jmnext 含架构判断"; else echo "  **bin/jmnext 里没有架构判断**"; exit 1; fi
gate_commit
