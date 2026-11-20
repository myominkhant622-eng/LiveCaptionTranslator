package com.khant.liveaudiotranslator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FloatingService extends Service {
    private static final String CHANNEL_ID = "LiveAudioTranslatorChannel";
    private static final int SAMPLE_RATE = 16000;

    private WindowManager windowManager;
    private LinearLayout floatingView;
    private TextView subtitleView;
    private MediaProjection mediaProjection;
    private AudioRecord audioRecord;
    private volatile boolean isRunning = false;

    private String apiKey = "";
    private String targetLang = "Burmese";
    private boolean isSlang = true;

    private final List<String> modelList = new ArrayList<>();
    private volatile int currentModelIdx = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        setupOverlay();
    }

    private void setupOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingView = new LinearLayout(this);
        floatingView.setOrientation(LinearLayout.HORIZONTAL);
        floatingView.setBackgroundColor(Color.argb(180, 0, 0, 0));
        floatingView.setPadding(24, 16, 24, 16);
        floatingView.setGravity(Gravity.CENTER_VERTICAL);

        subtitleView = new TextView(this);
        subtitleView.setText("Model ရှာဖွေနေသည်...");
        subtitleView.setTextColor(Color.WHITE);
        subtitleView.setTextSize(16);
        subtitleView.setMaxWidth(750);
        floatingView.addView(subtitleView);

        Button closeBtn = new Button(this);
        closeBtn.setText("X");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setOnClickListener(v -> stopSelf());
        floatingView.addView(closeBtn);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = 80;

        windowManager.addView(floatingView, params);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundNotification();

        if (intent != null && !isRunning) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");
            apiKey = intent.getStringExtra("apiKey");
            targetLang = intent.getStringExtra("lang");
            isSlang = intent.getBooleanExtra("slang", true);

            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mpm != null && data != null) {
                mediaProjection = mpm.getMediaProjection(resultCode, data);
                detectModelsAndStart();
            } else {
                updateSubtitle("Error: MediaProjection မရပါ");
            }
        }
        return START_NOT_STICKY;
    }

    private void detectModelsAndStart() {
        new Thread(() -> {
            modelList.clear();
            modelList.add("models/gemini-2.5-flash-lite");
            modelList.add("models/gemini-2.5-flash");
            modelList.add("models/gemini-flash-latest");

            try {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    String body = readStream(conn.getInputStream());
                    JSONObject json = new JSONObject(body);
                    JSONArray models = json.getJSONArray("models");

                    for (int i = models.length() - 1; i >= 0; i--) {
                        JSONObject m = models.getJSONObject(i);
                        String name = m.getString("name");
                        String methods = m.optString("supportedGenerationMethods", "");
                        if (methods.contains("generateContent")
                                && name.contains("flash")
                                && !name.contains("tts")
                                && !name.contains("image")
                                && !name.contains("exp")
                                && !modelList.contains(name)) {
                            modelList.add(name);
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            currentModelIdx = 0;
            updateSubtitle("အသံနားထောင်နေသည်... (" + getActiveModel().replace("models/", "") + ")");
            startAudioCapture();
        }).start();
    }

    private String getActiveModel() {
        if (modelList.isEmpty()) return "models/gemini-2.5-flash-lite";
        if (currentModelIdx >= modelList.size()) currentModelIdx = 0;
        return modelList.get(currentModelIdx);
    }

    private void startForegroundNotification() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Live Audio Translator", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Live Audio Translator v2")
                .setContentText("System audio ကို ဘာသာပြန်နေသည်...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }
    }

    private void startAudioCapture() {
        isRunning = true;
        new Thread(() -> {
            try {
                AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();

                AudioFormat format = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build();

                // 12 seconds per chunk to completely avoid 503 overload and 429 rate limits
                int chunkBytes = SAMPLE_RATE * 2 * 12;
                int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

                audioRecord = new AudioRecord.Builder()
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(Math.max(minBuf * 4, chunkBytes))
                        .setAudioPlaybackCaptureConfig(config)
                        .build();

                audioRecord.startRecording();
                byte[] buffer = new byte[4096];

                while (isRunning) {
                    ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
                    long maxAmp = 0;

                    while (isRunning && pcmOut.size() < chunkBytes) {
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            pcmOut.write(buffer, 0, read);
                            for (int i = 0; i < read - 1; i += 2) {
                                int sample = Math.abs((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
                                if (sample > maxAmp) maxAmp = sample;
                            }
                        }
                    }

                    if (!isRunning)
                      
