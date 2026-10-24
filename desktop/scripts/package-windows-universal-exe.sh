#!/bin/bash
# SPDX-License-Identifier: MPL-2.0
# Windows **架构自适应单体 exe**：一个文件同时含 x64 与 arm64 两套运行时，启动时自动选择。
#
# 为什么单独做一个：此前的单体 exe 分 x64 / arm64 两个文件，用户需要自己判断机器架构
# （issue #4 报的就是"x64 的 exe 在 x64 机器上打不开"，而 ZIP 反而能用——分辨架构本身就是负担）。
#
# 做法：两套胖 jar（fatJar 的 target 决定打进去的 Skiko 原生库）+ 两套 Skiko 原生库 + 两套 JRE，
# 全部打进同一个 NSIS 自解压 exe；启动时读 PROCESSOR_ARCHITECTURE / PROCESSOR_ARCHITEW6432 选一套。
# 代价是体积约一倍（两套运行时），换来"只有一个文件、不用分辨架构"。
#
# 用法：在容器内、desktop/ 目录下运行 scripts/package-windows-universal-exe.sh [输出目录]
set -euo pipefail
OUT_DIR="${1:-dist-win-universal}"
VERSION=$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+' src/main/kotlin/com/jmnext/desktop/Version.kt | tail -1)
. "$(dirname "$0")/lib-gate.sh"
GATE_EXPECT="$OUT_DIR/Windows-universal-$VERSION.exe"
if ! gate_begin win-universal-exe "$OUT_DIR" src/main/kotlin ../shared/src/main/kotlin build.gradle.kts ../gradle/libs.versions.toml; then exit 0; fi
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
STAGE="$W/stage"; mkdir -p "$STAGE"

echo "== 1/5 两套胖 jar（x64 与 arm64）=="
for a in x64 arm64; do
  /opt/gradle-9.8.0/bin/gradle --console=plain fatJar -Ptarget=windows-$a >/dev/null
  JAR="build/libs/jmnext-windows-$a.jar"
  [ -f "$JAR" ] || { echo "  找不到 $JAR"; exit 1; }
  cp "$JAR" "$STAGE/jmnext-$a.jar"
  echo "  jmnext-$a.jar $(stat -c %s "$STAGE/jmnext-$a.jar") 字节"
done

echo "== 2/5 两套 Skiko 原生库 =="
for a in x64 arm64; do
  SK="$W/skiko-$a.jar"
  curl -sL --max-time 300 -o "$SK" \
    "https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-windows-$a/0.150.1/skiko-awt-runtime-windows-$a-0.150.1.jar"
  unzip -l "$SK" | grep -q "skiko-windows-$a.dll" || { echo "  该 jar 里没有 skiko-windows-$a.dll，拒绝出包"; exit 1; }
  cp "$SK" "$STAGE/skiko-windows-$a.jar"
  echo "  skiko-windows-$a.jar $(stat -c %s "$STAGE/skiko-windows-$a.jar") 字节"
done

