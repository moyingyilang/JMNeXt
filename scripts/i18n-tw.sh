#!/usr/bin/env bash
# 繁中（台湾）—— 兼容旧入口，实际工作交给 i18n-gen.sh。
set -euo pipefail
cd "$(dirname "$0")/.." || exit 1
exec ./scripts/i18n-gen.sh zh-TW s2twp
