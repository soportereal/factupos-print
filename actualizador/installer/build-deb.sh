#!/bin/bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"; PKG="factupos-actualizador"
VERSION="${1:-1.0.0}"; OUT="${2:-$HERE/installer/Output}"
STAGE="$(mktemp -d)"; trap 'rm -rf "$STAGE"' EXIT
install -d "$STAGE/usr/lib/$PKG"
install -m755 "$HERE/$PKG.sh" "$STAGE/usr/lib/$PKG/$PKG.sh"
install -d "$STAGE/lib/systemd/system"
install -m644 "$HERE/$PKG.service" "$STAGE/lib/systemd/system/$PKG.service"
install -m644 "$HERE/$PKG.timer" "$STAGE/lib/systemd/system/$PKG.timer"
install -d "$STAGE/DEBIAN"
sed "s/__VERSION__/$VERSION/" "$HERE/installer/debian/control" > "$STAGE/DEBIAN/control"
install -m755 "$HERE/installer/debian/postinst" "$STAGE/DEBIAN/postinst"
chmod 755 "$STAGE"; mkdir -p "$OUT"
dpkg-deb --build --root-owner-group "$STAGE" "$OUT/${PKG}_${VERSION}_all.deb" >/dev/null
echo "OK -> $OUT/${PKG}_${VERSION}_all.deb"