echo "== 3/5 两套 Temurin 21 运行时（缓存优先，两个架构都校验是有效 zip）=="
for pair in "x64:$HOME/.cache/win-jre-x64.zip:OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip:x64" \
            "arm64:$HOME/.cache/win-jre-arm64.zip:OpenJDK21U-jre_aarch64_windows_hotspot_21.0.12.1_1.zip:aarch64"; do
  a=${pair%%:*}; rest=${pair#*:}; CACHE=${rest%%:*}; rest=${rest#*:}; FILE=${rest%%:*}; dir=${rest##*:}
  JREZ="$W/jre-$a.zip"
  if [ -s "$CACHE" ]; then
    cp "$CACHE" "$JREZ"; echo "  $a 用缓存：$(stat -c %s "$JREZ") 字节"
  else
    for base in \
      "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jre/$dir/windows" \
      "https://mirror.nju.edu.cn/Adoptium/21/jre/$dir/windows" \
      "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1" ; do
      curl -sL --max-time 600 -o "$JREZ" "$base/$FILE" || true
      [ -s "$JREZ" ] && [ "$(stat -c %s "$JREZ")" -gt 1000000 ] && break
    done
    [ -s "$JREZ" ] || { echo "  $a 运行时下载失败"; exit 1; }
    unzip -l "$JREZ" >/dev/null 2>&1 || { echo "  $a 下载到的不是有效 zip，拒绝继续"; exit 1; }
    mkdir -p "$(dirname "$CACHE")"; cp "$JREZ" "$CACHE"; echo "  已缓存到 $CACHE"
  fi
  rm -rf "$W/jre-$a"; unzip -q "$JREZ" -d "$W/jre-$a" && mv "$W"/jre-$a/*/ "$STAGE/runtime-$a"
  [ -f "$STAGE/runtime-$a/bin/java.exe" ] || { echo "  runtime-$a/bin/java.exe 不在，拒绝出包"; exit 1; }
done

echo "== 4/5 启动与诊断脚本 =="
printf '@echo off\r\ncd /d "%%~dp0"\r\nrem 默认软件渲染（兼容建不出 GL 上下文的环境）；要强制 GPU 就改成 GL\r\nset JMCOMIC_RENDER=\r\necho === JMNeXt（自动判断架构）===\r\nif /I "%%PROCESSOR_ARCHITECTURE%%"=="ARM64" (set A=arm64) else (set A=x64)\r\nif /I "%%PROCESSOR_ARCHITEW6432%%"=="ARM64" (set A=arm64)\r\necho 使用架构：%%A%%\r\nruntime-%%A%%\\bin\\java.exe -XX:+DisableAttachMechanism -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -version\r\necho === running ===\r\nruntime-%%A%%\\bin\\java.exe -XX:+DisableAttachMechanism -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "jmnext-%%A%%.jar;skiko-windows-%%A%%.jar" com.jmnext.desktop.MainKt\r\necho === exited with code %%ERRORLEVEL%% ===\r\npause\r\n' > "$STAGE/run.bat"

echo "== 5/5 NSIS 打成单体 exe（含两套运行时）=="
OUT_EXE="$VERSION-$$.exe"
cat > "$W/app.nsi" <<NSI
Unicode true
Name "JMNeXt"
OutFile "$OUT_EXE"
SilentInstall silent
RequestExecutionLevel user
AutoCloseWindow true
!include "LogicLib.nsh"
Section
  SetOutPath "\$LOCALAPPDATA\\JMNeXt"
  ; 条件解压：只有版本标记不存在时才解压（两套运行时约 180MB，每次重解会表现为"点了没反应"）
  IfFileExists "\$LOCALAPPDATA\\JMNeXt\\.installed-$VERSION" jmnext_done jmnext_extract
jmnext_extract:
  File /r "stage\\*"
  FileOpen \$0 "\$LOCALAPPDATA\\JMNeXt\\.installed-$VERSION" w
  FileClose \$0
jmnext_done:
  System::Call 'kernel32::SetCurrentDirectory(t "\$LOCALAPPDATA\\JMNeXt")'
  ; 架构自适应：先看原生变量，再看 WOW64 下的 PROCESSOR_ARCHITEW6432（32 位进程在 ARM64 上会读成 x86）
  ReadEnvStr \$R1 "PROCESSOR_ARCHITECTURE"
  ReadEnvStr \$R2 "PROCESSOR_ARCHITEW6432"
  StrCpy \$R0 "x64"
  \${If} \$R1 == "ARM64"
    StrCpy \$R0 "arm64"
  \${ElseIf} \$R2 == "ARM64"
    StrCpy \$R0 "arm64"
  \${EndIf}
  ; javaw：不弹控制台。若打不开，双击应用目录里的 run.bat 可看到报错，日志另有 %USERPROFILE%\\jmnext.log
  Exec '"\$LOCALAPPDATA\\JMNeXt\\runtime-\$R0\\bin\\javaw.exe" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "\$LOCALAPPDATA\\JMNeXt\\jmnext-\$R0.jar;\$LOCALAPPDATA\\JMNeXt\\skiko-windows-\$R0.jar" com.jmnext.desktop.MainKt'
SectionEnd
NSI
( cd "$W" && makensis -V2 "app.nsi" >/dev/null )
mkdir -p "$OUT_DIR"
cp "$W/$OUT_EXE" "$OUT_DIR/Windows-universal-$VERSION.exe"
EXE="$OUT_DIR/Windows-universal-$VERSION.exe"

echo "== 验收 =="
printf "  %s  %s 字节\n" "$(basename "$EXE")" "$(stat -c %s "$EXE")"
CHK="$W/chk"; mkdir -p "$CHK"; ( 7z x -y -o"$CHK" "$EXE" >/dev/null 2>&1 )
for m in jmnext-x64.jar jmnext-arm64.jar skiko-windows-x64.jar skiko-windows-arm64.jar run.bat \
         runtime-x64/bin/java.exe runtime-arm64/bin/java.exe; do
  f=$(find "$CHK" -path "*$m" -print -quit 2>/dev/null)
  [ -n "$f" ] && printf "  %-32s %s 字节\n" "$m" "$(stat -c %s "$f")" || { printf "  **缺 %s**\n" "$m"; }
done
for a in x64 arm64; do
  JF=$(find "$CHK" -path "*runtime-$a/bin/java.exe" -print -quit 2>/dev/null)
  printf "  runtime-%s java.exe 架构: %s\n" "$a" "$(file -b "$JF" 2>/dev/null | cut -d, -f1-2)"
done
printf "  PE 头：%s\n" "$(head -c2 "$EXE")"
gate_commit
