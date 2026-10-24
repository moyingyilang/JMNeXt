#!/usr/bin/env bash
# SPDX-License-Identifier: MPL-2.0
# 打包闸门：输入未变且产物齐全时跳过本次打包。
#
# 背景：完整打包链实测 433 秒（Linux aarch64 84 秒 + Linux x86_64 170 秒 + 两个 ZIP 69 秒 + 两个 exe 97 秒），
# 而"只改了文档"或"只改了一个平台"时其余步骤会毫无意义地全量重跑。
#
# 指纹 = 输入文件的（路径 + 大小 + 修改时间）排序后取 sha256。任何编辑都会改 mtime，
# 所以"内容变了而指纹不变"实际不会发生。实测两个方向：未改动 → 指纹相同；touch → 指纹不同。
# 反例：`stat -c %Y` 只有秒级精度，同一秒内 touch 检测不到，故不用它。
#
# 安全原则：**宁可多跑，绝不漏更**。指纹为空、产物清单为空、任一产物不存在 → 一律不跳过。
# 强制重跑：FORCE=1
_gate_fp() { local p
  for p in "$@"; do
    if [ -d "$p" ]; then find "$p" -type f -printf '%p %s %T@\n' 2>/dev/null
    elif [ -f "$p" ]; then printf '%s %s %s\n' "$p" "$(stat -c %s "$p")" "$(stat -c %Y "$p")"
    else printf 'MISSING %s\n' "$p"; fi
  done | LC_ALL=C sort | sha256sum | cut -d' ' -f1; }

gate_begin() { # 用法: gate_begin <名字> <输出目录> <输入路径...>；返回 1 表示应跳过
  local name="$1" out="$2"; shift 2
  GATE_NAME="$name"; GATE_OUT="$out"; GATE_STAMP="$out/.gate-$name.sha"
  GATE_FP="$(_gate_fp "$@")"
  [ "${FORCE:-0}" = "1" ] && return 0
  [ -n "$GATE_FP" ] || return 0
  [ -n "$GATE_EXPECT" ] || return 0
  [ -f "$GATE_STAMP" ] || return 0
  [ "$(cat "$GATE_STAMP")" = "$GATE_FP" ] || return 0
  local f; for f in $GATE_EXPECT; do [ -s "$f" ] || return 0; done
  echo "  跳过打包：输入未变且产物齐全（$name）。要强制重跑设 FORCE=1"
  return 1
}
gate_commit() { mkdir -p "$GATE_OUT"; printf '%s\n' "$GATE_FP" > "$GATE_STAMP"; echo "  已记录输入指纹（$GATE_NAME）"; }
