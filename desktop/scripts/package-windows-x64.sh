#!/bin/bash
# SPDX-License-Identifier: MPL-2.0
# Windows x64 免装 Java 的 ZIP。
#
# 为什么要单独写：`gradle fatJar` 打出的 jar **不含 Skiko 的原生库**（.dll），
# 而 Compose 桌面端必须有它，否则启动即 java.lang.ExceptionInInitializerError
# （报错位置 androidx.compose.ui.scene.skia.SurfaceSkiaLayerComponent.<init>）。
# 群友实测：只放 fat jar 会闪退；补上 skiko-windows-x64.jar 后正常启动（2026-10-04 确认）。
#
# 用法：在容器内运行 scripts/package-windows-x64.sh [输出目录]
set -euo pipefail
OUT_DIR="${1:-dist-win64}"
VERSION=$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+' src/main/kotlin/com/jmnext/desktop/Version.kt | tail -1)
. "$(dirname "$0")/lib-gate.sh"
GATE_EXPECT="$OUT_DIR/Windows-x64-$VERSION.zip"
if ! gate_begin win-x64 "$OUT_DIR" src/main/kotlin ../shared/src/main/kotlin build.gradle.kts ../gradle/libs.versions.toml; then exit 0; fi
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
STAGE="$W/stage"; mkdir -p "$STAGE"

echo "== 1/5 胖 jar（windows-x64）=="
/opt/gradle-9.8.0/bin/gradle --console=plain fatJar -Ptarget=windows-x64 >/dev/null
JAR="build/libs/jmnext-windows-x64.jar"
[ -f "$JAR" ] || { echo "  找不到 $JAR"; exit 1; }
cp "$JAR" "$STAGE/jmnext.jar"

echo "== 2/5 Skiko Windows x64 原生库（关键，缺了会 ExceptionInInitializerError）=="
SK="$W/skiko.jar"
curl -sL --max-time 300 -o "$SK" \
  "https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-windows-x64/0.150.1/skiko-awt-runtime-windows-x64-0.150.1.jar"
unzip -l "$SK" | grep -q "skiko-windows-x64.dll" || { echo "  该 jar 里没有 skiko-windows-x64.dll，拒绝出包"; exit 1; }
cp "$SK" "$STAGE/skiko-windows-x64.jar"

echo "== 3/5 Temurin 21 Windows x64 运行时（国内镜像优先）=="
# 缓存优先：镜像下载失败过一次（拿到非 zip，unzip 报 "End-of-central-directory signature not found"），
# 每次重新下载 45MB 既慢又脆；这里与 exe 脚本一致，优先用缓存，并在解压前先确认拿到的确实是 zip。
JREZ="$W/jre.zip"; CACHE="${JRE_CACHE:-$HOME/.cache/win-jre-x64.zip}"
if [ -s "$CACHE" ]; then
  cp "$CACHE" "$JREZ"; echo "  用缓存：$(stat -c %s "$JREZ") 字节"
else
  for base in \
    "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jre/x64/windows" \
    "https://mirror.nju.edu.cn/Adoptium/21/jre/x64/windows" \
    "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1" ; do
    curl -sL --max-time 600 -o "$JREZ" "$base/OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip" || true
    [ -s "$JREZ" ] && [ "$(stat -c %s "$JREZ")" -gt 1000000 ] && break
  done
  [ -s "$JREZ" ] || { echo "  运行时下载失败"; exit 1; }
  unzip -l "$JREZ" >/dev/null 2>&1 || { echo "  下载到的不是有效 zip（镜像可能返回了错误页），拒绝继续"; exit 1; }
  mkdir -p "$(dirname "$CACHE")"; cp "$JREZ" "$CACHE"; echo "  已缓存到 $CACHE"
fi
unzip -q "$JREZ" -d "$W/jre" && mv "$W"/jre/*/ "$STAGE/runtime"
[ -f "$STAGE/runtime/bin/java.exe" ] || { echo "  runtime/bin/java.exe 不在，拒绝出包"; exit 1; }

echo "== 4/5 启动脚本（英文，避免 cmd 代码页乱码）=="
printf '@echo off\r\nrem 默认软件渲染（兼容建不出 GL 上下文的环境）；要强制 GPU 就改成 GL\r\nset JMCOMIC_RENDER=\r\ncd /d "%%~dp0"\r\necho === JMNeXt starting ===\r\nruntime\\bin\\java.exe -version\r\necho === running ===\r\nruntime\\bin\\java.exe -XX:+DisableAttachMechanism -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "jmnext.jar;skiko-windows-x64.jar" com.jmnext.desktop.MainKt\r\necho === exited with code %%ERRORLEVEL%% ===\r\npause\r\n' > "$STAGE/jmnext.bat"
printf '@echo off\r\ncd /d "%%~dp0"\r\nset OUT=diag.txt\r\necho ==== JMNeXt diagnostics ==== > "%%OUT%%"\r\nsysteminfo | findstr /B /C:"OS Name" /C:"OS Version" /C:"System Type" >> "%%OUT%%" 2>&1\r\nruntime\\bin\\java.exe -version >> "%%OUT%%" 2>&1\r\necho ---- run app ---- >> "%%OUT%%"\r\nruntime\\bin\\java.exe -XX:+DisableAttachMechanism -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "jmnext.jar;skiko-windows-x64.jar" com.jmnext.desktop.MainKt >> "%%OUT%%" 2>&1\r\necho ---- exit code %%ERRORLEVEL%% ---- >> "%%OUT%%"\r\ntype "%%OUT%%"\r\npause\r\n' > "$STAGE/diag.bat"

echo "== 5/5 打包 + 验收 =="
mkdir -p "$OUT_DIR"
ABS="$(cd "$OUT_DIR" && pwd)/Windows-x64-$VERSION.zip"
Z="$OUT_DIR/Windows-x64-$VERSION.zip"
rm -f "$Z"; (cd "$STAGE" && zip -qr "$(cd "$OLDPWD" && pwd)/$Z" . 2>/dev/null) || (cd "$STAGE" && zip -qr "$ABS" .)
for must in runtime/bin/java.exe skiko-windows-x64.jar jmnext.jar jmnext.bat diag.bat; do
  unzip -l "$Z" > "$W/list.txt" 2>/dev/null; grep -q "$must" "$W/list.txt" || { echo "  验收失败：缺 $must"; exit 1; }
done
echo "  通过：$(stat -c %s "$Z") 字节 -> $Z"

gate_commit
