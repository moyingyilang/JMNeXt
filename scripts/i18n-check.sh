#!/usr/bin/env bash
# 检查所有 values-*/ 与 values/ 的键、格式占位符是否一致，并在编译产物里确认资源真的在。
set -u
cd "$(dirname "$0")/.." || exit 1
ROOT=$(pwd)
CN=app/src/main/res/values/strings.xml

keys() { sed -n 's/.*<string name="\([^"]*\)".*/\1/p' "$1" | sort; }

cn=$(mktemp); keys "$CN" > "$cn"
spec_cn=$(mktemp); perl "$ROOT/scripts/i18n-specs.pl" "$CN" > "$spec_cn"
printf '简中键 %s 个\n' "$(wc -l < "$cn" | tr -d ' ')"

bad=0
for dir in app/src/main/res/values-*/; do
    f="$dir/strings.xml"
    [ -f "$f" ] || continue
    loc=${dir#app/src/main/res/}
    tw=$(mktemp); keys "$f" > "$tw"
    missing=$(comm -23 "$cn" "$tw"); extra=$(comm -13 "$cn" "$tw")
    n=$(wc -l < "$tw" | tr -d ' ')
    if [ -n "$missing$extra" ]; then
        bad=1
        printf '%-20s %s 键，键不一致\n' "$loc" "$n"
        [ -n "$missing" ] && printf '%s\n' "$missing" | sed 's/^/  缺 /'
        [ -n "$extra" ] && printf '%s\n' "$extra" | sed 's/^/  多 /'
    else
        printf '%-20s %s 键，对齐' "$loc" "$n"
    fi

    spec_loc=$(mktemp); perl "$ROOT/scripts/i18n-specs.pl" "$f" > "$spec_loc"
    if ! diff -q "$spec_cn" "$spec_loc" >/dev/null 2>&1; then
        bad=1
        printf '，占位符不一致：\n'
        diff "$spec_cn" "$spec_loc" | sed 's/^/   /'
    else
        printf '，占位符一致\n'
    fi
    rm -f "$tw" "$spec_loc"
done
rm -f "$cn" "$spec_cn"

APK=${1:-app/build/outputs/apk/full/debug/app-full-debug.apk}
if [ -f "$APK" ] && command -v aapt2 >/dev/null 2>&1; then
    for loc in zh-rTW zh-rHK; do
        n=$(aapt2 dump resources "$APK" 2>/dev/null | grep -c "$loc")
        [ "$n" -gt 0 ] && printf 'APK 内 %s 条目 %s 行\n' "$loc" "$n"
    done
fi
exit "$bad"
