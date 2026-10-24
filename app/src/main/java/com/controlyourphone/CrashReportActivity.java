package com.controlyourphone;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Minimal launcher kept separate from the main UI so a MainActivity startup
 * crash can be inspected on-device without a PC or ADB.
 */
public class CrashReportActivity extends Activity {
    private TextView reportView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        showReportScreen();
    }

    private void showReportScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Control Your Phone — Crash Diagnostics");
        title.setTextSize(21);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        hint.setText("This small screen is separate from the main app. If the main screen crashes, reopen the app to view its saved report here.");
        hint.setTextSize(14);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.topMargin = pad / 2;
        root.addView(hint, hintLp);

        String report = CrashLoggingApplication.readLastCrash(this);
        reportView = new TextView(this);
        reportView.setText(report == null || report.trim().isEmpty()
                ? "NO SAVED CRASH REPORT FOUND.\n\nTap “Open main app” to reproduce the crash once, then reopen Control Your Phone. This screen should display the report if Android recorded a Java exception."
                : report);
        reportView.setTextIsSelectable(true);
        reportView.setTextSize(12);
        reportView.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(reportView);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollLp.topMargin = pad;
        scrollLp.bottomMargin = pad;
        root.addView(scroll, scrollLp);

        Button copy = new Button(this);
        copy.setText("Copy crash report");
        copy.setOnClickListener(v -> {
            String text = reportView.getText().toString();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Crash report", text));
                android.widget.Toast.makeText(this, "Copied. Paste it into ChatGPT.", android.widget.Toast.LENGTH_LONG).show();
            }
        });
        root.addView(copy, new LinearLayout.LayoutParams(-1, -2));

        Button open = new Button(this);
        open.setText("Open main app");
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, MainActivity.class));
            } catch (Throwable error) {
                reportView.setText("Could not launch MainActivity:\\n" + error);
            }
        });
        root.addView(open, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }
}
