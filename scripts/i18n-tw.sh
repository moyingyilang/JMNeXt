#!/usr/bin/env bash
# 从 values/strings.xml 批量生成 values-zh-rTW/strings.xml。
#
# 转换用 OpenCC 的 s2twp（简体 → 台湾正体，含台湾用词），再按下面的词表覆盖几个
# 我们说惯的词。改完简体就重跑一遍，别手改繁中文件。
#
# OpenCC 装在 chroot 里，所以脚本会在需要时通过 chroot 调用它。
set -euo pipefail
cd "$(dirname "$0")/.." || exit 1

SRC=app/src/main/res/values/strings.xml
DST=app/src/main/res/values-zh-rTW/strings.xml
CHROOT=${CHROOT:-/data/data/com.termux/files/home/.local/share/tmoe-linux/containers/chroot/debian-sid_arm64}

convert() {
    if command -v opencc >/dev/null 2>&1; then
        opencc -c s2twp.json
    else
        # 宿主没有 opencc：拷进 chroot 转完再拿回来
        local tmp
        tmp=$(mktemp)
        cat > "$tmp"
        chmod 644 "$tmp"
        su -c "cp '$tmp' '$CHROOT/tmp/in.xml' && chroot '$CHROOT' /bin/bash -c 'export PATH=/usr/bin:/bin; opencc -c s2twp.json < /tmp/in.xml > /tmp/out.xml'" >/dev/null
        cat "$CHROOT/tmp/out.xml"
        rm -f "$tmp"
        su -c "rm -f '$CHROOT/tmp/in.xml' '$CHROOT/tmp/out.xml'" || true
    fi
}

tmp=$(mktemp)
convert < "$SRC" > "$tmp"

# 术语覆盖：OpenCC 给的是通用译法，这里换成我们惯用的
sed -i \
    -e 's/遮蔽/封鎖/g' \
    -e 's/網盤/網路硬碟/g' \
    -e 's/釋出頁/發佈頁/g' \
    -e 's/專案主頁/專案首頁/g' \
    -e 's/>許可</>授權</g' \
    -e 's/問題反饋/問題回報/g' \
    -e 's/使用者名稱/使用者名稱/g' \
    "$tmp"

# 去掉转换后残留的简体注释行（注释不参与翻译）
grep -v '^    <!--' "$tmp" > "$tmp.clean"

# 补回文件头注释
{
    head -n 2 "$tmp.clean"
    echo '    <!-- 由 scripts/i18n-tw.sh 从 values/strings.xml 生成，别手改 -->'
    tail -n +3 "$tmp.clean"
} > "$DST"
rm -f "$tmp" "$tmp.clean"

printf '已生成 %s（%s 个键）\n' "$DST" "$(grep -c '<string name=' "$DST")"
