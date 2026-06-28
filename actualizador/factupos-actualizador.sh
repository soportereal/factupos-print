#!/bin/sh
# FactuPOS actualizador: mantiene TODAS las apps FactuPOS al dia desde invefacon
# (failover invefacon.net -> invefacon.com). Instala las que falten. Corre como root.
BASES="https://soportereal.com/software/factupos-app/linux https://invefacon.net/software"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
BASE=""
for b in $BASES; do
    if curl -fsS --max-time 15 "$b/versiones.txt" -o "$TMP/versiones.txt" 2>/dev/null; then
        BASE="$b"; break
    fi
done
[ -z "$BASE" ] && { echo "actualizador: sin conexion a invefacon"; exit 0; }
echo "actualizador: usando $BASE"
python3 - "$TMP/versiones.txt" "$BASE" "$TMP" <<'PY'
import sys, json, subprocess, urllib.request, os
mf, base, tmp = sys.argv[1], sys.argv[2], sys.argv[3]
data = json.load(open(mf))
def cur(pkg):
    return subprocess.run(["dpkg-query","-Wf","${Version}",pkg],capture_output=True,text=True).stdout.strip()
def vt(v):
    try: return tuple(int(x) for x in v.split("."))
    except: return (0,)
for name, info in data.items():
    pkg, ver, deb = info["paquete"], info["version"], info["deb"]
    c = cur(pkg)
    if c and vt(c) >= vt(ver):
        print("  al dia:", pkg, c); continue
    p = os.path.join(tmp, deb)
    try:
        urllib.request.urlretrieve(base+"/"+deb, p)
        subprocess.run(["apt-get","install","-y","--allow-downgrades",p], check=False)
        print("  instalado/actualizado:", pkg, "->", ver)
    except Exception as e:
        print("  error", pkg, e)
PY
