#!/usr/bin/env bash
# 扫桌面端写死的中文字面量，输出 file:line<TAB>文案 的候选清单。
# 注释行跳过；同一文件同一行只取第一条。
set -euo pipefail
cd "$(dirname "$0")/.." || exit 1
OUT=i18n/desktop/candidates.tsv
: > "$OUT"
while IFS= read -r line; do
    file=${line%%:*}
    rest=${line#*:}
    lineno=${rest%%:*}
    text=${rest#*:}
    # 去掉两端引号
    text=${text#\"}
    text=${text%\"}
    printf '%s:%s\t%s\n' "$file" "$lineno" "$text" >> "$OUT"
done < <(grep -rnoE '"[^"]*[一-龥][^"]*"' desktop/src/main/kotlin --include=*.kt \
         | grep -vE ':\s*(//|\*)' || true)
printf '候选 %s 条，去重后 %s 条\n' \
  "$(wc -l < "$OUT" | tr -d ' ')" \
  "$(cut -f2 "$OUT" | sort -u | wc -l | tr -d ' ')"
