package com.controlyourphone;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int SHIZUKU_REQUEST_CODE = 4917;
    private final int ink = 0xFF17243A, muted = 0xFF59677D, blue = 0xFF315DA8, page = 0xFFF4F6FA;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView deviceText, memoryText, batteryText, storageText, shizukuText, outputText, appListText;
    private TextInputEditText commandInput;
    private boolean destroyed = false;

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            refreshShizukuStatus();
            toast(grantResult == PackageManager.PERMISSION_GRANTED ? "Izin Shizuku diberikan." : "Izin Shizuku ditolak.");
        }
    };
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::refreshShizukuStatus;
    private final Shizuku.OnBinderDeadListener binderDeadListener = this::refreshShizukuStatus;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        buildUi();
        refreshDeviceInfo();
        refreshShizukuStatus();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(page);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(content);
        content.addView(label("DEVICE UTILITIES  •  SHIZUKU", 12, blue, true));
        TextView title = label("Control Your Phone", 29, ink, true);
        LinearLayout.LayoutParams titleLp = params(-1, -2); titleLp.topMargin = dp(5); content.addView(title, titleLp);
        TextView intro = label("Real device information and authorized diagnostics.", 14, muted, false);
        LinearLayout.LayoutParams introLp = params(-1, -2); introLp.topMargin = dp(5); content.addView(intro, introLp);

        deviceText = label("", 14, ink, false); memoryText = label("", 14, ink, false);
        batteryText = label("", 14, ink, false); storageText = label("", 14, ink, false);
        addCard("DEVICE OVERVIEW", deviceText, memoryText, batteryText, storageText,
                action("Refresh information", v -> refreshDeviceInfo()));

        shizukuText = label("", 14, ink, false);
        addCard("SHIZUKU ACCESS", shizukuText,
                action("Request / check permission", v -> requestShizukuPermission()),
                action("Open Wireless debugging", v -> openWirelessDebugging()),
                action("Open this app's settings", v -> openAppSettings()));

        addHeading("Diagnostics");
        TextView diagNote = label("These shortcuts collect read-only system information. Output depends on your Android version and manufacturer.", 13, muted, false);
        addCard("READ-ONLY CHECKS", diagNote,
                action("System properties", v -> runPreset("getprop")),
                action("Battery diagnostics", v -> runPreset("dumpsys battery")),
                action("Thermal service", v -> runPreset("dumpsys thermalservice")),
                action("Display service", v -> runPreset("dumpsys display")),
                action("List CPU information", v -> runPreset("cat /proc/cpuinfo")),
                action("List memory information", v -> runPreset("cat /proc/meminfo")));

        addHeading("Shell command");
        TextView warning = label("Commands run with Android's shell identity through Shizuku, not root. Only run commands you understand. Some commands will be denied by Android.", 13, 0xFF80591A, false);
        commandInput = new TextInputEditText(this);
        commandInput.setSingleLine(false); commandInput.setMinLines(1); commandInput.setMaxLines(3);
        commandInput.setTextSize(14); commandInput.setHint("Example: getprop ro.build.version.release");
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("Shell command"); inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.addView(commandInput, new LinearLayout.LayoutParams(-1, -2));
        outputText = label("Output will appear here.", 12, ink, false); outputText.setTextIsSelectable(true);
        addCard("AUTHORIZED SHELL", warning, inputLayout,
                action("Run command", v -> runShellCommand()),
                action("Clear output", v -> outputText.setText("Output cleared.")), outputText);

        addHeading("Installed apps");
        appListText = label("", 13, ink, false);
        addCard("APP INVENTORY", label("Shows packages visible to this application. Android privacy rules may hide some apps.", 13, muted, false),
                action("Load installed apps", v -> loadInstalledApps()), appListText);
        TextView footer = label("Control Your Phone 2.0 • Material 3 • No fake optimization switches", 12, muted, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = params(-1, -2); footerLp.topMargin = dp(24); content.addView(footer, footerLp);
        setContentView(scroll);
    }

    private void refreshDeviceInfo() {
        if (deviceText == null) return;
        deviceText.setText("Device: " + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL)
                + "\nProduct: " + safe(Build.PRODUCT)
                + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nCPU ABI: " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "Unavailable")
                + "\nBuild fingerprint: " + safe(Build.FINGERPRINT));
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                memoryText.setText("RAM total: " + formatBytes(mi.totalMem) + "\nRAM available: " + formatBytes(mi.availMem)
                        + "\nLow-memory state: " + (mi.lowMemory ? "Yes" : "No"));
            } else memoryText.setText("RAM information unavailable.");
        } catch (Throwable e) { memoryText.setText("RAM information unavailable: " + e.getClass().getSimpleName()); }

        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int pct = level >= 0 && scale > 0 ? level * 100 / scale : -1;
            int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            batteryText.setText("Charge: " + (pct >= 0 ? pct + "%" : "Unavailable")
                    + "\nTemperature: " + (temp >= 0 ? String.format(Locale.US, "%.1f °C", temp / 10.0) : "Unavailable")
                    + "\nHealth: " + batteryHealth(battery.getIntExtra(BatteryManager.EXTRA_HEALTH, 0))
                    + "\nStatus: " + batteryStatus(battery.getIntExtra(BatteryManager.EXTRA_STATUS, 0)));
        } else batteryText.setText("Battery information unavailable.");
        try {
            StatFs stat = new StatFs(getFilesDir().getAbsolutePath());
            storageText.setText("Storage volume: " + formatBytes(stat.getTotalBytes()) + " total\nAvailable: "
                    + formatBytes(stat.getAvailableBytes()) + "\nNote: this is the app-data volume, not a combined report of every storage device.");
        } catch (Throwable e) { storageText.setText("Storage information unavailable: " + e.getClass().getSimpleName()); }
    }

    private void refreshShizukuStatus() {
        if (shizukuText == null || destroyed) return;
        boolean connected, granted = false;
        try { connected = Shizuku.pingBinder(); if (connected) granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable ignored) { connected = false; }
        final boolean c = connected, g = granted;
        main.post(() -> { if (shizukuText != null && !destroyed) shizukuText.setText("Service: " + (c ? "Connected" : "Not connected")
                + "\nPermission: " + (g ? "Granted" : "Not granted") + "\nExecution identity: Android shell (not root)"); });
    }

    private void requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) { toast("Shizuku belum berjalan. Jalankan Shizuku lalu coba lagi."); refreshShizukuStatus(); return; }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) toast("Izin Shizuku sudah diberikan.");
            else Shizuku.requestPermission(SHIZUKU_REQUEST_CODE);
        } catch (Throwable e) { toast("Tidak bisa meminta izin: " + e.getClass().getSimpleName()); }
        refreshShizukuStatus();
    }

    private boolean shellReady() {
        try { return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable ignored) { return false; }
    }
    private void runPreset(String command) { if (commandInput != null) commandInput.setText(command); runCommand(command); }
    private void runShellCommand() {
        String command = commandInput == null || commandInput.getText() == null ? "" : commandInput.getText().toString().trim();
        if (command.isEmpty()) { toast("Masukkan perintah terlebih dahulu."); return; }
        if (command.length() > 1500) { toast("Perintah terlalu panjang (maksimal 1500 karakter)."); return; }
        runCommand(command);
    }

    private void runCommand(String command) {
        if (!shellReady()) {
            if (outputText != null) outputText.setText("Shizuku belum siap atau izin belum diberikan.\nJalankan Shizuku, lalu tekan tombol izin.");
            refreshShizukuStatus(); return;
        }
        if (outputText != null) outputText.setText("Running: " + command + "\nPlease wait…");
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && commandInput != null) imm.hideSoftInputFromWindow(commandInput.getWindowToken(), 0);
        } catch (Throwable ignored) { }
        executor.execute(() -> {
            String result; Process process = null;
            try {
                java.lang.reflect.Method processMethod = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                processMethod.setAccessible(true);
                process = (Process) processMethod.invoke(null, new String[]{"sh", "-c", "(" + command + ") 2>&1"}, null, null);
                final Process active = process;
                FutureTask<String> readerTask = new FutureTask<>(() -> {
                    StringBuilder out = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(active.getInputStream()))) {
                        String line; while ((line = reader.readLine()) != null) if (out.length() < 18000) out.append(line).append('\n');
                    }
                    return out.toString();
                });
                Thread readerThread = new Thread(readerTask, "cyphone-shell-output"); readerThread.setDaemon(true); readerThread.start();
                FutureTask<Integer> waitTask = new FutureTask<>(active::waitFor);
                Thread waitThread = new Thread(waitTask, "cyphone-shell-wait"); waitThread.setDaemon(true); waitThread.start();
                boolean finished;
                try { waitTask.get(20, TimeUnit.SECONDS); finished = true; }
                catch (java.util.concurrent.TimeoutException timeout) { finished = false; }
                if (!finished) {
                    process.destroy(); readerThread.interrupt(); waitThread.interrupt();
                    String partial = ""; try { partial = readerTask.get(1, TimeUnit.SECONDS); } catch (Throwable ignored) { }
                    result = "Command timed out after 20 seconds.\n" + (partial.isEmpty() ? "No output captured." : "Output:\n" + partial);
                } else {
                    String out;
                    try { out = readerTask.get(2, TimeUnit.SECONDS); } catch (Throwable e) { out = "Output reader failed: " + e.getClass().getSimpleName(); }
                    result = "Exit code: " + waitTask.get() + "\n\n" + (out.isEmpty() ? "(no output)" : out);
                }
            } catch (Throwable e) { result = "Command failed: " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()); }
            finally { if (process != null) try { process.destroy(); } catch (Throwable ignored) { } }
            final String finalResult = result;
            main.post(() -> { if (!destroyed && outputText != null) outputText.setText(finalResult); });
        });
    }

    private void loadInstalledApps() {
        appListText.setText("Loading visible packages…");
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            try {
                PackageManager pm = getPackageManager();
                List<PackageInfo> apps = pm.getInstalledPackages(0);
                Collections.sort(apps, (a, b) -> {
                    String an = a.applicationInfo == null ? a.packageName : pm.getApplicationLabel(a.applicationInfo).toString();
                    String bn = b.applicationInfo == null ? b.packageName : pm.getApplicationLabel(b.applicationInfo).toString();
                    return an.compareToIgnoreCase(bn);
                });
                int count = 0;
                for (PackageInfo info : apps) {
                    if (count++ >= 250) { sb.append("\n… list limited to 250 visible packages."); break; }
                    String label = info.applicationInfo == null ? info.packageName : pm.getApplicationLabel(info.applicationInfo).toString();
                    sb.append("• ").append(label).append("\n  ").append(info.packageName).append('\n');
                }
                if (sb.length() == 0) sb.append("No visible packages found.");
            } catch (Throwable e) { sb.append("Could not list packages: ").append(e.getClass().getSimpleName()); }
            final String result = sb.toString();
            main.post(() -> { if (!destroyed && appListText != null) appListText.setText(result); });
        });
    }

    private void openAppSettings() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        catch (Throwable e) { toast("Tidak bisa membuka pengaturan aplikasi."); }
    }
    private void openWirelessDebugging() {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                intent = new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS");
                if (intent.resolveActivity(getPackageManager()) == null) intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            } else intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            startActivity(intent);
        } catch (Throwable e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) { toast("Pengaturan tidak dapat dibuka."); }
        }
    }
    private String batteryHealth(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "Unspecified failure";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }
    private String batteryStatus(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not charging";
            default: return "Unknown";
        }
    }
    private String formatBytes(long bytes) {
        if (bytes < 0) return "Unavailable";
        if (bytes < 1024L) return bytes + " B";
        double value = bytes; String[] units = {"B", "KB", "MB", "GB", "TB"}; int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }
    private String safe(String s) { return s == null || s.trim().isEmpty() ? "Unavailable" : s; }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); v.setLineSpacing(dp(3), 1.0f);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private void addHeading(String text) {
        TextView heading = label(text, 21, ink, true); LinearLayout.LayoutParams lp = params(-1, -2);
        lp.topMargin = dp(22); lp.bottomMargin = dp(2); content.addView(heading, lp);
    }
    private void addCard(String title, View... children) {
        MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(20)); card.setCardBackgroundColor(0xFFFFFFFF);
        card.setStrokeColor(0xFFE2E7F0); card.setStrokeWidth(dp(1)); card.setCardElevation(dp(1)); card.setUseCompatPadding(true);
        LinearLayout.LayoutParams cardLp = params(-1, -2); cardLp.topMargin = dp(12); content.addView(card, cardLp);
        LinearLayout inside = new LinearLayout(this); inside.setOrientation(LinearLayout.VERTICAL); inside.setPadding(dp(16), dp(15), dp(16), dp(16)); card.addView(inside);
        inside.addView(label(title, 12, blue, true));
        for (View child : children) { LinearLayout.LayoutParams lp = params(-1, -2); lp.topMargin = dp(10); inside.addView(child, lp); }
    }
    private MaterialButton action(String text, View.OnClickListener listener) {
        MaterialButton b = new MaterialButton(this); b.setText(text); b.setAllCaps(false); b.setTextSize(14); b.setCornerRadius(dp(14)); b.setOnClickListener(listener); return b;
    }
    private LinearLayout.LayoutParams params(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private void toast(String text) { if (!destroyed) Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}