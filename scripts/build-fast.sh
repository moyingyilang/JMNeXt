#!/bin/sh
# build-fast.sh —— 日常迭代用的快速构建（只做必要的活）
#
# 实测（同一台机、热 daemon）：
#   联网 assembleFullDebug，全部 up-to-date：29 秒
#   --offline 同样任务：                    7 秒
#   差的那 20 多秒是依赖元数据/动态版本的网络检查，与编译无关。
#
# 用法：
#   ./scripts/build-fast.sh check          只编译 Kotlin（最快，先看有没有错）
#   ./scripts/build-fast.sh apk            编译 + 打 full debug 包（单变体）
#   ./scripts/build-fast.sh apk lite       编译 + 打 lite debug 包
#   ./scripts/build-fast.sh release        打 release 包（full/lite 两个都打）
#   ./scripts/build-fast.sh clean          清掉 app 构建产物
#
# 约定：
#   - 默认 --offline：依赖已在缓存里时不会再联网；需要拉新依赖时加 --online。
#   - 不打 assembleDebug（那是 full + lite 一起），日常只编需要的那一个变体。
set -eu
cd "$(dirname "$0")/.." || exit 1
OFFLINE="--offline"
case "${2:-}" in
    --online) OFFLINE="" ;;
esac
GRADLE=${GRADLE:-gradle}

case "${1:-check}" in
check)
    exec "$GRADLE" --console=plain $OFFLINE :app:compileFullDebugKotlin
    ;;
apk)
    if [ "${2:-}" = "lite" ]; then
        exec "$GRADLE" --console=plain $OFFLINE :app:assembleLiteDebug
    fi
    exec "$GRADLE" --console=plain $OFFLINE :app:assembleFullDebug
    ;;
release)
    exec "$GRADLE" --console=plain $OFFLINE :app:assembleRelease
    ;;
clean)
    exec "$GRADLE" --console=plain $OFFLINE :app:clean
    ;;
*)
    sed -n '2,20p' "$0"
    ;;
esac
