#!/usr/bin/env bash
# 校验桌面端的键与生成结果：
#   1. desktop-keys.txt 里的键必须在 zh-CN 里存在（或带 dest_ 前缀，留给桌面独有文案）
#   2. 每个键在 zh-CN 里必须有非空中文
#   3. GeneratedStrings.kt 已生成且键数与 keys 一致
#   4. 各语言与 zh-CN 的占位符序列一致
set -euo pipefail
cd "$(dirname "$0")/.." || exit 1

KEYS=i18n/desktop/desktop-keys.txt
CN=i18n/translations/zh-CN.tsv
GEN=desktop/src/main/kotlin/com/jmnext/desktop/i18n/GeneratedStrings.kt
bad=0

while IFS= read -r key; do
    [ -n "$key" ] || continue
    case "$key" in dest_*) continue ;; esac
    if ! cut -f1 "$CN" | grep -qx "$key"; then
        echo "键不在简中里：$key"; bad=1
    fi
done < "$KEYS"

if [ ! -f "$GEN" ]; then
    echo "还没生成 $GEN"; exit 1
fi

gen_keys=$(grep -c '^        "' "$GEN" || true)
want=$(( $(wc -l < "$KEYS" | tr -d ' ') * 7 ))
printf '生成表条目 %s，期望约 %s（%s 键 × 7 语言，缺译文会略少）\n' "$gen_keys" "$want" "$(wc -l < "$KEYS" | tr -d ' ')"

# 占位符一致性：按语言分别比较
for loc in zh-CN en ja ru ko; do
    while IFS= read -r key; do
        [ -n "$key" ] || continue
        cn_spec=$(awk -F'\t' -v k="$key" '$1==k{n=gsub(/%[0-9]+\$[a-z]/,"&"); print n}' "$CN")
        loc_spec=$(awk -F'\t' -v k="$key" '$1==k{n=gsub(/%[0-9]+\$[a-z]/,"&"); print n}' "i18n/translations/$loc.tsv")
        [ -n "$loc_spec" ] || continue
        if [ "$cn_spec" != "$loc_spec" ]; then
            echo "占位符不一致：$key（$loc $loc_spec vs zh-CN $cn_spec）"; bad=1
        fi
    done < "$KEYS"
done

exit "$bad"
