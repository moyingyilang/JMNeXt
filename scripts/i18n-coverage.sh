#!/usr/bin/env bash
# 统计桌面端还有多少中文没走 tl()：按文件列出未包裹的含中文字面量行数。
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
total=0
printf '%-46s %6s\n' 文件 未包裹
for f in desktop/src/main/kotlin/com/jmnext/desktop/*.kt; do
    n=$(grep -nE '"[^"]*[一-龥][^"]*"' "$f" \
        | grep -vE ':\s*(\*|//)' \
        | grep -vE 'System\.err\.println|Log\.(line|error|info)' \
        | grep -vE '\btl\(|\btlf\(|I18n\.tr[i]?\(' \
        | wc -l | tr -d ' ')
    if [ "$n" -gt 0 ]; then
        printf '%-46s %6s\n' "$(basename "$f")" "$n"
        total=$((total + n))
    fi
done
printf '%-46s %6s\n' 合计 "$total"
