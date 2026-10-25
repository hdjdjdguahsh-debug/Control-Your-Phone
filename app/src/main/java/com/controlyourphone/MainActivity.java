package com.controlyourphone;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
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
    private static final int CREATE_REPORT_REQUEST = 4918;
    private final int ink = 0xFF17243A, muted = 0xFF59677D, blue = 0xFF315DA8, page = 0xFFF4F6FA;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView deviceText, memoryText, batteryText, storageText, shizukuText, outputText, appListText;
    private TextView healthText, networkText, historyText;
    private String pendingDiagnosticReport = "";
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
        try {
            // Keep the main screen usable even if optional integrations fail.
            try { DynamicColors.applyToActivityIfAvailable(this); } catch (Throwable ignored) { }
            buildUi();
            showPreviousCrashReport();
            refreshDeviceInfo();

            // Shizuku is optional: never let a stopped/missing service prevent launch.
            try {
                Shizuku.addRequestPermissionResultListener(permissionListener);
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
                Shizuku.addBinderDeadListener(binderDeadListener);
                refreshShizukuStatus();
            } catch (Throwable e) {
                if (shizukuText != null) {
                    shizukuText.setText("Shizuku integration unavailable: "
                            + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : "\n" + e.getMessage()));
                }
            }
        } catch (Throwable e) {
            try {
                ((CrashLoggingApplication) getApplication()).saveCrashReport(Thread.currentThread(), e);
            } catch (Throwable ignored) { }
            showStartupError(e);
        }
    }

    private View crashReportCard;

    /**
     * Shows the last uncaught crash on the next launch so the report can be
     * copied and shared without a computer, ADB, or a separate log viewer.
     */
    private void showPreviousCrashReport() {
        final String report = CrashLoggingApplication.readLastCrash(this);
        if (report == null || report.trim().isEmpty() || content == null) return;

        MaterialCardView card = new MaterialCardView(this);
        crashReportCard = card;
        card.setRadius(dp(20));
        card.setCardBackgroundColor(0xFFFFF4F2);
        card.setStrokeColor(0xFFDF8B82);
        card.setStrokeWidth(dp(1));
        card.setCardElevation(dp(1));
        card.setUseCompatPadding(true);

        LinearLayout.LayoutParams cardLp = params(-1, -2);
        cardLp.topMargin = dp(12);
        content.addView(card, Math.min(3, content.getChildCount()));

        LinearLayout inside = new LinearLayout(this);
        inside.setOrientation(LinearLayout.VERTICAL);
        inside.setPadding(dp(16), dp(15), dp(16), dp(16));
        card.addView(inside);

        inside.addView(label("PREVIOUS CRASH CAPTURED", 12, 0xFFB42318, true));
        TextView explanation = label(
                "Copy this report and paste it into the chat to identify the failing class and line.",
                13, muted, false);
        LinearLayout.LayoutParams explanationLp = params(-1, -2);
        explanationLp.topMargin = dp(8);
        inside.addView(explanation, explanationLp);

        String preview = report.length() > 4200
                ? report.substring(0, 4200) + "\n\n[Preview truncated. Use Copy full crash report for the complete log.]"
                : report;
        TextView reportText = label(preview, 11, ink, false);
        reportText.setTextIsSelectable(true);
        reportText.setMaxLines(36);
        LinearLayout.LayoutParams reportLp = params(-1, -2);
        reportLp.topMargin = dp(10);
        inside.addView(reportText, reportLp);

        LinearLayout.LayoutParams copyLp = params(-1, -2);
        copyLp.topMargin = dp(10);
        inside.addView(action("Copy full crash report", v -> {
            try {
                ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null) {
                    toast("Clipboard is unavailable.");
                    return;
                }
                clipboard.setPrimaryClip(ClipData.newPlainText("Control Your Phone crash report", report));
                toast("Crash report copied. Paste it into this chat.");
            } catch (Throwable copyError) {
                toast("Could not copy report: " + copyError.getClass().getSimpleName());
            }
        }), copyLp);

        LinearLayout.LayoutParams clearLp = params(-1, -2);
        clearLp.topMargin = dp(6);
        inside.addView(action("Clear crash report", v -> {
            CrashLoggingApplication.clearLastCrash(this);
            if (crashReportCard != null && content != null) content.removeView(crashReportCard);
            crashReportCard = null;
            toast("Crash report cleared.");
        }), clearLp);
    }

    private void showStartupError(Throwable error) {
        try {
            LinearLayout fallback = new LinearLayout(this);
            fallback.setOrientation(LinearLayout.VERTICAL);
            fallback.setPadding(dp(20), dp(24), dp(20), dp(24));

            TextView heading = new TextView(this);
            heading.setText("Control Your Phone could not start");
            heading.setTextSize(20);
            heading.setTypeface(null, android.graphics.Typeface.BOLD);
            fallback.addView(heading);

            String report = CrashLoggingApplication.readLastCrash(this);
            String details = error.getClass().getName() + "\n" + String.valueOf(error.getMessage());
            TextView message = new TextView(this);
            message.setText(details + (report == null ? "" : "\n\n--- SAVED CRASH REPORT ---\n" + report));
            message.setTextIsSelectable(true);
            message.setTextSize(13);
            LinearLayout.LayoutParams messageLp = params(-1, -2);
            messageLp.topMargin = dp(12);
            fallback.addView(message, messageLp);

            if (report != null && !report.trim().isEmpty()) {
                LinearLayout.LayoutParams copyLp = params(-1, -2);
                copyLp.topMargin = dp(12);
                fallback.addView(action("Copy full crash report", v -> {
                    try {
                        ClipboardManager clipboard =
                                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Control Your Phone crash report", report));
                            toast("Crash report copied. Paste it into this chat.");
                        }
                    } catch (Throwable copyError) {
                        toast("Copy failed: " + copyError.getClass().getSimpleName());
                    }
                }), copyLp);
            }
            setContentView(fallback);
        } catch (Throwable fallbackError) {
            try {
                TextView message = new TextView(this);
                message.setText("Startup error: " + error.getClass().getName()
                        + "\n" + String.valueOf(error.getMessage()));
                message.setTextIsSelectable(true);
                setContentView(message);
            } catch (Throwable ignored) { }
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        try { Shizuku.removeRequestPermissionResultListener(permissionListener); } catch (Throwable ignored) { }
        try { Shizuku.removeBinderReceivedListener(binderReceivedListener); } catch (Throwable ignored) { }
        try { Shizuku.removeBinderDeadListener(binderDeadListener); } catch (Throwable ignored) { }
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

        healthText = label("Run a check to summarize available device indicators.", 14, ink, false);
        addCard("ONE-TAP HEALTH CHECK", healthText,
                action("Run health check", v -> runHealthCheck()),
                action("Export diagnostic report", v -> exportDiagnosticReport()));

        networkText = label("Network test has not run yet.", 14, ink, false);
        addCard("NETWORK DETECTIVE", networkText,
                action("Check network and latency", v -> runNetworkCheck()));

        historyText = label("No diagnostic history yet.", 13, ink, false);
        addCard("DIAGNOSTIC HISTORY", historyText,
                action("Refresh history", v -> refreshDiagnosticHistory()),
                action("Clear history", v -> { getSharedPreferences("diagnostics", MODE_PRIVATE).edit().remove("history").apply(); refreshDiagnosticHistory(); toast("Diagnostic history cleared."); }));
        refreshDiagnosticHistory();

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
        TextView footer = label("Control Your Phone v1.0 • Diagnostics first, no fake optimization switches", 12, muted, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = params(-1, -2); footerLp.topMargin = dp(24); content.addView(footer, footerLp);
        setContentView(scroll);
    }

    private void runHealthCheck() {
        if (healthText == null) return;
        try {
            refreshDeviceInfo();
            List<String> findings = new java.util.ArrayList<>();
            List<String> warnings = new java.util.ArrayList<>();
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                if (mi.lowMemory) warnings.add("Android reports low available memory.");
                else findings.add("Memory pressure: no low-memory warning reported.");
            } else warnings.add("Memory information is unavailable.");

            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int pct = level >= 0 && scale > 0 ? level * 100 / scale : -1;
                int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (pct >= 0 && pct < 20) warnings.add("Battery is below 20% (" + pct + "%).");
                else if (pct >= 20) findings.add("Battery level: " + pct + "%.");
                else warnings.add("Battery level is unavailable.");
                if (temp >= 500) warnings.add("Battery temperature is high (" + String.format(Locale.US, "%.1f °C", temp / 10.0) + ").");
                else if (temp >= 0) findings.add("Battery temperature: " + String.format(Locale.US, "%.1f °C", temp / 10.0) + ".");
            } else warnings.add("Battery information is unavailable.");

            StatFs stat = new StatFs(getFilesDir().getAbsolutePath());
            long total = stat.getTotalBytes();
            long available = stat.getAvailableBytes();
            if (total > 0) {
                int freePct = (int) Math.min(100, (available * 100L) / total);
                if (freePct < 10) warnings.add("App-data volume has less than 10% free space (" + freePct + "%).");
                else findings.add("App-data volume free space: " + freePct + "%.");
            } else warnings.add("Storage information is unavailable.");

            String status = warnings.isEmpty() ? "OK" : "WARNING";
            StringBuilder report = new StringBuilder();
            report.append("Control Your Phone — Diagnostic Report\n");
            report.append("Time: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new java.util.Date())).append('\n');
            report.append("App version: ").append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName).append('\n');
            report.append("Device: ").append(safe(Build.MANUFACTURER)).append(' ').append(safe(Build.MODEL)).append('\n');
            report.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            report.append("Health status: ").append(status).append("\n\n");
            report.append("Warnings:\n");
            if (warnings.isEmpty()) report.append("- No warning indicators found in the checks available to this app.\n");
            else for (String warning : warnings) report.append("- ").append(warning).append('\n');
            report.append("\nFindings:\n");
            for (String finding : findings) report.append("- ").append(finding).append('\n');
            report.append("\nLimitations: This is a basic indicator check, not a malware scan or a full hardware diagnostic. Storage figures refer to the app-data volume.\n");
            pendingDiagnosticReport = report.toString();
            healthText.setText("Status: " + status + "\n" + (warnings.isEmpty() ? "No warning indicators found in available checks." : String.join("\n", warnings))
                    + (findings.isEmpty() ? "" : "\n\n" + String.join("\n", findings)));
            saveDiagnosticHistory(status + " — " + warnings.size() + " warning(s)");
            refreshDiagnosticHistory();
        } catch (Throwable e) {
            healthText.setText("Health check could not finish: " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    private void saveDiagnosticHistory(String entry) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("diagnostics", MODE_PRIVATE);
            String old = prefs.getString("history", "");
            String timestamp = new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new java.util.Date());
            String next = timestamp + "  " + entry + (old == null || old.isEmpty() ? "" : "\n" + old);
            String[] lines = next.split("\\n");
            StringBuilder limited = new StringBuilder();
            for (int i = 0; i < Math.min(lines.length, 10); i++) {
                if (i > 0) limited.append('\n');
                limited.append(lines[i]);
            }
            prefs.edit().putString("history", limited.toString()).apply();
        } catch (Throwable ignored) { }
    }

    private void refreshDiagnosticHistory() {
        if (historyText == null) return;
        String history = getSharedPreferences("diagnostics", MODE_PRIVATE).getString("history", "");
        historyText.setText(history == null || history.trim().isEmpty() ? "No diagnostic history yet." : history);
    }

    private void exportDiagnosticReport() {
        if (pendingDiagnosticReport == null || pendingDiagnosticReport.trim().isEmpty()) runHealthCheck();
        if (pendingDiagnosticReport == null || pendingDiagnosticReport.trim().isEmpty()) {
            toast("Run the health check first.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "control-your-phone-diagnostic.txt");
        try { startActivityForResult(intent, CREATE_REPORT_REQUEST); }
        catch (Throwable e) { toast("Could not open file picker: " + e.getClass().getSimpleName()); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREATE_REPORT_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (OutputStream stream = getContentResolver().openOutputStream(data.getData());
             OutputStreamWriter writer = stream == null ? null : new OutputStreamWriter(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            if (writer == null) { toast("Could not open the selected file."); return; }
            writer.write(pendingDiagnosticReport);
            writer.flush();
            toast("Diagnostic report exported.");
        } catch (Throwable e) {
            toast("Report export failed: " + e.getClass().getSimpleName());
        }
    }

    private void runNetworkCheck() {
        if (networkText == null) return;
        networkText.setText("Checking network status and TCP connection latency…");
        executor.execute(() -> {
            String result;
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                Network active = cm == null ? null : cm.getActiveNetwork();
                NetworkCapabilities caps = active == null || cm == null ? null : cm.getNetworkCapabilities(active);
                if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    result = "No active network with internet capability was reported by Android. This does not prove whether the Wi-Fi router itself is working.";
                } else {
                    String transport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi"
                            : caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Cellular"
                            : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet" : "Other";
                    long started = System.nanoTime();
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress("connectivitycheck.gstatic.com", 443), 4000);
                        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                        result = "Network: " + transport + "\nInternet capability: reported by Android\nTCP connect latency to connectivitycheck.gstatic.com: " + ms + " ms\nNote: this is a single TCP connection test, not a full speed test.";
                    } catch (Throwable e) {
                        result = "Network: " + transport + "\nAndroid reports internet capability, but the TCP test failed: " + e.getClass().getSimpleName() + ". The network may block the test host or internet access.";
                    }
                }
            } catch (Throwable e) {
                result = "Network check failed: " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            }
            final String finalResult = result;
            main.post(() -> {
                if (!destroyed && networkText != null) networkText.setText(finalResult);
                if (!destroyed) saveDiagnosticHistory("Network check completed");
                refreshDiagnosticHistory();
            });
        });
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