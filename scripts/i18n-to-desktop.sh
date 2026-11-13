#!/usr/bin/env bash
# 生成桌面端的 Kotlin 字符串表。
#
# 键来自 i18n/desktop/desktop-keys.txt；文案按语言取：
#   zh-CN → i18n/translations/zh-CN.tsv（由 values/strings.xml 导出）
#   en/ja/ru/ko → i18n/translations/<代码>.tsv（人工译文源）
#   zh-rTW/zh-rHK → app/src/main/res/values-<代码>/strings.xml（生成产物）
set -euo pipefail
cd "$(dirname "$0")/.." || exit 1

KEYS=i18n/desktop/desktop-keys.txt
OUT=desktop/src/main/kotlin/com/jmnext/desktop/i18n/GeneratedStrings.kt
LOCALES="zh-CN zh-rTW zh-rHK en ja ru ko"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

dump_locale() { # $1=locale  $2=输出文件
    case "$1" in
        zh-rTW|zh-rHK)
            perl -ne 'print "$1\t$2\n" if /<string name="([^"]*)">(.*)<\/string>/' \
                "app/src/main/res/values-$1/strings.xml" > "$2"
            ;;
        *)
            cp "i18n/translations/$1.tsv" "$2"
            ;;
    esac
}

for loc in $LOCALES; do
    dump_locale "$loc" "$TMP/$loc.tsv"
done

mkdir -p "$(dirname "$OUT")"
{
    echo 'package com.jmnext.desktop.i18n'
    echo
    echo '// 由 scripts/i18n-to-desktop.sh 生成，别手改'
    echo '// 键来自 i18n/desktop/desktop-keys.txt'
    echo
    echo 'internal val STRINGS: Map<String, Map<String, String>> = mapOf('
    for loc in $LOCALES; do
        echo "    \"$loc\" to mapOf("
        while IFS= read -r key; do
            [ -n "$key" ] || continue
            value=$(awk -F'\t' -v k="$key" '$1==k{v=$2} END{if (v!="") print v}' "$TMP/$loc.tsv")
            [ -n "$value" ] || continue
            value=$(printf '%s' "$value" | sed 's/\\/\\\\/g; s/"/\\"/g')
            printf '        "%s" to "%s",\n' "$key" "$value"
        done < "$KEYS"
        echo '    ),'
    done
    echo ')'
    echo
    echo '/** 某门语言的表；缺语言时回落到简中。 */'
    echo 'internal fun stringsFor(locale: String): Map<String, String> ='
    echo '    STRINGS[locale] ?: STRINGS.getValue("zh-CN")'
} > "$OUT"

printf '已生成 %s（%s 个键 × %s 门语言）\n' "$OUT" "$(wc -l < "$KEYS" | tr -d ' ')" "$(echo $LOCALES | wc -w | tr -d ' ')"
