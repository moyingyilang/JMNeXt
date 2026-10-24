#!/usr/bin/env bash
#
# 构建并打包 Linux 桌面版（aarch64），出包前做产物自检。
#
# 为什么要有这个脚本：2.0.0 期间我出过两次"改了代码但打出来的包是旧的"，
# 两次都靠人肉发现，代价是用户白跑一趟。所以自检不是可选项 —— 任何一项不过就拒绝出包。
#
# 必须在容器里运行（需要 glibc 的 JDK 与 dpkg-deb/rpmbuild/mksquashfs）。
# 用法：  scripts/package-linux.sh [输出目录]      默认 dist
set -euo pipefail
trap 'rm -rf "${CHK:-}" "${STAGE:-}" "${TOP:-}" "${AD:-}" "${W:-}" "${D:-}" 2>/dev/null || true' EXIT   # 退出即清临时目录，避免中断/超时把 staging 留在容器 /tmp

cd "$(dirname "$0")/.."
V=${VERSION:-$(grep -oE '[0-9]+.[0-9]+.[0-9]+' src/main/kotlin/com/jmnext/desktop/Version.kt | tail -1)}
[ -n "$V" ] || { echo '拿不到版本号（Version.kt）'; exit 1; }
OUT=${1:-dist}
. "$(dirname "$0")/lib-gate.sh"
GATE_EXPECT="$OUT/Linux-aarch64-$V.tar.gz $OUT/Linux-aarch64-$V.deb $OUT/Linux-aarch64-$V.rpm $OUT/Linux-aarch64-$V.AppImage"
if ! gate_begin linux-aarch64 "$OUT" build/compose/binaries/main/app/jmnext src/main/resources/icon.png; then exit 0; fi
GRADLE=${GRADLE:-/opt/gradle-9.8.0/bin/gradle}
APP=build/compose/binaries/main/app/jmnext
ICON=${ICON:-$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/icon.png}

# ── 自检用的标记串 ────────────────────────────────────────────────
# 说明：这里必须用 grep 而不是 strings —— strings 默认只输出 ASCII，
# 中文会被整段丢弃，用它查中文等于没查（这个坑真实发生过）。
# 代码演进后请同步更新这两组串。
MARK_NEW="打开作品"          # 新代码里应当存在
MARK_OLD=""                  # 旧代码里的、必须不存在的（留空表示跳过）

echo "== 1/6 构建 =="
"$GRADLE" --console=plain createDistributable | tail -2

echo "== 2/6 产物自检 =="
# a) 应用 jar 只能有一个：带内容哈希的文件名在改代码后会多出一个旧 jar，
#    而类路径里旧的先被加载，表现成"代码改了但行为没变"。
JAR_COUNT=$(ls "$APP"/lib/app/JMNeXt-Desktop-*.jar 2>/dev/null | wc -l)
if [ "$JAR_COUNT" -ne 1 ]; then
  echo "  自检失败：应用 jar 有 $JAR_COUNT 个（应为 1，旧 jar 未被清理）"; exit 1
fi
# b) 启动类必须是带包名的全限定名（裸 MainKt 会匹配到默认包里的旧类）
grep -q "app.mainclass=com.jmnext.desktop.MainKt" "$APP"/lib/app/jmnext.cfg \
  || { echo "  自检失败：启动类不是 com.jmnext.desktop.MainKt"; exit 1; }
# c) 新旧标记
CHK=$(mktemp -d)
JAR=$(find "$APP/lib/app" -maxdepth 1 -name "JMNeXt-Desktop-*.jar" -print -quit)   # 不用管道：head 提前关管道会让 find 收到 SIGPIPE，pipefail 下静默退出
[ -n "$JAR" ] || { echo "  自检失败：找不到应用 jar"; rm -rf "$CHK"; exit 1; }
unzip -q "$JAR" "*.class" -d "$CHK"   # 不要 cd 进临时目录：JAR 是相对路径，cd 之后就找不到了
LEGACY=$(grep -rl '构建链验证窗口' "$CHK" 2>/dev/null | wc -l) || true
[ "$LEGACY" -eq 0 ] || { echo "  自检失败：早期验证窗口的类仍在（$LEGACY 个文件）"; rm -rf "$CHK"; exit 1; }
NEW=$(grep -rl "$MARK_NEW" "$CHK" 2>/dev/null | wc -l) || true
[ "$NEW" -ge 1 ] || { echo "  自检失败：新标记「$MARK_NEW」在产物里找不到"; rm -rf "$CHK"; exit 1; }
if [ -n "$MARK_OLD" ]; then
  OLD=$(grep -rl "$MARK_OLD" "$CHK" 2>/dev/null | wc -l) || true
  [ "$OLD" -eq 0 ] || { echo "  自检失败：旧标记「$MARK_OLD」仍在（$OLD 个文件）"; rm -rf "$CHK"; exit 1; }
fi
rm -rf "$CHK"
echo "  应用 jar 1 个、启动类正确、标记检查通过"

echo "== 3/6 便携包 =="
rm -rf "$OUT" && mkdir -p "$OUT"
tar czf "$OUT/Linux-aarch64-$V.tar.gz" -C "$APP" .

