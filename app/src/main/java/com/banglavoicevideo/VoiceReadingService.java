package com.banglavoicevideo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Locale;

public class VoiceReadingService extends Service {

    private TextToSpeech tts;

    private static final String CHANNEL_ID =
            "BanglaVoiceVideoVoiceChannel";

    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        startForeground(
                NOTIFICATION_ID,
                createNotification()
        );

        initializeTTS();
    }

    private void initializeTTS() {

        tts = new TextToSpeech(
                this,
                status -> {

                    if (status == TextToSpeech.SUCCESS) {

                        tts.setLanguage(
                                new Locale("bn", "BD")
                        );

                        tts.setSpeechRate(0.95f);
                        tts.setPitch(1.0f);

                        setupListener();
                    }
                }
        );
    }

    private void setupListener() {

        tts.setOnUtteranceProgressListener(
                new UtteranceProgressListener() {

            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {

                stopSelf();
            }

            @Override
            public void onError(String utteranceId) {

                stopSelf();
            }
        });
    }

    private Notification createNotification() {

        return new NotificationCompat.Builder(
                this,
                CHANNEL_ID
        )
                .setContentTitle(
                        "BanglaVoiceVideo"
                )
                .setContentText(
                        "ভয়েস পড়া চলছে"
                )
                .setSmallIcon(
                        android.R.drawable.ic_media_play
                )
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Voice Reading",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "BanglaVoiceVideo voice reading"
            );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {
                manager.createNotificationChannel(
                        channel
                );
            }
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        if (intent != null &&
                intent.hasExtra("text")) {

            String text =
                    intent.getStringExtra("text");

            if (text != null &&
                    !text.trim().isEmpty() &&
                    tts != null) {

                tts.setSpeechRate(0.95f);
                tts.setPitch(1.0f);

                tts.speak(
                        cleanTextForSpeech(text),
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "background_reading"
                );
            }
        }

        return START_NOT_STICKY;
    }

    private String cleanTextForSpeech(
            String text) {

        if (text == null) {
            return "";
        }

        return text
                .replaceAll(
                        "[*_#@|~^`]+",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    @Override
    public void onDestroy() {

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
