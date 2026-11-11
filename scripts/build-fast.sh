#!/bin/sh
# 平时用这个编，别直接 assembleDebug（那是 full 和 lite 一起编）。
#
#   ./scripts/build-fast.sh check     只编 Kotlin，看有没有错
#   ./scripts/build-fast.sh apk       编完打 full debug
#   ./scripts/build-fast.sh apk lite  打 lite debug
#   ./scripts/build-fast.sh release   打 release
#   ./scripts/build-fast.sh clean     清掉 app 产物
#
# 默认 --offline：依赖都在缓存里，联网检查反而慢。要拉新依赖时加 --online。
set -eu
cd "$(dirname "$0")/.." || exit 1
OFFLINE="--offline"
[ "${2:-}" = "--online" ] && OFFLINE=""
GRADLE=${GRADLE:-gradle}

case "${1:-check}" in
check) exec "$GRADLE" --console=plain $OFFLINE :app:compileFullDebugKotlin ;;
apk)
    if [ "${2:-}" = "lite" ]; then
        exec "$GRADLE" --console=plain $OFFLINE :app:assembleLiteDebug
    fi
    exec "$GRADLE" --console=plain $OFFLINE :app:assembleFullDebug ;;
release) exec "$GRADLE" --console=plain $OFFLINE :app:assembleRelease ;;
clean) exec "$GRADLE" --console=plain $OFFLINE :app:clean ;;
*) sed -n '2,14p' "$0" ;;
esac
