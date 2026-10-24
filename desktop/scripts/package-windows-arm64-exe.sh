#!/bin/bash
# Windows arm64 **单体 exe**（便携、免安装、双击即运行）。
#
# 为什么要它：此前的交付物是一个 ZIP（里面是 jmnext.bat + runtime/ + jar），第一次拿到的人
# 不知道该怎么用。单体 exe 的目标是"双击就行"。
#
# 做法：用 NSIS 把 stage（应用 jar + Skiko 原生库 + 免装 JRE）打进一个静默自解压 exe，
# 解压到 %LOCALAPPDATA%\JMNeXt 后直接启动（javaw，不弹控制台）。首次运行解压、之后直接启动。
#
# 用法：scripts/package-windows-arm64-exe.sh [输出目录]      默认 dist-win64
set -euo pipefail
OUT_DIR="${1:-dist-win64}"
VERSION=$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+' src/main/kotlin/com/jmnext/desktop/Version.kt | tail -1)
. "$(dirname "$0")/lib-gate.sh"
GATE_EXPECT="$OUT_DIR/Windows-arm64-$VERSION.exe"
if ! gate_begin win-arm64-exe "$OUT_DIR" src/main/kotlin ../shared/src/main/kotlin build.gradle.kts ../gradle/libs.versions.toml; then exit 0; fi
W=$(mktemp -d); trap 'rm -rf "$W" "${CHK:-}"' EXIT
STAGE="$W/stage"; mkdir -p "$STAGE"

echo "== 1/4 胖 jar（windows-arm64）=="
/opt/gradle-9.8.0/bin/gradle --console=plain fatJar -Ptarget=windows-arm64 >/dev/null
JAR="build/libs/jmnext-windows-arm64.jar"
[ -f "$JAR" ] || { echo "  找不到 $JAR"; exit 1; }
cp "$JAR" "$STAGE/jmnext.jar"

echo "== 2/4 Skiko Windows x64 原生库（缺了会 ExceptionInInitializerError）=="
SK="$W/skiko.jar"
curl -sL --max-time 300 -o "$SK" \
  "https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-windows-arm64/0.150.1/skiko-awt-runtime-windows-arm64-0.150.1.jar"
unzip -l "$SK" | grep -q "skiko-windows-arm64.dll" || { echo "  该 jar 里没有 skiko-windows-arm64.dll，拒绝出包"; exit 1; }
cp "$SK" "$STAGE/skiko-windows-arm64.jar"

echo "== 3/4 Temurin 21 Windows x64 运行时（优先用缓存 $HOME/.cache/win-jre-arm64.zip）=="
JREZ="$W/jre.zip"; CACHE="${JRE_CACHE:-$HOME/.cache/win-jre-arm64.zip}"
if [ -s "$CACHE" ]; then cp "$CACHE" "$JREZ"; echo "  用缓存：$(stat -c %s "$JREZ") 字节"
else
  for base in \
    "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jre/aarch64/windows" \
    "https://mirror.nju.edu.cn/Adoptium/21/jre/aarch64/windows" \
    "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1" ; do
    curl -sL --max-time 600 -o "$JREZ" "$base/OpenJDK21U-jre_aarch64_windows_hotspot_21.0.12.1_1.zip" || true
    [ -s "$JREZ" ] && [ "$(stat -c %s "$JREZ")" -gt 1000000 ] && break
  done
  [ -s "$JREZ" ] || { echo "  运行时下载失败"; exit 1; }
  mkdir -p "$(dirname "$CACHE")"; cp "$JREZ" "$CACHE"; echo "  已缓存到 $CACHE"
