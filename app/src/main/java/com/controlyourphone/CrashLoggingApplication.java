package com.controlyourphone;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Stores the most recent uncaught exception so it can be copied from inside
 * the app after the next launch, without requiring ADB or another app.
 */
public class CrashLoggingApplication extends Application {
    private static final String TAG = "ControlYourPhoneCrash";
    private static final String CRASH_FILE = "last_crash.txt";
    private static final int MAX_REPORT_CHARS = 64000;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            saveCrashReport(thread, error);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    public void saveCrashReport(Thread thread, Throwable error) {
        try {
            File file = new File(getFilesDir(), CRASH_FILE);
            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
                writer.println("Control Your Phone - crash report");
                writer.println("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
                        .format(new Date()));
                writer.println("App version: " + getVersionName());
                writer.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
                writer.println("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
                writer.println("Thread: " + (thread == null ? "unknown" : thread.getName()));
                writer.println();
                error.printStackTrace(writer);
                writer.flush();
                if (writer.checkError()) {
                    Log.e(TAG, "Crash report writer reported an error.");
                }
            }
        } catch (Throwable loggingError) {
            // Never let crash-reporting prevent Android's normal crash handling.
            Log.e(TAG, "Could not save crash report", loggingError);
        }
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    public static String readLastCrash(Context context) {
        File file = new File(context.getFilesDir(), CRASH_FILE);
        if (!file.isFile()) return null;

        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                int remaining = MAX_REPORT_CHARS - result.length();
                if (remaining <= 0) {
                    result.append("\n[Report truncated at ").append(MAX_REPORT_CHARS).append(" characters.]");
                    break;
                }
                result.append(buffer, 0, Math.min(count, remaining));
                if (count > remaining) {
                    result.append("\n[Report truncated at ").append(MAX_REPORT_CHARS).append(" characters.]");
                    break;
                }
            }
            return result.toString();
        } catch (Throwable error) {
            return "Could not read crash report: " + error.getClass().getSimpleName()
                    + ": " + String.valueOf(error.getMessage());
        }
    }

    public static void clearLastCrash(Context context) {
        try {
            new File(context.getFilesDir(), CRASH_FILE).delete();
        } catch (Throwable ignored) {
            // Clearing a diagnostic report is best-effort.
        }
    }
}
