#!/usr/bin/env bash
# 生成桌面端的 Kotlin 字符串表。
#
# 键来自 i18n/desktop/desktop-keys.txt；文案按语言取：
#   zh-CN → i18n/translations/zh-CN.tsv（由 values/strings.xml 导出）
#   en/ja/ru/ko → i18n/translations/<代码>.tsv（人工译文源）
#   zh-rTW/zh-rHK → app/src/main/res/values-<代码>/strings.xml（生成产物）
# 键名以 dest_ 开头的只在桌面端存在，值取自 i18n/desktop/dest.tsv（第一行是表头）。
set -euo pipefail
cd "$(dirname "$0")/.." || exit 1

KEYS=i18n/desktop/desktop-keys.txt
OUT=desktop/src/main/kotlin/com/jmnext/desktop/i18n/GeneratedStrings.kt
LOCALES="zh-CN zh-rTW zh-rHK en ja ru ko"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

dump_locale() {
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
cp i18n/desktop/dest.tsv "$TMP/dest.tsv"

dest_value() {
    awk -F'\t' -v k="$2" -v loc="$1" '
        NR==1 { for (i=1;i<=NF;i++) if ($i==loc) col=i; next }
        $1==k && col>0 { print $col; exit }
    ' "$TMP/dest.tsv"
}

# 生成一行 Kotlin 字符串字面量：转义反斜杠、引号、美元符号（Kotlin 会把 $ 当模板）
kotlin_literal() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\$/\\$/g'
}

mkdir -p "$(dirname "$OUT")"
{
    echo 'package com.jmnext.desktop.i18n'
    echo
    echo '// 由 scripts/i18n-to-desktop.sh 生成，别手改'
    echo '// 键来自 i18n/desktop/desktop-keys.txt，桌面独有文案来自 i18n/desktop/dest.tsv'
    echo
    echo 'internal val STRINGS: Map<String, Map<String, String>> = mapOf('
    for loc in $LOCALES; do
        echo "    \"$loc\" to mapOf("
        while IFS= read -r key; do
            [ -n "$key" ] || continue
            case "$key" in
                dest_*) value=$(dest_value "$loc" "$key") ;;
                *)      value=$(awk -F'\t' -v k="$key" '$1==k{v=$2} END{if (v!="") print v}' "$TMP/$loc.tsv") ;;
            esac
            [ -n "$value" ] || continue
            printf '        "%s" to "%s",\n' "$key" "$(kotlin_literal "$value")"
        done < "$KEYS"
        echo '    ),'
    done
    echo ')'
    echo
    echo '/** 某门语言的表；缺语言时回落到简中。 */'
    echo 'internal fun stringsFor(locale: String): Map<String, String> ='
    echo '    STRINGS[locale] ?: STRINGS.getValue("zh-CN")'
} > "$OUT"

{
    echo
    echo '/** 中文原文 → 该语言的译文；桌面端现有文案以中文为键，便于逐屏替换。 */'
    echo 'internal val BY_ZH: Map<String, Map<String, String>> = mapOf('
    for loc in $LOCALES; do
        echo "    \"$loc\" to mapOf("
        # 先放 Android 的键（以 zh-CN 值作键），再放桌面独有的 dest_ 文案
        while IFS=$'\t' read -r key zh; do
            [ -n "$zh" ] || continue
            val=$(awk -F'\t' -v k="$key" '$1==k{v=$2} END{if (v!="") print v}' "$TMP/$loc.tsv")
            [ -n "$val" ] || continue
            [ "$zh" = "$val" ] && continue
            printf '        "%s" to "%s",\n' "$(kotlin_literal "$zh")" "$(kotlin_literal "$val")"
        done < "$TMP/zh-CN.tsv"
        awk -F'\t' -v loc="$loc" '
            NR==1 { for (i=1;i<=NF;i++) { if ($i==loc) col=i; if ($i=="zh-CN") zhcol=i } next }
            col>0 && zhcol>0 && $zhcol!="" && $col!="" && $zhcol!=$col {
                printf "%s\t%s\n", $zhcol, $col
            }
        ' "$TMP/dest.tsv" | while IFS=$'\t' read -r zh val; do
            printf '        "%s" to "%s",\n' "$(kotlin_literal "$zh")" "$(kotlin_literal "$val")"
        done
        echo '    ),'
    done
    echo ')'
    echo
    echo '/** 按中文原文取译文；没有对应译文就返回原文。 */'
    echo 'internal fun byZhFor(locale: String): Map<String, String> ='
    echo '    BY_ZH[locale] ?: emptyMap()'
} >> "$OUT"

printf '已生成 %s（%s 个键 × %s 门语言）\n' "$OUT" "$(wc -l < "$KEYS" | tr -d ' ')" "$(echo $LOCALES | wc -w | tr -d ' ')"
