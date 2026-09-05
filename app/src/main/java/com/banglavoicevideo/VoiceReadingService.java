package com.banglavoicevideo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Locale;

public class VoiceReadingService extends Service {

    private static final String CHANNEL_ID = "BanglaVoiceVideoVoiceChannel";
    private static final int NOTIFICATION_ID = 1001;

    private TextToSpeech tts;
    private String pendingText;
    private String pendingLanguage;
    private boolean ttsReady = false;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            );
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }

        initializeTTS();
    }

    private void initializeTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setSpeechRate(0.95f);
                tts.setPitch(1.0f);
                setupListener();

                if (pendingText != null && !pendingText.trim().isEmpty()) {
                    String text = pendingText;
                    String language = pendingLanguage;

                    pendingText = null;
                    pendingLanguage = null;

                    speakText(text, language);
                }
            }
        });
    }

    private void setupListener() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                if ("background_reading".equals(utteranceId)) {
                    stopSelf();
                }
            }

            @Override
            public void onError(String utteranceId) {
                stopSelf();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if ("STOP".equals(action)) {
            if (tts != null) {
                tts.stop();
            }

            pendingText = null;
            pendingLanguage = null;
            stopSelf();

            return START_NOT_STICKY;
        }

        String text = intent.getStringExtra("text");
        String language = intent.getStringExtra("language");

        if (text == null || text.trim().isEmpty()) {
            return START_NOT_STICKY;
        }

        if (!ttsReady || tts == null) {
            pendingText = text;
            pendingLanguage = language;
            return START_NOT_STICKY;
        }

        speakText(text, language);

        return START_NOT_STICKY;
    }

    private void speakText(String text, String language) {

        if (tts == null || !ttsReady) {
            return;
        }

        try {
            int result;

            if ("en".equals(language)) {
                result = tts.setLanguage(Locale.US);
            } else {
                result = tts.setLanguage(new Locale("bn", "BD"));
            }

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                return;
            }

            tts.setSpeechRate(0.95f);
            tts.setPitch(1.0f);

            String cleanText = cleanTextForSpeech(text);

            if (!cleanText.isEmpty()) {
                tts.speak(
                        cleanText,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "background_reading"
                );
            }

        } catch (Exception e) {
            stopSelf();
        }
    }

    private String cleanTextForSpeech(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replaceAll("[*_#@|~^`]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Notification createNotification() {

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BanglaVoiceVideo")
                .setContentText("ভয়েস পড়া চলছে")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Voice Reading",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription("BanglaVoiceVideo voice reading");

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        ttsReady = false;

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
