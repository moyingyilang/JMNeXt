#!/bin/sh
# 拿到官方新版 APK 后，用它核对并同步上游信息。
#
#   ./scripts/apk-sync.sh <官方 APK>
#
# 做的事：
#   1. 读包名/版本，和项目里记的官方版本常量比对；
#   2. 从 assets/public/static/js/main.*.js 抽出 zh-TW / zh-CN 两套 i18next 字典，落到 i18n/upstream/；
#   3. 和上一份字典比，打印新增/删除/改动的键数。
#
# 不改代码，只出报告和字典；要不要动代码由人决定。
set -eu
cd "$(dirname "$0")/.." || exit 1
APK=${1:?用法: scripts/apk-sync.sh <官方 APK>}
[ -f "$APK" ] || { echo "找不到 $APK"; exit 1; }

WORK=.build/apk-sync
rm -rf "$WORK"; mkdir -p "$WORK"
unzip -q "$APK" -d "$WORK/x"

BT=${ANDROID_BUILD_TOOLS:-$HOME/android-sdk/build-tools/36.0.0}
if [ -x "$BT/aapt2" ]; then
  echo "== 包信息"
  "$BT/aapt2" dump badging "$APK" 2>/dev/null | sed -n '1p' | cut -c1-120
fi

[ -d i18n/upstream ] && rm -rf i18n/previous && cp -r i18n/upstream i18n/previous
JS=$(ls "$WORK"/x/assets/public/static/js/main.*.js 2>/dev/null | head -1)
[ -n "$JS" ] || { echo "包里没有 assets/public/static/js/main.*.js，可能不是同一套前端"; exit 2; }
echo "== 抽字典：$(basename "$JS")"
node scripts/extract-i18n.js "$JS" i18n/upstream

if [ -d i18n/previous ]; then
  echo "== 与上一份比较"
  for l in zh-CN zh-TW; do
    old=i18n/previous/$l.json; new=i18n/upstream/$l.json
    [ -f "$old" ] || continue
    jq -r --slurpfile o "$old" --slurpfile n "$new" -n '
      def keys: [paths(scalars) | join(".")];
      ($o[0]|keys) as $ok | ($n[0]|keys) as $nk
      | "  \($ok|length) -> \($nk|length) 条；新增 \($nk-$ok|length)，删除 \($ok-$nk|length)"' 2>/dev/null || true
  done
fi

echo "== 版本常量（项目里记的官方版本）"
grep -rn "DEFAULT_CLIENT_VERSION" --include=*.kt shared app 2>/dev/null | head -3 | sed 's/^/  /'
