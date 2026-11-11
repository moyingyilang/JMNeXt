#!/bin/bash
# 从已打好的标准包里生成 4 个调试包：内容相同，多一个 debug.sh / debug.bat。
# 调试包不是重新编译，只是把 JMNEXT_VERBOSE=1 写进启动脚本，方便用户抓日志。
set -eu
cd "$(dirname "$0")/.." || exit 1
V=$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+' src/main/kotlin/com/jmnext/desktop/Version.kt | tail -1)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT

for a in aarch64 x86_64; do
  case $a in
    aarch64) src="dist/Linux-aarch64-$V.tar.gz";  out="dist" ;;
    *)       src="dist-x64/Linux-x86_64-$V.tar.gz"; out="dist-x64" ;;
  esac
  [ -f "$src" ] || { echo "  跳过：缺 $src"; continue; }
  rm -rf "$T/l"; mkdir -p "$T/l"; tar xzf "$src" -C "$T/l"
  top=$(ls "$T/l" | head -1)
  cat > "$T/l/$top/debug.sh" <<'EOF'
#!/bin/sh
# 带详细日志启动：日志同时打到终端与 debug.log
cd "$(dirname "$0")" || exit 1
JMNEXT_VERBOSE=1 ./bin/jmnext "$@" 2>&1 | tee debug.log
EOF
  chmod +x "$T/l/$top/debug.sh"
  name=$([ "$a" = aarch64 ] && echo Linux-aarch64 || echo Linux-x86_64)
  ( cd "$T/l" && tar czf "/tmp/$name-$V-debug.tar.gz" "$top" )
  mv "/tmp/$name-$V-debug.tar.gz" "$out/"
  echo "  $name 调试包完成"
done

for a in x64 arm64; do
  case $a in
    x64)   src="dist-win64/Windows-x64-$V.zip";   out="dist-win64" ;;
    *)     src="dist-win/Windows-arm64-$V.zip";   out="dist-win" ;;
  esac
  [ -f "$src" ] || { echo "  跳过：缺 $src"; continue; }
  rm -rf "$T/w"; mkdir -p "$T/w"; unzip -q "$src" -d "$T/w"
  first=$(ls "$T/w" | head -1)
  if [ -d "$T/w/$first" ] && [ "$(ls "$T/w" | wc -l)" = "1" ]; then base="$T/w/$first"; else base="$T/w"; fi
  printf '@echo off\r\ncd /d "%%~dp0"\r\nset JMNEXT_VERBOSE=1\r\nruntime\\bin\\java.exe -cp "jmnext.jar;skiko-windows-%s.jar" com.jmnext.desktop.MainKt > debug.log 2>&1\r\ntype debug.log\r\npause\r\n' "$a" > "$base/debug.bat"
  ( cd "$T/w" && zip -qr "$(cd "$OLDPWD" >/dev/null; pwd)/$out/Windows-$a-$V-debug.zip" . )
  echo "  Windows $a 调试包完成"
done