fi
unzip -q "$JREZ" -d "$W/jre" && mv "$W"/jre/*/ "$STAGE/runtime"
[ -f "$STAGE/runtime/bin/java.exe" ] || { echo "  runtime/bin/java.exe 不在，拒绝出包"; exit 1; }
# 诊断脚本：单体 exe 静默无窗口，出问题时双击它就能看到报错（并 pause 住）
printf '@echo off\r\ncd /d "%%~dp0"\r\nrem 默认软件渲染（兼容建不出 GL 上下文的环境）；要强制 GPU 就改成 GL\r\nset JMCOMIC_RENDER=\r\necho === JMNeXt ===\r\nruntime\\bin\\java.exe -version\r\necho === running ===\r\nruntime\\bin\\java.exe -XX:+DisableAttachMechanism -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "jmnext.jar;skiko-windows-arm64.jar" com.jmnext.desktop.MainKt\r\necho === exited with code %%ERRORLEVEL%% ===\r\npause\r\n' > "$STAGE/run.bat"

echo "== 4/4 NSIS 打成单体 exe =="
OUT_EXE="$VERSION-$$.exe"
cat > "$W/app.nsi" <<NSI
Unicode true
Name "JMNeXt"
OutFile "$OUT_EXE"
SilentInstall silent
RequestExecutionLevel user
AutoCloseWindow true
Section
  SetOutPath "\$LOCALAPPDATA\\JMNeXt"
  ; 条件解压：只有版本标记不存在时才解压。
  ; 此前 File /r 每次启动都执行 —— 静默 + 无窗口 + 重解约 150MB，用户看到的就是"点了没反应/炸了"。
  IfFileExists "\$LOCALAPPDATA\\JMNeXt\\.installed-$VERSION" jmnext_done jmnext_extract
jmnext_extract:
  File /r "stage\\*"
  FileOpen \$0 "\$LOCALAPPDATA\\JMNeXt\\.installed-$VERSION" w
  FileClose \$0
jmnext_done:
  ; 工作目录切到应用目录：ZIP 版的 jmnext.bat 一直有 cd /d "%~dp0"，单体 exe 此前漏了这一步
  System::Call 'kernel32::SetCurrentDirectory(t "\$LOCALAPPDATA\\JMNeXt")'
  ; 与 zip 版一致：Windows 上默认用 GL 渲染，绕开部分环境的 OpenGL 上下文问题
  ; javaw：不弹控制台窗口。若打不开，双击应用目录里的 run.bat 可看到报错，日志另有 %USERPROFILE%\\jmnext.log
  Exec '"\$LOCALAPPDATA\\JMNeXt\\runtime\\bin\\javaw.exe" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "\$LOCALAPPDATA\\JMNeXt\\jmnext.jar;\$LOCALAPPDATA\\JMNeXt\\skiko-windows-arm64.jar" com.jmnext.desktop.MainKt'
SectionEnd
NSI
( cd "$W" && makensis -V2 "app.nsi" >/dev/null )
mkdir -p "$OUT_DIR"
cp "$W/$OUT_EXE" "$OUT_DIR/Windows-arm64-$VERSION.exe"
EXE="$OUT_DIR/Windows-arm64-$VERSION.exe"

echo "== 验收 =="
printf "  %s  %s 字节\n" "$(basename "$EXE")" "$(stat -c %s "$EXE")"
[ "$(head -c2 "$EXE")" = "MZ" ] && echo "  PE 头：MZ（Windows 可执行）" || { echo "  不是 PE 文件，拒绝出包"; exit 1; }
# 判据用**解压后看实物**：NSIS 归档里路径带 $LOCALAPPDATA/JMNeXt 前缀，直接 grep 包清单容易写错
CHK=$(mktemp -d); 7z x -y -o"$CHK" "$EXE" >/dev/null 2>&1 || true
for m in runtime/bin/java.exe skiko-windows-arm64.jar jmnext.jar; do
  f=$(find "$CHK" -path "*/$m" -print -quit 2>/dev/null)
  [ -n "$f" ] && echo "  内含 $m ($(stat -c %s "$f") 字节)" || { echo "  包内缺 $m"; rm -rf "$CHK"; exit 1; }
done
JF=$(find "$CHK" -path "*/runtime/bin/java.exe" -print -quit)
echo "  java.exe 架构: $(file -b "$JF" | cut -d, -f1-2)"
rm -rf "$CHK"
echo "单体 exe 完成：$EXE"

gate_commit
