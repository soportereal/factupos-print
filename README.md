# FactuPOS Apps

Monorepo **único** de las apps de escritorio y móviles de FactuPOS
(Windows / Linux / Android). GitHub: `soportereal/factupos-apps`.

> **Regla de oro:** este repo es la **única** fuente del código de las apps.
> No editar copias sueltas. Las viejas pre-monorepo quedaron archivadas en
> `factupos.local/apps/_legacy/` (no usar).

## Estructura (una carpeta por app)

```
print/         Cliente de impresión (Python)            · Win + Linux · print.yml
bridge/        Print Bridge HTTP/Bluetooth COM (Python)  · Win + Linux · bridge.yml
backup/        Respaldo de BD SQL Server (Python 3.11)   · Win         · backup.yml
ia/            Soporte remoto con IA (Python/GTK3)       · Win + Linux · ia.yml
panel/         Barra de tareas FactuPOS OS (Python/GTK3) · Linux       · panel.yml
printer-inst/  Asistente de impresoras USB/Serial (Py)   · Linux       · printer-inst.yml
actualizador/  Servicio de auto-actualización (systemd)  · Linux       · (build-deb.sh)
dataequipo/    Info del equipo (Python)                  · Linux       · (build-deb.sh)
fingerprint/   Huella / biometría                        · Win         · fingerprint.yml
   ├── windows/   código C#/.NET (DigitalPersona SDK) — fuente del servicio
   └── installer/ Inno Setup (empaqueta el ZIP de binarios ya compilados)
biometria-linux/ Huella en kiosko (Java)                 · Linux       · (build-deb.sh / packaging)
bridge-android/ Print Bridge Sunmi (Android, Gradle)     · Android     · android.yml
sms-android/   Envío de SMS (Android, Gradle)            · Android     · android.yml

.github/workflows/  print · bridge · backup · ia · fingerprint · panel · printer-inst · android
```

Android: `android.yml` compila ambos APK con `assembleDebug` (firma debug,
instalable por sideload). La keystore release (`.jks`) está fuera del repo.

> El **bridge Android (Sunmi)** y el **SoporteRemoto (RustDesk SRL)** todavía viven
> en carpetas aparte (`factupos.local/apps/factupos-bridge-android`,
> `factupos.local/apps/rustdesk-srl`); el **Datáfono** está en su propio repo
> (`factupos-datafono-gh`, montado en `factupos.com/datafono/`).

Cada app tiene su `installer/<app>.iss` (Inno, Windows) o `installer/build-deb.sh`
(Linux). Los instaladores configuran autostart oculto + permisos (icacls) para que
el auto-update funcione sin UAC.

## Compilación

- **Windows:** GitHub Actions (`windows-latest`) compila con PyInstaller+GTK (MSYS2)
  e Inno Setup. Descargar los artefactos con `gh run download`.
  - Excepción **fingerprint**: NO se compila en CI (es C#/.NET con SDK propietario);
    el workflow baja el ZIP ya compilado y solo lo empaqueta con Inno.
- **Linux:** `bash <app>/installer/build-deb.sh <version>` → `installer/Output/*.deb`.
- **Android (sms-android):** `./gradlew assembleRelease` → `app/build/outputs/apk/`.

## Publicación (manual)

Los binarios finales se copian a la carpeta pública de descargas, **organizada por
plataforma**:

```
soportereal.com/software/factupos-app/
├── windows/   *.exe  (instaladores + el _Windows.exe del auto-update)
├── linux/     *.deb  + el .py crudo (auto-update Linux) + *_version.json
└── android/   *.apk
```

Esa carpeta se navega/descarga desde
**https://soportereal.com/software/index.php?d=factupos-app**
(la página lista sola lo que haya en cada subcarpeta — no hay que tocar HTML).

Los **manifests de auto-update** viven:
- Windows → `factupos.com/downloads/*_version.json`
  (`print_client_version.json`, `bridge_windows_version.json`,
  `factupos_backup_version.json`, `factupos_ia_version.json`, …)
- Linux → junto al binario, dentro de `.../linux/*_version.json`.

## Versionado

La versión es única por app: la constante `VERSION` en su fuente principal.
Subir versión = editar esa constante **y** el/los manifest(s) al publicar
(el server WS / el auto-update anuncian la versión leyendo el manifest).
