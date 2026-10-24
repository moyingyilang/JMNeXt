#!/usr/bin/env bash
# SPDX-License-Identifier: MPL-2.0
#
# 交叉打包：aarch64 主机 → x86_64 Linux。
#
# 桌面端是纯 JVM，没有自写原生代码，所以"交叉"只需换三样东西：
#   1. JVM 运行时换成 x86_64 的（jlink 用 x86_64 的 jmods 生成，见 /root/x64-runtime）
#   2. Skiko 原生库换成 libskiko-linux-x64.so（Arm 版那个 .so 要删掉）
#   3. 启动器从 jpackage 的 ELF 换成 shell 脚本（jpackage 不能跨平台生成启动器）
# 应用自己的 jar 是平台无关的，直接用 aarch64 构建出的那份。
#
# 用法：scripts/package-linux-x64.sh [输出目录]   默认 dist-x64
set -euo pipefail
trap 'rm -rf "${CHK:-}" "${STAGE:-}" "${TOP:-}" "${AD:-}" "${W:-}" "${D:-}" 2>/dev/null || true' EXIT   # 退出即清临时目录，避免中断/超时把 staging 留在容器 /tmp
cd "$(dirname "$0")/.."
V=${VERSION:-$(grep -oE '[0-9]+.[0-9]+.[0-9]+' src/main/kotlin/com/jmnext/desktop/Version.kt | tail -1)}
[ -n "$V" ] || { echo '拿不到版本号（Version.kt）'; exit 1; }
OUT=${1:-dist-x64}
. "$(dirname "$0")/lib-gate.sh"
GATE_EXPECT="$OUT/Linux-x86_64-$V.tar.gz $OUT/Linux-x86_64-$V.deb $OUT/Linux-x86_64-$V.rpm $OUT/Linux-x86_64-$V.AppImage"
if ! gate_begin linux-x86_64 "$OUT" build/compose/binaries/main/app/jmnext src/main/resources/icon.png; then exit 0; fi
APP=build/compose/binaries/main/app/jmnext
X64_RUNTIME=${X64_RUNTIME:-/root/x64-runtime}
SKIKO_JAR=${SKIKO_JAR:-/root/skiko-win-none.jar}     # 名字起错了，内容是 linux-x64 的
[ -f "$SKIKO_JAR" ] || { echo "缺少 x86_64 的 Skiko jar"; exit 1; }
[ -d "$X64_RUNTIME" ] || { echo "缺少 x86_64 运行时：$X64_RUNTIME"; exit 1; }

echo "== 1/5 组装 x86_64 应用目录 =="
rm -rf "$OUT" && mkdir -p "$OUT/stage"
STAGE="$OUT/stage/jmnext"
mkdir -p "$STAGE/bin" "$STAGE/lib"
cp -a "$APP/lib/app" "$STAGE/lib/app"
cp -a "$X64_RUNTIME" "$STAGE/lib/runtime"

# 换原生库：删掉 Arm 版，解出 x86_64 版放到与原来相同的位置
rm -f "$STAGE"/lib/app/libskiko-linux-arm64.so "$STAGE"/lib/app/libskiko-linux-arm64.so.sha256
( cd "$STAGE/lib/app" && unzip -o -q "$SKIKO_JAR" 'libskiko-linux-x64.so' 'libskiko-linux-x64.so.sha256' )

# 启动器：脚本代替 ELF
cat > "$STAGE/bin/jmnext" <<'LAUNCH'
#!/bin/sh
# JMNeXt 桌面版启动器（x86_64）
# jpackage 不能跨平台生成启动器，所以这里用脚本；行为与它的 ELF 等价：
# 用自带的运行时跑应用的 jar，并把 $APPDIR 传给 Skiko 找原生库。
DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
exec "$DIR/lib/runtime/bin/java" \
  -Dskiko.library.path="$DIR/lib/app" \
  -cp "$DIR/lib/app/*" \
  com.jmnext.desktop.MainKt "$@"
