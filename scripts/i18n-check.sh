#!/usr/bin/env bash
# 检查 values/ 与 values-zh-rTW/ 的键是否一一对应，并在编译产物里确认繁体值存在。
# 只读文件、只读 APK，不改任何东西。
set -u
cd "$(dirname "$0")/.." || exit 1

CN=app/src/main/res/values/strings.xml
TW=app/src/main/res/values-zh-rTW/strings.xml

keys() { sed -n 's/.*<string name="\([^"]*\)".*/\1/p' "$1" | sort; }

cn=$(mktemp); tw=$(mktemp)
keys "$CN" > "$cn"; keys "$TW" > "$tw"

missing=$(comm -23 "$cn" "$tw")
extra=$(comm -13 "$cn" "$tw")

printf '简中键 %s 个，繁中键 %s 个\n' "$(wc -l < "$cn" | tr -d ' ')" "$(wc -l < "$tw" | tr -d ' ')"
if [ -n "$missing" ]; then
    printf '繁中缺 %s 个：\n' "$(printf '%s\n' "$missing" | wc -l | tr -d ' ')"
    printf '%s\n' "$missing" | sed 's/^/  /'
fi
if [ -n "$extra" ]; then
    printf '繁中多出 %s 个：\n' "$(printf '%s\n' "$extra" | wc -l | tr -d ' ')"
    printf '%s\n' "$extra" | sed 's/^/  /'
fi
[ -n "$missing$extra" ] || printf '键完全对齐\n'

# 产物侧：确认编译后的 APK 里 zh-TW 真的带值（不是只存在于源码）
APK=${1:-app/build/outputs/apk/full/debug/app-full-debug.apk}
if [ -f "$APK" ]; then
    if command -v aapt2 >/dev/null 2>&1; then
        n=$(aapt2 dump resources "$APK" 2>/dev/null | grep -c 'zh-rTW')
        printf 'APK 内 zh-rTW 资源条目 %s 行\n' "$n"
    else
        printf '没有 aapt2，跳过产物检查\n'
    fi
else
    printf '没找到 APK：%s，跳过产物检查\n' "$APK"
fi

rm -f "$cn" "$tw"
