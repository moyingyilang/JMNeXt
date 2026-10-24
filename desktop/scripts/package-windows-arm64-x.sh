#!/bin/bash
# SPDX-License-Identifier: MPL-2.0
# Windows ARM64 免装 Java 包（与 x64 同理：fat jar 缺 Skiko 原生库，必须补）
set -euo pipefail
OUT_DIR="${1:-dist-win}"; VERSION=$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+' src/main/kotlin/com/jmnext/desktop/Version.kt | tail -1)
. "$(dirname "$0")/lib-gate.sh"
GATE_EXPECT="$OUT_DIR/Windows-arm64-$VERSION.zip"
if ! gate_begin win-arm64 "$OUT_DIR" src/main/kotlin ../shared/src/main/kotlin build.gradle.kts ../gradle/libs.versions.toml; then exit 0; fi
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT; STAGE="$W/stage"; mkdir -p "$STAGE"
echo "== 1/4 fatJar(arm64) + Skiko 原生库 =="
/opt/gradle-9.8.0/bin/gradle --console=plain fatJar -Ptarget=windows-arm64 >/dev/null
cp build/libs/jmnext-windows-arm64.jar "$STAGE/jmnext.jar"
curl -sL --max-time 300 -o "$W/skiko.jar" "https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-windows-arm64/0.150.1/skiko-awt-runtime-windows-arm64-0.150.1.jar"
unzip -l "$W/skiko.jar" | grep -q "skiko-windows-arm64.dll" || { echo "  缺 skiko-windows-arm64.dll，拒绝出包"; exit 1; }
cp "$W/skiko.jar" "$STAGE/skiko-windows-arm64.jar"
echo "== 2/4 Temurin 21 Windows aarch64 =="
# 缓存优先 + 校验确实是 zip（与 x64 脚本、exe 脚本一致）：镜像偶发返回错误页会让 unzip 直接失败
JREZ="$W/jre.zip"; CACHE="${JRE_CACHE:-$HOME/.cache/win-jre-arm64.zip}"
if [ -s "$CACHE" ]; then
  cp "$CACHE" "$JREZ"; echo "  用缓存：$(stat -c %s "$JREZ") 字节"
else
  for base in \
    "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jre/aarch64/windows" \
    "https://mirror.nju.edu.cn/Adoptium/21/jre/aarch64/windows" \
    "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1" ; do
    curl -sL --max-time 600 -o "$JREZ" "$base/OpenJDK21U-jre_aarch64_windows_hotspot_21.0.12.1_1.zip" || true
    [ -s "$JREZ" ] && [ "$(stat -c %s "$JREZ")" -gt 1000000 ] && break
  done
  [ -s "$JREZ" ] || { echo "  运行时下载失败"; exit 1; }
  unzip -l "$JREZ" >/dev/null 2>&1 || { echo "  下载到的不是有效 zip（镜像可能返回了错误页），拒绝继续"; exit 1; }
  mkdir -p "$(dirname "$CACHE")"; cp "$JREZ" "$CACHE"; echo "  已缓存到 $CACHE"
fi
unzip -q "$JREZ" -d "$W/jre" && mv "$W"/jre/*/ "$STAGE/runtime"
[ -f "$STAGE/runtime/bin/java.exe" ] || { echo "  缺 runtime/bin/java.exe"; exit 1; }
echo "== 3/4 启动脚本 =="
printf '@echo off\r\nrem 默认软件渲染（兼容建不出 GL 上下文的环境）；要强制 GPU 就改成 GL\r\nset JMCOMIC_RENDER=\r\ncd /d "%%~dp0"\r\nruntime\\bin\\java.exe -XX:+DisableAttachMechanism -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "jmnext.jar;skiko-windows-arm64.jar" com.jmnext.desktop.MainKt\r\npause\r\n' > "$STAGE/jmnext.bat"
printf '@echo off\r\ncd /d "%%~dp0"\r\nset OUT=diag.txt\r\necho ==== JMNeXt diagnostics ==== > "%%OUT%%"\r\nsysteminfo | findstr /B /C:"OS Name" /C:"OS Version" /C:"System Type" >> "%%OUT%%" 2>&1\r\nruntime\\bin\\java.exe -version >> "%%OUT%%" 2>&1\r\necho ---- run app ---- >> "%%OUT%%"\r\nruntime\\bin\\java.exe -XX:+DisableAttachMechanism -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "jmnext.jar;skiko-windows-arm64.jar" com.jmnext.desktop.MainKt >> "%%OUT%%" 2>&1\r\necho ---- exit code %%ERRORLEVEL%% ---- >> "%%OUT%%"\r\ntype "%%OUT%%"\r\npause\r\n' > "$STAGE/diag.bat"
echo "== 4/4 打包 + 验收 =="
mkdir -p "$OUT_DIR"
ABS="$(cd "$OUT_DIR" && pwd)/Windows-arm64-$VERSION.zip"
Z="$OUT_DIR/Windows-arm64-$VERSION.zip"; rm -f "$Z"; (cd "$STAGE" && zip -qr "$ABS" .)
unzip -l "$Z" > "$W/list.txt" 2>/dev/null
for m in runtime/bin/java.exe skiko-windows-arm64.jar jmnext.jar jmnext.bat; do grep -q "$m" "$W/list.txt" || { echo "  验收失败：缺 $m"; exit 1; }; done
echo "  通过：$(stat -c %s "$Z") 字节"

gate_commit