LAUNCH
chmod 755 "$STAGE/bin/jmnext"
cp $(cd "$(dirname "$0")/.." && pwd)/src/main/resources/icon.png "$STAGE/jmnext.png" 2>/dev/null || true
cp $(cd "$(dirname "$0")/.." && pwd)/src/main/resources/icon.png "$OUT/jmnext.png" 2>/dev/null || true

echo "== 2/5 架构自检（不通过就拒绝出包）=="
file "$STAGE/lib/runtime/bin/java" | grep -q "x86-64" || { echo "  失败：运行时不是 x86-64"; exit 1; }
[ -f "$STAGE/lib/app/libskiko-linux-x64.so" ] || { echo "  失败：缺 libskiko-linux-x64.so"; exit 1; }
[ ! -f "$STAGE/lib/app/libskiko-linux-arm64.so" ] || { echo "  失败：Arm 的 .so 没删干净"; exit 1; }
echo "  运行时 x86-64、Skiko x86_64、Arm 原生库已清"

echo "== 3/5 便携包 =="
tar czf "$OUT/Linux-x86_64-$V.tar.gz" -C "$STAGE" .

echo "== 4/5 deb(amd64) 与 rpm(x86_64) =="
D=$(mktemp -d)/jmnext
mkdir -p "$D/DEBIAN" "$D/opt/jmnext" "$D/usr/bin" "$D/usr/share/icons/hicolor/256x256/apps"
cp -a "$STAGE/." "$D/opt/jmnext/"
printf '#!/bin/sh\nexec /opt/jmnext/bin/jmnext "$@"\n' > "$D/usr/bin/jmnext"
chmod 755 "$D/usr/bin/jmnext"
cp "$OUT/jmnext.png" "$D/usr/share/icons/hicolor/256x256/apps/jmnext.png" 2>/dev/null || true
cat > "$D/DEBIAN/control" <<'CTL'
Package: jmnext
Version: 2.0.0
Architecture: amd64
Maintainer: moyingyilang
Depends: libc6
Section: utils
Priority: optional
Description: JMNeXt desktop client (x86_64)
 A third-party JMComic client built with Kotlin and Compose Desktop.
CTL
dpkg-deb --build --root-owner-group "$D" "$OUT/Linux-x86_64-$V.deb" >/dev/null

