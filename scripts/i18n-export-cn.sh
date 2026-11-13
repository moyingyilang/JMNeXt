#!/usr/bin/env bash
# 从 values/strings.xml 导出 zh-CN.tsv，供桌面端与比对脚本读。
# 其余语言的 TSV 是人工译文源（en/ja/ru/ko），繁中由 i18n-gen.sh 生成，都不从这里导出。
set -euo pipefail
cd "$(dirname "$0")/.." || exit 1
SRC=app/src/main/res/values/strings.xml
DST=i18n/translations/zh-CN.tsv
perl -ne 'print "$1\t$2\n" if /<string name="([^"]*)">(.*)<\/string>/' "$SRC" > "$DST"
printf '已导出 %s（%s 个键）\n' "$DST" "$(wc -l < "$DST" | tr -d ' ')"
