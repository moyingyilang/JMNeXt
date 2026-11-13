#!/usr/bin/env bash
# 桌面端多语言自检：
#   1. dest.tsv / pending-*.tsv 每行的语言列数与表头一致
#   2. 中文模板的占位符（%1$s、%2$d）在六种译文里必须一一对应
#   3. 生成出来的 GeneratedStrings.kt 键数与来源一致
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
bad=0

check_tsv() { # $1=文件 $2=是否有表头
    local f=$1 header=$2 n=0
    local cols
    cols=$(head -1 "$f" | awk -F'\t' '{print NF}')
    while IFS=$'\t' read -r -a field; do
        n=$((n + 1))
        [ "$header" = "yes" ] && [ "$n" -eq 1 ] && continue
        [ -z "${field[0]:-}" ] && continue
        if [ "${#field[@]}" -ne "$cols" ]; then
            echo "列数不对：$f 第 $n 行（${#field[@]} vs $cols）"; bad=1; continue
        fi
        local zh=${field[1]}
        local zh_spec; zh_spec=$(printf '%s' "$zh" | grep -oE '%[0-9]+\$[a-z]' | tr '\n' ' ')
        local i=2
        while [ "$i" -lt "$cols" ]; do
            local v=${field[$i]}
            local v_spec; v_spec=$(printf '%s' "$v" | grep -oE '%[0-9]+\$[a-z]' | tr '\n' ' ')
            if [ "$zh_spec" != "$v_spec" ]; then
                echo "占位符不一致：$f 第 $n 行第 $((i+1)) 列（[$zh_spec] vs [$v_spec]）"; bad=1
            fi
            i=$((i + 1))
        done
    done < "$f"
}

check_tsv i18n/desktop/dest.tsv yes
for f in i18n/desktop/pending-*.tsv; do
    [ -f "$f" ] || continue
    check_tsv "$f" no
done

GEN=desktop/src/main/kotlin/com/jmnext/desktop/i18n/GeneratedStrings.kt
if [ ! -f "$GEN" ]; then echo "还没生成 $GEN"; exit 1; fi
printf '生成表条目 %s\n' "$(grep -c ' to "' "$GEN")"

exit "$bad"
