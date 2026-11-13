#!/usr/bin/env bash
# 生成桌面端的 Kotlin 字符串表（STRINGS + BY_ZH）。
#
# 一次 perl 进程读完所有输入，不再按 key 起子进程 —— 之前那版每条文案都 fork 一次
# awk/sed，144 键 × 7 语言要起上千个进程，跑几十秒还容易被当成卡死。
set -euo pipefail
cd "$(dirname "$0")/.." || exit 1
perl scripts/i18n-to-desktop.pl
