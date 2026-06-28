package com.factupos.printbridge;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.ScrollView;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Set;

/**
 * FactuPOS Print Bridge - MainActivity
 *
 * Muestra el estado del servicio HTTP, la impresora activa,
 * y permite seleccionar entre SUNMI interna y impresoras BT pareadas.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PrintBridge";
    private static final int NOTIFICATION_PERMISSION_CODE = 100;
    private static final int BLUETOOTH_PERMISSION_CODE = 101;

    private TextView txtEstado;
    private TextView txtImpresora;
    private TextView txtPuerto;
    private Button btnIniciar;
    private Button btnDetener;
    private Button btnPrueba;
    private Button btnLimpiarLog;
    private RadioGroup radioGroupPrinters;
    private TextView txtPrinterLabel;
    private TextView txtVersion;
    private TextView txtPrintLog;
    private ScrollView scrollLog;
    private final Handler logHandler = new Handler(Looper.getMainLooper());
    private final Runnable logRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (txtPrintLog != null) {
                String log = PrintService.logRender();
                if (!log.equals(txtPrintLog.getText().toString())) {
                    txtPrintLog.setText(log);
                    if (scrollLog != null) {
                        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
                    }
                }
            }
            logHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtEstado = findViewById(R.id.txtEstado);
        txtImpresora = findViewById(R.id.txtImpresora);
        txtPuerto = findViewById(R.id.txtPuerto);
        btnIniciar = findViewById(R.id.btnIniciar);
        btnDetener = findViewById(R.id.btnDetener);
        btnPrueba = findViewById(R.id.btnPrueba);
        radioGroupPrinters = findViewById(R.id.radioGroupPrinters);
        txtPrinterLabel = findViewById(R.id.txtPrinterLabel);
        txtVersion = findViewById(R.id.txtVersion);
        txtPrintLog = findViewById(R.id.txtPrintLog);
        scrollLog = findViewById(R.id.scrollLog);
        btnLimpiarLog = findViewById(R.id.btnLimpiarLog);

        // Mostrar versión y fecha de compilación
        txtVersion.setText("v" + BuildConfig.VERSION_NAME + " | " + BuildConfig.BUILD_DATE);

        btnIniciar.setOnClickListener(v -> iniciarServicio());
        btnDetener.setOnClickListener(v -> detenerServicio());
        btnPrueba.setOnClickListener(v -> imprimirPrueba());
        if (btnLimpiarLog != null) {
            btnLimpiarLog.setOnClickListener(v -> {
                PrintService.logClear();
                if (txtPrintLog != null) txtPrintLog.setText("(log limpiado)\n");
            });
        }

        // Pedir permisos
        solicitarPermisos();

        // Pedir whitelist de optimizacion de bateria (CRITICO en Xiaomi/MIUI/Huawei
        // para que el foreground service no sea matado y el puerto 8765 quede vivo)
        solicitarWhitelistBateria();

        // Auto-iniciar el servicio
        iniciarServicio();

        // Arrancar refresh del log
        logHandler.post(logRefreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        logHandler.removeCallbacks(logRefreshRunnable);
    }

    /**
     * Solicita al usuario que la app sea ignorada del battery optimization.
     * Sin esto, Android (sobre todo MIUI/EMUI/OneUI) mata el foreground service
     * tras unos minutos y el puerto 8765 deja de responder.
     */
    private void solicitarWhitelistBateria() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        String pkg = getPackageName();
        if (pm.isIgnoringBatteryOptimizations(pkg)) return; // ya esta whitelist
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + pkg));
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo pedir whitelist de bateria", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        actualizarUI();
        cargarImpresoras();
        // Re-arrancar refresh del log
        logHandler.removeCallbacks(logRefreshRunnable);
        logHandler.post(logRefreshRunnable);
    }

    private void solicitarPermisos() {
        // Notificaciones Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }

        // Bluetooth Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] btPermisos = {
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            };
            boolean necesita = false;
            for (String perm : btPermisos) {
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    necesita = true;
                    break;
                }
            }
            if (necesita) {
                ActivityCompat.requestPermissions(this, btPermisos, BLUETOOTH_PERMISSION_CODE);
            }
        }

        // Location (necesario para BLE scan)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 102);
        }
    }

    private void iniciarServicio() {
        Intent intent = new Intent(this, PrintService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        // Esperar un momento para que el servicio inicie
        txtEstado.postDelayed(this::actualizarUI, 500);
    }

    private void detenerServicio() {
        Intent intent = new Intent(this, PrintService.class);
        stopService(intent);
        actualizarUI();
    }

    private void actualizarUI() {
        boolean activo = PrintService.isRunning();

        txtEstado.setText(activo ? "Activo" : "Detenido");
        txtEstado.setTextColor(activo ? 0xFF22C55E : 0xFFEF4444);

        txtPuerto.setText(activo ? "localhost:8765" : "-");

        // Mostrar impresora activa
        PrinterManager pm = PrintService.getStaticPrinterManager();
        if (pm != null) {
            String activeName = pm.getActiveName();
            String activeType = pm.getActiveType();
            txtImpresora.setText(activeName);
            if ("sunmi".equals(activeType)) {
                txtImpresora.setTextColor(0xFF16A34A);
            } else if ("bluetooth".equals(activeType)) {
                txtImpresora.setTextColor(0xFF0284C7);
            } else {
                txtImpresora.setTextColor(0xFF64748B);
            }
        } else {
            String modelo = Build.MODEL;
            boolean esSunmi = modelo.toLowerCase().contains("sunmi") ||
                              Build.MANUFACTURER.toLowerCase().contains("sunmi");
            txtImpresora.setText(esSunmi ? "SUNMI " + modelo : modelo);
            txtImpresora.setTextColor(0xFF1E293B);
        }

        btnIniciar.setEnabled(!activo);
        btnDetener.setEnabled(activo);
        btnPrueba.setEnabled(activo);
    }

    /**
     * Cargar lista de impresoras disponibles en RadioGroup
     */
    private void cargarImpresoras() {
        radioGroupPrinters.removeAllViews();

        // Obtener tipo y address activos desde SharedPreferences
        SharedPreferences prefs = getSharedPreferences("printer_prefs", MODE_PRIVATE);
        String activeType = prefs.getString("active_type", "");
        String activeAddress = prefs.getString("active_address", "");

        boolean esSunmi = Build.MODEL.toLowerCase().contains("sunmi") ||
                          Build.MANUFACTURER.toLowerCase().contains("sunmi");

        // Si no hay selección y es SUNMI, default a sunmi
        if (activeType.isEmpty() && esSunmi) {
            activeType = "sunmi";
        }

        int radioId = 1000;

        // Opción SUNMI Interna (si es dispositivo SUNMI)
        if (esSunmi) {
            RadioButton rbSunmi = crearRadioButton("SUNMI Interna", radioId);
            rbSunmi.setTag("sunmi|");
            if ("sunmi".equals(activeType)) {
                rbSunmi.setChecked(true);
            }
            final RadioButton rbSunmiRef = rbSunmi;
            rbSunmi.setOnClickListener(v -> {
                uncheckRadiosHermanos(rbSunmiRef);
                rbSunmiRef.setChecked(true);
                guardarSeleccionImpresora("sunmi", "", "SUNMI Interna");
            });
            radioGroupPrinters.addView(rbSunmi);
            radioId++;
        }

        // Dispositivos BT pareados
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null && btAdapter.isEnabled()) {
            try {
                Set<BluetoothDevice> paired = btAdapter.getBondedDevices();
                if (paired != null) {
                    for (BluetoothDevice device : paired) {
                        final String nombre = device.getName() != null ? device.getName() : "Desconocido";
                        final String addr = device.getAddress();
                        final String proto = prefs.getString("protocol_" + addr.toUpperCase(), "auto");

                        // Fila horizontal: RadioButton (peso 1) + Spinner del protocolo
                        LinearLayout fila = new LinearLayout(this);
                        fila.setOrientation(LinearLayout.HORIZONTAL);
                        fila.setGravity(Gravity.CENTER_VERTICAL);
                        fila.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));

                        RadioButton rb = crearRadioButton(nombre + "  (" + addr + ")", radioId);
                        rb.setTag("bluetooth|" + addr);
                        if ("bluetooth".equals(activeType) && addr.equalsIgnoreCase(activeAddress)) {
                            rb.setChecked(true);
                        }
                        rb.setLayoutParams(new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                        final RadioButton rbRef = rb;
                        rb.setOnClickListener(v -> {
                            // Manual uncheck de hermanos (RadioGroup no maneja wrapped)
                            uncheckRadiosHermanos(rbRef);
                            rbRef.setChecked(true);
                            guardarSeleccionImpresora("bluetooth", addr, nombre);
                        });

                        // Spinner con las 4 opciones de protocolo
                        Spinner spnProto = new Spinner(this);
                        final String[] labels = {"Auto", "ESC/POS", "CPCL", "ZPL"};
                        final String[] vals = {"auto", "escpos", "cpcl", "zpl"};
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, labels);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spnProto.setAdapter(adapter);
                        int idx = 0;
                        for (int i = 0; i < vals.length; i++) if (vals[i].equals(proto)) { idx = i; break; }
                        spnProto.setSelection(idx, false);
                        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                        slp.setMarginStart(8);
                        spnProto.setLayoutParams(slp);
                        spnProto.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                String nuevo = vals[position];
                                SharedPreferences.Editor ed = getSharedPreferences("printer_prefs", MODE_PRIVATE).edit();
                                ed.putString("protocol_" + addr.toUpperCase(), nuevo);
                                ed.apply();
                                Log.i(TAG, "Protocolo " + nombre + " -> " + nuevo);
                            }
                            @Override
                            public void onNothingSelected(AdapterView<?> parent) { }
                        });

                        fila.addView(rb);
                        fila.addView(spnProto);
                        radioGroupPrinters.addView(fila);
                        radioId++;
                    }
                }
            } catch (SecurityException e) {
                Log.w(TAG, "Sin permiso BT para listar", e);
                TextView tvErr = new TextView(this);
                tvErr.setText("Sin permiso Bluetooth");
                tvErr.setTextColor(0xFFEF4444);
                tvErr.setTextSize(12);
                tvErr.setPadding(0, 8, 0, 8);
                radioGroupPrinters.addView(tvErr);
            }
        } else {
            if (!esSunmi) {
                TextView tvNone = new TextView(this);
                tvNone.setText("Bluetooth apagado o no disponible");
                tvNone.setTextColor(0xFF94A3B8);
                tvNone.setTextSize(12);
                tvNone.setPadding(0, 8, 0, 8);
                radioGroupPrinters.addView(tvNone);
            }
        }

        // Actualizar label (cuenta entradas tanto SUNMI directas como filas wrapeadas)
        int total = radioGroupPrinters.getChildCount();
        txtPrinterLabel.setText("Impresoras disponibles (" + total + ")");
    }

    /**
     * Desmarca todos los RadioButtons hermanos (en el RadioGroup o anidados en
     * filas LinearLayout) excepto el que se pasa como excepción.
     */
    private void uncheckRadiosHermanos(RadioButton mantener) {
        for (int i = 0; i < radioGroupPrinters.getChildCount(); i++) {
            View child = radioGroupPrinters.getChildAt(i);
            if (child instanceof RadioButton && child != mantener) {
                ((RadioButton) child).setChecked(false);
            } else if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View grandchild = row.getChildAt(j);
                    if (grandchild instanceof RadioButton && grandchild != mantener) {
                        ((RadioButton) grandchild).setChecked(false);
                    }
                }
            }
        }
    }

    /**
     * Persiste la impresora activa y notifica al PrinterManager (si el servicio corre).
     */
    private void guardarSeleccionImpresora(String type, String address, String labelToast) {
        SharedPreferences.Editor editor = getSharedPreferences("printer_prefs", MODE_PRIVATE).edit();
        editor.putString("active_type", type);
        editor.putString("active_address", address);
        editor.apply();

        PrinterManager pm = PrintService.getStaticPrinterManager();
        if (pm != null) pm.setActive(type, address);

        actualizarUI();
        Toast.makeText(this, "Impresora: " + labelToast, Toast.LENGTH_SHORT).show();
    }

    /**
     * Diálogo para configurar el protocolo de una impresora BT.
     * El protocolo determina cómo el APK convierte el texto entrante:
     *   - Auto: detecta por nombre (ZEBRA, MZ, IMZ, ZQ, ZD -> CPCL; resto -> ESC/POS)
     *   - ESC/POS: termicas chinas/Epson/etc.
     *   - CPCL: Zebra mobile (MZ320, ZQ, iMZ)
     *   - ZPL: Zebra escritorio (ZD, ZT, ZP)
     */
    private void mostrarDialogoProtocolo(String address, String nombre) {
        SharedPreferences prefs = getSharedPreferences("printer_prefs", MODE_PRIVATE);
        String actual = prefs.getString("protocol_" + address.toUpperCase(), "auto");

        final String[] opciones = {"Auto (detectar por nombre)", "ESC/POS", "CPCL (Zebra mobile)", "ZPL (Zebra escritorio)"};
        final String[] valores  = {"auto", "escpos", "cpcl", "zpl"};

        int idxActual = 0;
        for (int i = 0; i < valores.length; i++) {
            if (valores[i].equals(actual)) { idxActual = i; break; }
        }

        new AlertDialog.Builder(this)
            .setTitle("Protocolo para " + nombre)
            .setSingleChoiceItems(opciones, idxActual, (dialog, which) -> {
                String nuevo = valores[which];
                SharedPreferences.Editor ed = getSharedPreferences("printer_prefs", MODE_PRIVATE).edit();
                ed.putString("protocol_" + address.toUpperCase(), nuevo);
                ed.apply();
                Toast.makeText(this, "Protocolo: " + opciones[which], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                cargarImpresoras(); // refresca etiqueta [PROTO]
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /**
     * Crear RadioButton con estilo consistente
     */
    private RadioButton crearRadioButton(String text, int id) {
        RadioButton rb = new RadioButton(this);
        rb.setId(id);
        rb.setText(text);
        rb.setTextSize(14);
        rb.setTextColor(0xFF1E293B);
        rb.setPadding(8, 12, 8, 12);
        return rb;
    }

    /**
     * Imprimir tiquete de prueba
     */
    private void imprimirPrueba() {
        PrinterManager pm = PrintService.getStaticPrinterManager();
        if (pm == null) {
            Toast.makeText(this, "Servicio no iniciado", Toast.LENGTH_SHORT).show();
            return;
        }

        String prueba = "================================\n"
                       + "   FACTUPOS PRINT BRIDGE v" + BuildConfig.VERSION_NAME + "\n"
                       + "       Prueba de impresion\n"
                       + "================================\n"
                       + "Impresora: " + pm.getActiveName() + "\n"
                       + "Tipo: " + pm.getActiveType() + "\n"
                       + "Modelo: " + Build.MODEL + "\n"
                       + "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                       + "--------------------------------\n"
                       + "Si puede leer esto, la impresora\n"
                       + "esta funcionando correctamente.\n"
                       + "================================\n\n\n";

        btnPrueba.setEnabled(false);
        btnPrueba.setText("Imprimiendo...");

        // Ejecutar en hilo separado (BT no puede correr en UI thread)
        new Thread(() -> {
            boolean ok = pm.printText(prueba);
            String errorDetail = "";
            if (!ok) {
                try {
                    String sppErr = pm.getBluetoothPrinter().getLastError();
                    String bleErr = pm.getBlePrinter().getLastError();
                    errorDetail = "SPP:" + (sppErr.isEmpty() ? "?" : sppErr);
                    if (!bleErr.isEmpty()) errorDetail += " | BLE:" + bleErr;
                } catch (Exception ignored) {}
            }
            final String msg = ok
                ? "Prueba enviada a " + pm.getActiveName()
                : "ERROR: " + (errorDetail.isEmpty() ? "Desconocido" : errorDetail);
            final String errForEmail = errorDetail;
            runOnUiThread(() -> {
                btnPrueba.setEnabled(true);
                btnPrueba.setText("Prueba");
                if (!ok) {
                    // Mostrar error completo en txtEstado (seleccionable)
                    txtEstado.setText(msg);
                    txtEstado.setTextIsSelectable(true);
                    txtEstado.setTextColor(Color.parseColor("#dc2626"));
                    // Copiar al portapapeles
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("PrintBridge Error", msg));
                        Toast.makeText(this, "Error copiado al portapapeles", Toast.LENGTH_SHORT).show();
                    }
                    // Enviar error por email
                    enviarErrorPorEmail(errForEmail);
                } else {
                    txtEstado.setText(msg);
                    txtEstado.setTextColor(Color.parseColor("#059669"));
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void enviarErrorPorEmail(String errorDetail) {
        try {
            PrinterManager pm = PrintService.getStaticPrinterManager();
            String body = "=== FactuPOS Print Bridge Error ===\n\n"
                + "Versión: " + BuildConfig.VERSION_NAME + "\n"
                + "Build: " + BuildConfig.BUILD_DATE + "\n"
                + "Modelo: " + Build.MODEL + "\n"
                + "Fabricante: " + Build.MANUFACTURER + "\n"
                + "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "Impresora: " + (pm != null ? pm.getActiveName() : "N/A") + "\n"
                + "Tipo: " + (pm != null ? pm.getActiveType() : "N/A") + "\n"
                + "Dirección: " + (pm != null ? pm.getActiveAddress() : "N/A") + "\n\n"
                + "Error detalle:\n" + errorDetail + "\n\n"
                + "SPP error: " + (pm != null ? pm.getBluetoothPrinter().getLastError() : "N/A") + "\n"
                + "BLE error: " + (pm != null ? pm.getBlePrinter().getLastError() : "N/A") + "\n"
                + "BLE scan dispositivos: " + (pm != null ? pm.getBlePrinter().getLastScanDevices() : "N/A") + "\n";

            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("text/plain");
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"info@soportereal.com"});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "PrintBridge Error v" + BuildConfig.VERSION_NAME + " - " + Build.MODEL);
            emailIntent.putExtra(Intent.EXTRA_TEXT, body);
            startActivity(Intent.createChooser(emailIntent, "Enviar error"));
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir email", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLUETOOTH_PERMISSION_CODE) {
            // Recargar lista de impresoras después de obtener permisos BT
            cargarImpresoras();
        }
    }
}