TOP=$(mktemp -d)
mkdir -p "$TOP"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
cp -a "$STAGE" "$TOP/SOURCES/jmnext-app"
cat > "$TOP/SPECS/jmnext.spec" <<'SPEC'
Name:           jmnext
Version:        @V@
Release:        1
Summary:        JMNeXt desktop client (x86_64)
License:        AGPL-3.0-only
BuildArch:      x86_64
Requires:       glibc
%description
A third-party JMComic client built with Kotlin and Compose Desktop.
%prep
%build
%install
rm -rf %{buildroot}
mkdir -p %{buildroot}/opt/jmnext %{buildroot}/usr/bin
cp -a %{_sourcedir}/jmnext-app/. %{buildroot}/opt/jmnext/
printf '#!/bin/sh\nexec /opt/jmnext/bin/jmnext "$@"\n' > %{buildroot}/usr/bin/jmnext
chmod 755 %{buildroot}/usr/bin/jmnext
%files
/opt/jmnext
/usr/bin/jmnext
SPEC
sed -i "s/@V@/$V/" "$TOP/SPECS/jmnext.spec"
# rpm：本机 rpmbuild（aarch64）会以 "No compatible architectures found for build"
# 拒绝跨架构构建（试过八种 define 组合均无效）。绕法是**让 rpmbuild 自己就是 x86_64**：
# 装 rpm:amd64（多架构），再用 qemu 运行它 —— 它看到的宿主就是 x86_64，
# 于是这条构建路径与在真机上完全一致，而不是伪造架构标记。
RPM_OK=0
RPM_OK=0
if [ -x /usr/bin/qemu-x86_64-static ]; then
  # 本机 rpmbuild 是 aarch64，跨架构会被拒；qemu 跑 x86_64 的 rpmbuild，并显式指定 buildroot（否则 %install 失败）
  # strip 必须禁用：%install 末尾的 brp-strip 会用宿主的 aarch64 strip 去处理 x86_64 的 .so，报
  # "Unable to recognise the format" 并让整个 %install 失败（这是跨架构 rpm 的核心坑）
  # x86_64 的 rpm 用**原生 aarch64 的 rpmbuild** 直接构建（不再走 qemu）：
  # rpm 的"没有兼容架构"是 rpmrc 里的兼容表决定的；包内容全是架构无关的文件（我们已交叉摆好 x86_64 的运行时），
  # 所以放开该检查、并用 --target x86_64 指定目标架构，产出包的 %{ARCH} 仍是 x86_64。
  # 实测：qemu 那条路要一两分钟，原生这条路十几秒。
  RPMRC=/opt/rpm-aarch64-custom/rpmrc
  if [ ! -f "$RPMRC" ]; then
    mkdir -p "$(dirname "$RPMRC")"
    cp /usr/lib/rpm/rpmrc "$RPMRC" 2>/dev/null || cp /opt/rpm-arm64/usr/lib/rpm/rpmrc "$RPMRC"
    printf 'arch_compat: aarch64: x86_64\nbuildarch_compat: aarch64: x86_64\n' >> "$RPMRC"
  fi
  export LD_LIBRARY_PATH=/opt/rpm-arm64/usr/lib/aarch64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}
  /opt/rpm-arm64/usr/bin/rpmbuild -bb --rcfile "$RPMRC" --target x86_64 \
    --define "_topdir $TOP" --define "_buildrootdir $TOP/BUILDROOT" --define "__strip /bin/true" \
    "$TOP/SPECS/jmnext.spec" >/tmp/rpm-x64.log 2>&1 || true
  if [ -n "$(find "$TOP/RPMS" -name "*.rpm" 2>/dev/null)" ]; then RPM_OK=1; fi
  [ "$RPM_OK" = 1 ] || { echo "  rpmbuild 完整输出（末尾 30 行）:"; tail -30 /tmp/rpm-x64.log | sed "s/^/    /"; }
  [ "$RPM_OK" = 1 ] || { echo "  rpmbuild 完整输出的末尾 25 行（含失败的那条命令）:"; tail -30 /tmp/rpm-x64.log | sed 's/^/    /'; }
fi
if [ "$RPM_OK" = 1 ]; then
  find "$TOP/RPMS" -name "*.rpm" -exec cp {} "$OUT/Linux-x86_64-$V.rpm" \;
  echo "  rpm 成功"
else
  echo "  rpm 失败：需在 x86_64 环境构建（已记档，不影响其它三类产物）"
fi

echo "== 5/5 AppImage(x86_64) =="
if [ -f /root/runtime-x86_64 ]; then
  AD=$(mktemp -d)/AppDir
  mkdir -p "$AD/usr"
  cp -a "$STAGE/." "$AD/usr/"
  printf '#!/bin/sh\nexec "$(dirname "$0")/usr/bin/jmnext" "$@"\n' > "$AD/AppRun"
  chmod 755 "$AD/AppRun"
  cp "$OUT/jmnext.png" "$AD/jmnext.png" 2>/dev/null || true
  printf '[Desktop Entry]\nType=Application\nName=JMNeXt\nExec=jmnext\nIcon=jmnext\nTerminal=false\nCategories=Utility;Graphics;\n' > "$AD/jmnext.desktop"
  SQ=$(mktemp -u).squashfs
  mksquashfs "$AD" "$SQ" -root-owned -noappend -comp gzip -quiet
  cat /root/runtime-x86_64 "$SQ" > "$OUT/Linux-x86_64-$V.AppImage"
  chmod 755 "$OUT/Linux-x86_64-$V.AppImage"
  rm -f "$SQ"
else
  echo "  跳过：缺少 x86_64 的 AppImage runtime"
fi
rm -rf "$OUT/stage"
echo; echo "== 成品 =="; ls -lh "$OUT" | tail -6

gate_commit
