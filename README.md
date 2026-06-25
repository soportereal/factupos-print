# FactuPOS Apps (Windows)

Monorepo de las apps de escritorio de FactuPOS para Windows. Cada app se compila
y empaqueta sola con GitHub Actions (runner `windows-latest`).

## Estructura

```
print/        Cliente de impresion (Python). Workflow: print.yml
              -> instaladores Factupos-Print-x64.exe / -x86.exe (Inno Setup)
bridge/       Print Bridge HTTP/Bluetooth COM (Python). Workflow: bridge.yml
              -> instaladores Factupos-Bridge-x64.exe / -x86.exe
fingerprint/  Biometria / Huella Digital (C#/.NET, binarios prebuilt). Workflow: fingerprint.yml
              -> instalador Factupos-Fingerprint-x64.exe (empaqueta el ZIP publicado)
.github/workflows/   print.yml, bridge.yml, fingerprint.yml
```

Cada app tiene su `installer/<app>.iss` (Inno Setup). Los instaladores configuran
autostart oculto + permisos (icacls) para que el auto-update funcione sin UAC.

## Publicacion

Los binarios finales se publican (manual) en
`soportereal.com/software/factupos-app/windows/`. Los manifests de auto-update
viven en `factupos.com/downloads/` (`print_client_version.json`,
`bridge_windows_version.json`).

> El fingerprint NO se compila aqui (es C#/.NET con SDK propietario): el workflow
> baja el ZIP ya compilado y solo lo empaqueta con Inno Setup.

## Cambios recientes

### print/ — formato de factura FIPVIVI005 (2026-06-24)
- **Numeracion de paginas**: cada hoja muestra "Pagina X de Y" al pie (`NumberedCanvas`).
- **Orden Codigo / Cabys**: en el detalle de cada linea va primero el Codigo y luego el Cabys.
- **Letra mas grande** en las lineas de detalle para mejor lectura.
- **"Recibido Conforme"** (firma + texto legal + ORIGINAL) ya no se parte entre hojas
  (`KeepTogether`): si no cabe, pasa completo a la siguiente pagina.
