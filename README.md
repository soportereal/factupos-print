# FactuPOS Print Client

Cliente de impresión multiplataforma de FactuPOS. Este repo compila el **.exe de Windows**
automáticamente con GitHub Actions (runner `windows-latest` + PyInstaller).

- Fuente: `factupos_print_client.py`
- El `.exe` se genera en cada push a `main` (o manual desde la pestaña **Actions** → **Run workflow**)
  y queda como **artifact** `FactuPOS_Print-exe`.

La versión Linux (.deb) se construye aparte.
