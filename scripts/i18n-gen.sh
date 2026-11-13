#!/usr/bin/env bash
# 从 values/strings.xml 生成一份语言资源。
#
#   ./scripts/i18n-gen.sh zh-TW s2twp     # 台湾正体
#   ./scripts/i18n-gen.sh zh-HK s2hk     # 香港正体
#   ./scripts/i18n-gen.sh en skeleton    # 英文占位骨架（值是 key 本身，等人工翻）
#
# 第二个参数是 OpenCC 配置名，或 skeleton 表示只生成空骨架。
set -euo pipefail
cd "$(dirname "$0")/.." || exit 1

LOCALE=${1:?用法: i18n-gen.sh <语言代码，如 zh-TW> <OpenCC 配置|skeleton>}
MODE=${2:-skeleton}
SRC=app/src/main/res/values/strings.xml
case "$LOCALE" in
    ??-??) DST="app/src/main/res/values-${LOCALE%%-*}-r${LOCALE##*-}/strings.xml" ;;
    *) DST="app/src/main/res/values-$LOCALE/strings.xml" ;;
esac
CHROOT=${CHROOT:-/data/data/com.termux/files/home/.local/share/tmoe-linux/containers/chroot/debian-sid_arm64}

mkdir -p "$(dirname "$DST")"

if [ "$MODE" = skeleton ]; then
    {
        echo '<resources>'
        sed -n 's/.*<string name="\([^"]*\)".*/\1/p' "$SRC" |
            sed 's/.*/    <string name="&">&<\/string>/'
        echo '</resources>'
    } > "$DST"
    printf '已生成骨架 %s（%s 个键，值是 key 本身）\n' "$DST" "$(grep -c '<string name=' "$DST")"
    exit 0
fi

tmp=$(mktemp)
if command -v opencc >/dev/null 2>&1; then
    opencc -c "$MODE.json" < "$SRC" > "$tmp"
else
    su -c "cp '$SRC' '$CHROOT/tmp/in.xml' && chroot '$CHROOT' /bin/bash -c 'export PATH=/usr/bin:/bin; opencc -c $MODE.json < /tmp/in.xml > /tmp/out.xml'" >/dev/null
    cp "$CHROOT/tmp/out.xml" "$tmp"
  test -s "$tmp" || { echo "转换失败：输出为空" >&2; exit 1; }
    su -c "rm -f '$CHROOT/tmp/in.xml' '$CHROOT/tmp/out.xml'" || true
fi
# 每门语言的用词覆盖（可选）
if [ -f "i18n/overrides/$LOCALE.sed" ]; then
    sed -i -f "i18n/overrides/$LOCALE.sed" "$tmp"
fi


{
    head -n 2 "$tmp"
    echo "    <!-- 由 scripts/i18n-gen.sh $LOCALE $MODE 生成，别手改 -->"
    tail -n +3 "$tmp" | grep -v '^    <!--'
} > "$DST"
rm -f "$tmp"
printf '已生成 %s（%s 个键）\n' "$DST" "$(grep -c '<string name=' "$DST")"
