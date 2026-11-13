#!/usr/bin/env bash
# 检查所有 values-*/ 与 values/ 的键是否一一对应，并在编译产物里确认资源真的在。
set -u
cd "$(dirname "$0")/.." || exit 1

CN=app/src/main/res/values/strings.xml
keys() { sed -n 's/.*<string name="\([^"]*\)".*/\1/p' "$1" | sort; }

cn=$(mktemp); keys "$CN" > "$cn"
printf '简中键 %s 个\n' "$(wc -l < "$cn" | tr -d ' ')"

bad=0
for dir in app/src/main/res/values-*/; do
    f="$dir/strings.xml"
    [ -f "$f" ] || continue
    tw=$(mktemp); keys "$f" > "$tw"
    missing=$(comm -23 "$cn" "$tw"); extra=$(comm -13 "$cn" "$tw")
    n=$(wc -l < "$tw" | tr -d ' ')
    if [ -z "$missing$extra" ]; then
        printf '%-24s %s 键，对齐\n' "${dir#app/src/main/res/}" "$n"
    else
        bad=1
        printf '%-24s %s 键，不一致\n' "${dir#app/src/main/res/}" "$n"
        [ -n "$missing" ] && printf '%s\n' "$missing" | sed 's/^/  缺 /'
        [ -n "$extra" ] && printf '%s\n' "$extra" | sed 's/^/  多 /'
    fi
    rm -f "$tw"
done
rm -f "$cn"

APK=${1:-app/build/outputs/apk/full/debug/app-full-debug.apk}
if [ -f "$APK" ] && command -v aapt2 >/dev/null 2>&1; then
    for loc in zh-rTW zh-rHK; do
        n=$(aapt2 dump resources "$APK" 2>/dev/null | grep -c "$loc")
        [ "$n" -gt 0 ] && printf 'APK 内 %s 条目 %s 行\n' "$loc" "$n"
    done
fi
exit "$bad"
