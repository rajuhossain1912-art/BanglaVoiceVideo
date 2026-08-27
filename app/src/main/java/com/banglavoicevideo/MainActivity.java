package com.banglavoicevideo;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextToSpeech textToSpeech;
    private EditText textInput;
    private TextView statusText;
    private Button listenButton;
    private Button pauseButton;

    private boolean ttsReady = false;
    private boolean isPaused = false;
    private boolean isSpeaking = false;

    private String currentText = "";
    private final List<String> speechParts = new ArrayList<>();
    private int currentPart = 0;

    private static final int MAX_PART_LENGTH = 3500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createInterface();

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;

                textToSpeech.setSpeechRate(0.95f);
                textToSpeech.setPitch(1.0f);

                textToSpeech.setOnUtteranceProgressListener(
                        new UtteranceProgressListener() {

                            @Override
                            public void onStart(String utteranceId) {
                                runOnUiThread(() -> {
                                    isSpeaking = true;
                                    updateButtons();
                                    statusText.setText("ভয়েস চলছে");
                                });
                            }

                            @Override
                            public void onDone(String utteranceId) {
                                runOnUiThread(() -> {
                                    if (isPaused) {
                                        return;
                                    }

                                    currentPart++;

                                    if (currentPart < speechParts.size()) {
                                        speakCurrentPart();
                                    } else {
                                        isSpeaking = false;
                                        isPaused = false;
                                        currentPart = 0;
                                        statusText.setText("পড়া শেষ হয়েছে");
                                        updateButtons();
                                    }
                                });
                            }

                            @Override
                            public void onError(String utteranceId) {
                                runOnUiThread(() -> {
                                    isSpeaking = false;
                                    statusText.setText("ভয়েস পড়তে সমস্যা হয়েছে");
                                    updateButtons();
                                });
                            }
                        }
                );

                statusText.setText("প্রস্তুত");
                updateButtons();

            } else {
                ttsReady = false;
                statusText.setText("TTS প্রস্তুত করা যায়নি");
            }
        });
    }

    private void createInterface() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 30, 30, 30);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("BanglaVoiceVideo");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 25);
        title.setContentDescription("BanglaVoiceVideo অ্যাপ");

        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        /*
         * EditText-কে আলাদা ScrollView-এর মধ্যে রাখা হয়েছে।
         * ফলে বড় লেখা দিলে পুরো স্ক্রিন দখল করবে না।
         * নিচের বাটনগুলো সবসময় আলাদা জায়গায় থাকবে।
         */
        ScrollView textScroll = new ScrollView(this);
        textScroll.setFillViewport(true);

        textInput = new EditText(this);
        textInput.setHint("এখানে বাংলা বা English লেখা লিখুন");
        textInput.setTextSize(18);
        textInput.setTextColor(Color.BLACK);
        textInput.setHintTextColor(Color.GRAY);
        textInput.setGravity(Gravity.TOP | Gravity.START);
        textInput.setPadding(20, 20, 20, 20);
        textInput.setSingleLine(false);
        textInput.setMaxLines(Integer.MAX_VALUE);
        textInput.setVerticalScrollBarEnabled(true);

        textInput.setContentDescription(
                "বাংলা অথবা English লেখা লেখার ঘর"
        );

        textScroll.addView(textInput, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                500
        ));

        root.addView(textScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        listenButton = new Button(this);
        listenButton.setText("লেখা শুনুন");
        listenButton.setContentDescription("লেখা শুনুন");
        listenButton.setOnClickListener(v -> startReading());

        root.addView(listenButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        pauseButton = new Button(this);
        pauseButton.setText("ভয়েস বন্ধ করুন");
        pauseButton.setContentDescription("ভয়েস বন্ধ করুন");
        pauseButton.setOnClickListener(v -> pauseOrResume());

        root.addView(pauseButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        statusText = new TextView(this);
        statusText.setText("প্রস্তুত");
        statusText.setTextSize(17);
        statusText.setTextColor(Color.BLACK);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 15, 0, 5);
        statusText.setContentDescription("বর্তমান অবস্থা");

        root.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
    }

    private void startReading() {

        if (!ttsReady) {
            statusText.setText("TTS এখনো প্রস্তুত নয়");
            return;
        }

        String text = textInput.getText().toString().trim();

        if (text.isEmpty()) {
            statusText.setText("আগে কিছু লেখা লিখুন");
            return;
        }

        currentText = text;

        speechParts.clear();
        speechParts.addAll(splitText(text));

        currentPart = 0;
        isPaused = false;
        isSpeaking = false;

        textToSpeech.stop();

        speakCurrentPart();
    }

    private void pauseOrResume() {

        if (!ttsReady) {
            return;
        }

        if (isSpeaking) {

            /*
             * TTS-কে থামিয়ে বর্তমান অংশের অবস্থান রাখা হচ্ছে।
             * পরেরবার চালু করলে একই অংশ থেকে আবার শুরু হবে।
             */
            textToSpeech.stop();

            isSpeaking = false;
            isPaused = true;

            statusText.setText("ভয়েস বন্ধ আছে");
            updateButtons();

        } else if (isPaused && !speechParts.isEmpty()) {

            isPaused = false;
            speakCurrentPart();

            statusText.setText("ভয়েস চালু হয়েছে");
            updateButtons();
        }
    }

    private void speakCurrentPart() {

        if (!ttsReady) {
            return;
        }

        if (currentPart < 0 || currentPart >= speechParts.size()) {
            return;
        }

        String part = speechParts.get(currentPart);

        if (part.trim().isEmpty()) {
            currentPart++;

            if (currentPart < speechParts.size()) {
                speakCurrentPart();
            }

            return;
        }

        selectVoiceForText(part);

        String utteranceId = "BanglaVoiceVideo_" + currentPart;

        textToSpeech.speak(
                part,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
        );

        isSpeaking = true;
        statusText.setText("ভয়েস চলছে");
        updateButtons();
    }

    /*
     * বাংলা এবং English-এর জন্য আলাদা TTS voice নির্বাচন।
     */
    private void selectVoiceForText(String text) {

        boolean hasBangla = containsBangla(text);
        boolean hasEnglish = containsEnglish(text);

        try {

            /*
             * বাংলা প্রধান হলে বাংলা voice ব্যবহার করার চেষ্টা।
             */
            if (hasBangla && !hasEnglish) {

                Voice banglaVoice = findVoice(
                        new Locale("bn", "BD")
                );

                if (banglaVoice != null) {
                    textToSpeech.setVoice(banglaVoice);
                } else {
                    textToSpeech.setLanguage(
                            new Locale("bn", "BD")
                    );
                }

            /*
             * English প্রধান হলে English voice ব্যবহার।
             */
            } else if (hasEnglish && !hasBangla) {

                Voice englishVoice = findVoice(
                        Locale.US
                );

                if (englishVoice != null) {
                    textToSpeech.setVoice(englishVoice);
                } else {
                    textToSpeech.setLanguage(Locale.US);
                }

            /*
             * বাংলা + English একসঙ্গে থাকলে
             * আপাতত বাংলা voice-কে অগ্রাধিকার।
             */
            } else if (hasBangla) {

                Voice banglaVoice = findVoice(
                        new Locale("bn", "BD")
                );

                if (banglaVoice != null) {
                    textToSpeech.setVoice(banglaVoice);
                } else {
                    textToSpeech.setLanguage(
                            new Locale("bn", "BD")
                    );
                }

            } else {

                textToSpeech.setLanguage(Locale.US);
            }

        } catch (Exception e) {
            try {
                textToSpeech.setLanguage(Locale.US);
            } catch (Exception ignored) {
            }
        }
    }

    private Voice findVoice(Locale wantedLocale) {

        if (textToSpeech.getVoices() == null) {
            return null;
        }

        Voice bestVoice = null;

        for (Voice voice : textToSpeech.getVoices()) {

            Locale locale = voice.getLocale();

            if (locale == null) {
                continue;
            }

            if (locale.equals(wantedLocale)) {

                if (!voice.isNetworkConnectionRequired()) {
                    return voice;
                }

                if (bestVoice == null) {
                    bestVoice = voice;
                }
            }
        }

        return bestVoice;
    }

    private boolean containsBangla(String text) {

        for (int i = 0; i < text.length(); i++) {

            char c = text.charAt(i);

            if (c >= '\u0980' && c <= '\u09FF') {
                return true;
            }
        }

        return false;
    }

    private boolean containsEnglish(String text) {

        for (int i = 0; i < text.length(); i++) {

            char c = text.charAt(i);

            if ((c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z')) {
                return true;
            }
        }

        return false;
    }

    /*
     * বড় লেখাকে ছোট অংশে ভাগ করা হচ্ছে।
     * এতে Android TTS-এর text-length সীমার সমস্যা কমবে।
     */
    private List<String> splitText(String text) {

        List<String> parts = new ArrayList<>();

        int start = 0;

        while (start < text.length()) {

            int end = Math.min(
                    start + MAX_PART_LENGTH,
                    text.length()
            );

            if (end < text.length()) {

                int sentenceBreak = findBreakPoint(
                        text,
                        start,
                        end
                );

                if (sentenceBreak > start) {
                    end = sentenceBreak;
                }
            }

            String part = text.substring(start, end).trim();

            if (!part.isEmpty()) {
                parts.add(part);
            }

            start = end;
        }

        return parts;
    }

    private int findBreakPoint(
            String text,
            int start,
            int end
    ) {

        for (int i = end - 1; i > start; i--) {

            char c = text.charAt(i);

            if (c == '।' ||
                    c == '.' ||
                    c == '!' ||
                    c == '?' ||
                    c == '\n') {

                return i + 1;
            }
        }

        for (int i = end - 1; i > start; i--) {

            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }

        return end;
    }

    private void updateButtons() {

        if (listenButton == null || pauseButton == null) {
            return;
        }

        listenButton.setText("লেখা শুনুন");
        listenButton.setContentDescription("লেখা শুনুন");

        if (isSpeaking) {

            pauseButton.setText("ভয়েস বন্ধ করুন");
            pauseButton.setContentDescription(
                    "ভয়েস বন্ধ করুন"
            );

        } else if (isPaused) {

            pauseButton.setText("ভয়েস চালু করুন");
            pauseButton.setContentDescription(
                    "ভয়েস চালু করুন"
            );

        } else {

            pauseButton.setText("ভয়েস বন্ধ করুন");
            pauseButton.setContentDescription(
                    "ভয়েস বন্ধ করুন"
            );
        }
    }

    @Override
    protected void onDestroy() {

        if (textToSpeech != null) {

            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        super.onDestroy();
    }
    }
