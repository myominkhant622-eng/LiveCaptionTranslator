package com.khant.livetranslator;

import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private LinearLayout floatingView;
    private TextView resultView;
    private String targetLang = "my";
    private boolean isSlang = true;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            targetLang = intent.getStringExtra("lang");
            isSlang = intent.getBooleanExtra("slang", true);
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingView = new LinearLayout(this);
        floatingView.setOrientation(LinearLayout.VERTICAL);
        floatingView.setBackgroundColor(Color.argb(215, 20, 20, 20));
        floatingView.setPadding(24, 20, 24, 20);

        resultView = new TextView(this);
        resultView.setText("စာသား Copy ကူးပြီး 'Translate' နှိပ်ပါ...");
        resultView.setTextColor(Color.WHITE);
        resultView.setTextSize(15);
        floatingView.addView(resultView);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button translateBtn = new Button(this);
        translateBtn.setText("Translate");
        translateBtn.setOnClickListener(v -> translateFromClipboard());
        btnRow.addView(translateBtn);

        Button closeBtn = new Button(this);
        closeBtn.setText("X");
        closeBtn.setOnClickListener(v -> stopSelf());
        btnRow.addView(closeBtn);

        floatingView.addView(btnRow);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = 120;

        windowManager.addView(floatingView, params);
    }

    private void translateFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence clipText = clipboard.getPrimaryClip().getItemAt(0).getText();
            if (clipText != null && clipText.length() > 0) {
                resultView.setText("ဘာသာပြန်နေသည်...");
                translateAsync(clipText.toString());
                return;
            }
        }
        resultView.setText("Copy ကူးထားသော စာသားမရှိပါ။");
    }

    private void translateAsync(String text) {
        new Thread(() -> {
            try {
                String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                        + targetLang + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                JSONArray jsonArray = new JSONArray(response.toString());
                JSONArray sentences = jsonArray.getJSONArray(0);
                StringBuilder translated = new StringBuilder();
                for (int i = 0; i < sentences.length(); i++) {
                    translated.append(sentences.getJSONArray(i).getString(0));
                }

                String finalText = applyStyle(translated.toString());
                new Handler(Looper.getMainLooper()).post(() -> resultView.setText(finalText));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> resultView.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private String applyStyle(String text) {
        if (!isSlang) return text;
        if ("my".equals(targetLang)) {
            return text.replace("မကောင်းဘူး", "စုတ်ပြတ်နေတာပဲ")
                       .replace("သွားစမ်း", "လိုးပဲသွားစမ်း")
                       .replace("ဘာလဲ", "ဘာလိုးတာလဲ")
                       .replace("အရမ်း", "သေလောက်အောင်");
        } else {
            return text.replace("bad", "f***ing bad")
                       .replace("what", "dafuq")
                       .replace("go away", "get the f*** out");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