echo "== 4/6 deb =="
STAGE=$(mktemp -d)/jmnext
mkdir -p "$STAGE/DEBIAN" "$STAGE/opt/jmnext" "$STAGE/usr/bin" \
  "$STAGE/usr/share/applications" "$STAGE/usr/share/icons/hicolor/256x256/apps"
cp -a "$APP/." "$STAGE/opt/jmnext/"
printf '#!/bin/sh\nexec /opt/jmnext/bin/jmnext "$@"\n' > "$STAGE/usr/bin/jmnext"
chmod 755 "$STAGE/usr/bin/jmnext"
cp "$ICON" "$STAGE/usr/share/icons/hicolor/256x256/apps/jmnext.png"
printf '[Desktop Entry]\nType=Application\nName=JMNeXt\nExec=/opt/jmnext/bin/jmnext\nIcon=jmnext\nTerminal=false\nCategories=Utility;Graphics;\n' \
  > "$STAGE/usr/share/applications/jmnext.desktop"
cat > "$STAGE/DEBIAN/control" <<'CTL'
Package: jmnext
Version: @V@
Architecture: arm64
Maintainer: moyingyilang
Depends: libc6
Section: utils
Priority: optional
Description: JMNeXt desktop client
 A third-party JMComic client built with Kotlin and Compose Desktop.
CTL
sed -i "s/@V@/$V/" "$STAGE/DEBIAN/control"
dpkg-deb --build --root-owner-group "$STAGE" "$OUT/Linux-aarch64-$V.deb" >/dev/null

echo "== 5/6 rpm =="
TOP=$(mktemp -d)
mkdir -p "$TOP"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
cp -a "$APP" "$TOP/SOURCES/jmnext-app"
cp "$ICON" "$TOP/SOURCES/jmnext.png"
cat > "$TOP/SPECS/jmnext.spec" <<'SPEC'
Name:           jmnext
Version:        @V@
Release:        1
Summary:        JMNeXt desktop client
License:        AGPL-3.0-only
BuildArch:      aarch64
Requires:       glibc
%description
A third-party JMComic client built with Kotlin and Compose Desktop.
%prep
%build
%install
rm -rf %{buildroot}
mkdir -p %{buildroot}/opt/jmnext
cp -a %{_sourcedir}/jmnext-app/. %{buildroot}/opt/jmnext/
mkdir -p %{buildroot}/usr/bin %{buildroot}/usr/share/icons/hicolor/256x256/apps
printf '#!/bin/sh\nexec /opt/jmnext/bin/jmnext "$@"\n' > %{buildroot}/usr/bin/jmnext
chmod 755 %{buildroot}/usr/bin/jmnext
cp %{_sourcedir}/jmnext.png %{buildroot}/usr/share/icons/hicolor/256x256/apps/jmnext.png
%files
/opt/jmnext
/usr/bin/jmnext
/usr/share/icons/hicolor/256x256/apps/jmnext.png
SPEC
sed -i "s/@V@/$V/" "$TOP/SPECS/jmnext.spec"
# rpm：不要静音构建输出 —— 静音过一次，失败时看不到原因，白跑一轮。
# 也**不要让它中断脚本**：rpm 失败不该连累后面 AppImage 的产出。
# aarch64 的 rpmbuild：装 rpm:amd64 时系统那份被换成 x86_64 了，故这里用私有解包的 arm64 版
export LD_LIBRARY_PATH=/opt/rpm-arm64/usr/lib/aarch64-linux-gnu:/opt/rpm-arm64/lib/aarch64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}
RPMBUILD_AARCH64=/opt/rpm-arm64/usr/bin/rpmbuild
if "$RPMBUILD_AARCH64" -bb --define "_topdir $TOP" --define "_target_cpu aarch64" "$TOP/SPECS/jmnext.spec"; then
  find "$TOP/RPMS" -name "*.rpm" -exec cp {} "$OUT/Linux-aarch64-$V.rpm" \;
  echo "  rpm 成功"
else
  echo "  rpm 失败：详情见上面 rpmbuild 的输出（已记档，不影响其它产物）"
fi

echo "== 6/6 AppImage =="
if [ -f /root/runtime-aarch64 ]; then
  APPDIR=$(mktemp -d)/AppDir
  mkdir -p "$APPDIR/usr"
  cp -a "$APP/." "$APPDIR/usr/"
  printf '#!/bin/sh\nexec "$(dirname "$0")/usr/bin/jmnext" "$@"\n' > "$APPDIR/AppRun"
  chmod 755 "$APPDIR/AppRun"
  cp "$ICON" "$APPDIR/jmnext.png"
  printf '[Desktop Entry]\nType=Application\nName=JMNeXt\nExec=jmnext\nIcon=jmnext\nTerminal=false\nCategories=Utility;Graphics;\n' \
    > "$APPDIR/jmnext.desktop"
  SQ=$(mktemp -u).squashfs
  mksquashfs "$APPDIR" "$SQ" -root-owned -noappend -comp gzip -quiet
  cat /root/runtime-aarch64 "$SQ" > "$OUT/Linux-aarch64-$V.AppImage"
  chmod 755 "$OUT/Linux-aarch64-$V.AppImage"
  rm -f "$SQ"
else
  echo "  跳过：缺少 AppImage runtime（/root/runtime-aarch64）"
fi

echo
echo "== 成品 =="
ls -lh "$OUT" | tail -6

gate_commit
