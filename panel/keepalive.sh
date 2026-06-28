#!/bin/sh
# FactuPOS Panel keepalive: si el panel se cae, lo relanza (un POS no debe quedar
# sin barra). Backoff anti crash-loop: tras varios cierres rápidos, espera más.
n=0; t0=$(date +%s)
while true; do
    /usr/bin/factupos-panel --monitor all
    now=$(date +%s); [ $((now - t0)) -gt 30 ] && { n=0; t0=$now; }; n=$((n+1))
    [ "$n" -ge 5 ] && sleep 10 || sleep 2
done
