#!/usr/bin/env bash
# 从 i18n/translations/<代码>.tsv 生成 app/src/main/res/values-<代码>/strings.xml。
# TSV 每行是 key<TAB>译文；缺键或有多余键时拒绝生成，避免出现半份翻译。
#
#   ./scripts/i18n-from-tsv.sh en
set -euo pipefail
cd "$(dirname "$0")/.." || exit 1

CODE=${1:?用法: i18n-from-tsv.sh <语言代码，如 en>}
TSV=i18n/translations/$CODE.tsv
SRC=app/src/main/res/values/strings.xml

case "$CODE" in
    ??-??) DIR="app/src/main/res/values-${CODE%%-*}-r${CODE##*-}" ;;
    *)     DIR="app/src/main/res/values-$CODE" ;;
esac
DST=$DIR/strings.xml

tmp=$(mktemp)
{
    echo '<resources>'
    while IFS=$'\t' read -r key value; do
        case "$key" in ''|'#'*) continue ;; esac
        # XML 与 Android 转义：& 要写成实体，单引号要写成 \'（否则 aapt2 报错）
        value=$(printf "%s" "$value" | sed "s/&/\&amp;/g; s/\x27/\\\\\x27/g")
        printf '    <string name="%s">%s</string>\n' "$key" "$value"
    done < "$TSV"
    echo '</resources>'
} > "$tmp"

# 键集合校验
src_keys=$(mktemp); dst_keys=$(mktemp)
sed -n 's/.*<string name="\([^"]*\)".*/\1/p' "$SRC" | sort > "$src_keys"
sed -n 's/.*<string name="\([^"]*\)".*/\1/p' "$tmp" | sort > "$dst_keys"
missing=$(comm -23 "$src_keys" "$dst_keys")
extra=$(comm -13 "$src_keys" "$dst_keys")
if [ -n "$missing$extra" ]; then
    printf 'TSV 与简中不一致，未生成：\n'
    [ -n "$missing" ] && printf '%s\n' "$missing" | sed 's/^/  缺 /'
    [ -n "$extra" ] && printf '%s\n' "$extra" | sed 's/^/  多 /'
    rm -f "$tmp" "$src_keys" "$dst_keys"
    exit 1
fi
rm -f "$src_keys" "$dst_keys"

mkdir -p "$DIR"
mv "$tmp" "$DST"
printf '已生成 %s（%s 个键）\n' "$DST" "$(grep -c '<string name=' "$DST")"
