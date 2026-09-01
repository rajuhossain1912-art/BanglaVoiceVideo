package com.banglavoicevideo;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends Activity {

    private TextToSpeech tts;
    private EditText textInput;
    private TextView statusText;
    private TextView titleText;

    private Button listenButton;
    private Button pauseButton;
    private Button settingsButton;

    private boolean ttsReady = false;
    private boolean speaking = false;
    private boolean paused = false;

    private int currentPart = 0;
    private List<String> speechParts = new ArrayList<>();

    private SharedPreferences preferences;

    private static final String PREFS = "BanglaVoiceVideoSettings";
    private static final String PREF_LANGUAGE = "app_language";
    private static final String PREF_SPEED = "voice_speed";

    private String appLanguage = "bn";
    private float speechRate = 1.0f;

    private static final int MAX_SPEECH_CHARS = 1000;

    private Button exportAudioButton;
    private boolean exportingAudio = false;
    private int exportIndex = 0;
    private List<String> exportParts = new ArrayList<>();
    private List<File> exportFiles = new ArrayList<>();
    private File exportDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);

        appLanguage = preferences.getString(
                PREF_LANGUAGE,
                "bn"
        );

        speechRate = preferences.getFloat(
                PREF_SPEED,
                0.95f
        );

        createMainInterface();
        initializeTTS();
    }

    private void initializeTTS() {

        tts = new TextToSpeech(
                this,
                result -> {

                    if (result == TextToSpeech.SUCCESS) {

                        ttsReady = true;

                        tts.setSpeechRate(
                                speechRate
                        );

                        tts.setPitch(1.0f);

                        setupTTSListener();

                        setStatus(
                                getText("প্রস্তুত", "Ready")
                        );

                        updateButtons();

                    } else {

                        ttsReady = false;

                        setStatus(
                                getText(
                                        "ভয়েস সিস্টেম প্রস্তুত করা যায়নি",
                                        "TTS could not be initialized"
                                )
                        );
                    }
                }
        );
    }

    private void setupTTSListener() {

        tts.setOnUtteranceProgressListener(
                new UtteranceProgressListener() {

                    @Override
                    public void onStart(
                            String utteranceId) {

                        runOnUiThread(() -> {

                            speaking = true;

                            setStatus(
                                    getText(
                                            "ভয়েস চলছে",
                                            "Voice is playing"
                                    )
                            );

                            updateButtons();
                        });
                    }

                    @Override
                    public void onDone(
                            String utteranceId) {

                        runOnUiThread(() -> {

                            if (utteranceId != null
                                    && utteranceId.startsWith("export_")) {

                                if (!exportingAudio) {
                                    return;
                                }

                                exportIndex++;

                                if (exportIndex >= exportParts.size()) {
                                    finishAudioExport();
                                } else {
                                    synthesizeNextExportPart();
                                }

                                return;
                            }

                            if (paused) {
                                return;
                            }

                            currentPart++;

                            if (currentPart <
                                    speechParts.size()) {

                                speakCurrentPart();

                            } else {

                                speaking = false;
                                paused = false;
                                currentPart = 0;

                                setStatus(
                                        getText(
                                                "পড়া শেষ হয়েছে",
                                                "Reading finished"
                                        )
                                );

                                updateButtons();
                            }
                        });
                    }

                    @Override
                    public void onError(
                            String utteranceId) {

                        runOnUiThread(() -> {

                            speaking = false;

                            setStatus(
                                    getText(
                                            "ভয়েস পড়তে সমস্যা হয়েছে",
                                            "Voice playback error"
                                    )
                            );

                            updateButtons();
                        });
                    }
                }
        );
    }

    private void createMainInterface() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                24,
                24,
                24,
                24
        );

        root.setBackgroundColor(
                Color.WHITE
        );

        titleText =
                new TextView(this);

        TextView title = titleText;

        title.setText(
                getText(
                        "BanglaVoiceVideo",
                        "BanglaVoiceVideo"
                )
        );

        title.setTextSize(26);

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        title.setTextColor(
                Color.BLACK
        );

        title.setGravity(
                Gravity.CENTER
        );

        title.setPadding(
                0,
                0,
                0,
                20
        );

        title.setContentDescription(
                "BanglaVoiceVideo"
        );

        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        ScrollView textScroll =
                new ScrollView(this);

        textScroll.setFillViewport(true);

        textInput =
                new EditText(this);

        textInput.setHint(
                getText(
                        "এখানে বাংলা বা English লেখা লিখুন",
                        "Write Bangla or English text here"
                )
        );

        textInput.setTextSize(18);

        textInput.setTextColor(
                Color.BLACK
        );

        textInput.setHintTextColor(
                Color.GRAY
        );

        textInput.setGravity(
                Gravity.TOP | Gravity.START
        );

        textInput.setPadding(
                20,
                20,
                20,
                20
        );

        textInput.setSingleLine(false);

        textInput.setMaxLines(
                Integer.MAX_VALUE
        );

        textInput.setVerticalScrollBarEnabled(
                true
        );

        textInput.setContentDescription(
                getText(
                        "বাংলা অথবা English লেখা লেখার ঘর",
                        "Text input box for Bangla or English"
                )
        );

        textScroll.addView(
                textInput,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        500
                )
        );

        root.addView(
                textScroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f
                )
        );

        listenButton =
                new Button(this);

        listenButton.setText(
                getText(
                        "লেখা শুনুন",
                        "Listen"
                )
        );

        listenButton.setContentDescription(
                getText(
                        "লেখা শুনুন",
                        "Listen to text"
                )
        );

        listenButton.setOnClickListener(
                v -> startReading()
        );

        root.addView(
                listenButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        pauseButton =
                new Button(this);

        pauseButton.setOnClickListener(
                v -> pauseOrResume()
        );

        root.addView(
                pauseButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        settingsButton =
                new Button(this);

        settingsButton.setText(
                getText(
                        "⚙ সেটিংস",
                        "⚙ Settings"
                )
        );

        settingsButton.setContentDescription(
                getText(
                        "সেটিংস",
                        "Settings"
                )
        );

        settingsButton.setOnClickListener(
                v -> showSettings()
        );

        root.addView(
                settingsButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        exportAudioButton = new Button(this);

        exportAudioButton.setText(
                getText(
                        "অডিও এক্সপোর্ট",
                        "Export Audio"
                )
        );

        exportAudioButton.setContentDescription(
                getText(
                        "সম্পূর্ণ লেখা অডিও ফাইলে সংরক্ষণ করুন",
                        "Save the complete text as an audio file"
                )
        );

        exportAudioButton.setOnClickListener(
                v -> exportAudio()
        );

        root.addView(
                exportAudioButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        statusText =
                new TextView(this);

        statusText.setText(
                getText(
                        "বর্তমান অবস্থা: প্রস্তুত",
                        "Current status: Ready"
                )
        );

        statusText.setTextSize(17);

        statusText.setTextColor(
                Color.BLACK
        );

        statusText.setGravity(
                Gravity.CENTER
        );

        statusText.setPadding(
                0,
                15,
                0,
                5
        );

        statusText.setContentDescription(
                getText(
                        "বর্তমান অবস্থা",
                        "Current status"
                )
        );

        root.addView(
                statusText,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(root);

        updateButtons();
    }

    private void startReading() {

        if (!ttsReady) {

            setStatus(
                    getText(
                            "ভয়েস সিস্টেম প্রস্তুত নয়",
                            "TTS is not ready"
                    )
            );

            return;
        }

        String text =
                textInput
                        .getText()
                        .toString()
                        .trim();

        if (text.isEmpty()) {

            setStatus(
                    getText(
                            "আগে কিছু লেখা লিখুন",
                            "Please enter some text first"
                    )
            );

            return;
        }

        tts.stop();

        speechParts.clear();

        speechParts.addAll(
                splitText(text)
        );

        currentPart = 0;
        paused = false;
        speaking = false;

        speakCurrentPart();
    }

    private void pauseOrResume() {

        if (!ttsReady) {
            return;
        }

        if (speaking) {

            tts.stop();

            speaking = false;
            paused = true;

            setStatus(
                    getText(
                            "ভয়েস বন্ধ আছে",
                            "Voice is paused"
                    )
            );

            updateButtons();

            return;
        }

        if (paused &&
                currentPart <
                        speechParts.size()) {

            paused = false;

            setStatus(
                    getText(
                            "ভয়েস চালু হয়েছে",
                            "Voice resumed"
                    )
            );

            speakCurrentPart();
        }
    }

    private void speakCurrentPart() {

        if (!ttsReady) {
            return;
        }

        if (currentPart < 0 ||
                currentPart >=
                        speechParts.size()) {

            return;
        }

        String part =
                speechParts
                        .get(currentPart)
                        .trim();

        if (part.isEmpty()) {

            currentPart++;

            speakCurrentPart();

            return;
        }

        chooseLanguage(part);

        String id =
                "part_" + currentPart;

        tts.speak(
                part,
                TextToSpeech.QUEUE_FLUSH,
                null,
                id
        );

        speaking = true;

        updateButtons();
    }

    private List<String> splitText(String text) {
        ArrayList<String> result = new ArrayList<>();
        String normalized = text.replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) return result;

        String[] sentences = normalized.split("(?<=[।.!?])\\s+");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;
            if (current.length() == 0) {
                current.append(sentence);
            } else if (current.length() + 1 + sentence.length() <= MAX_SPEECH_CHARS) {
                current.append(" ").append(sentence);
            } else {
                result.addAll(splitLongSpeechText(current.toString(), MAX_SPEECH_CHARS));
                current.setLength(0);
                current.append(sentence);
            }
        }
        if (current.length() > 0) {
            result.addAll(splitLongSpeechText(current.toString(), MAX_SPEECH_CHARS));
        }
        return result;
    }

    private List<String> splitLongSpeechText(String text, int maxChars) {
        ArrayList<String> result = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (current.length() > 0 && current.length() + 1 + word.length() > maxChars) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(" ");
            current.append(word);
        }
        if (current.length() > 0) result.add(current.toString().trim());
        return result;
    }

    private void chooseLanguage(
            String text) {

        try {

            boolean bangla =
                    containsBangla(text);

            boolean english =
                    containsEnglish(text);

            if (bangla && !english) {

                setBestVoice(
                        new Locale(
                                "bn",
                                "BD"
                        )
                );

            } else if (english &&
                    !bangla) {

                setBestVoice(
                        Locale.US
                );

            } else if (bangla) {

                setBestVoice(
                        new Locale(
                                "bn",
                                "BD"
                        )
                );

            } else {

                setBestVoice(
                        Locale.US
                );
            }

        } catch (Exception ignored) {
        }
    }

    private void setBestVoice(
            Locale locale) {

        if (tts == null) {
            return;
        }

        Voice fallback = null;

        try {

            if (tts.getVoices() == null) {
                tts.setLanguage(locale);
                return;
            }

            for (Voice voice :
                    tts.getVoices()) {

                Locale voiceLocale =
                        voice.getLocale();

                if (voiceLocale == null) {
                    continue;
                }

                if (voiceLocale
                        .getLanguage()
                        .equals(
                                locale.getLanguage()
                        )) {

                    if (!voice
                            .isNetworkConnectionRequired()) {

                        tts.setVoice(voice);

                        return;
                    }

                    if (fallback == null) {
                        fallback = voice;
                    }
                }
            }

            if (fallback != null) {

                tts.setVoice(fallback);

            } else {

                tts.setLanguage(locale);
            }

        } catch (Exception ignored) {

            try {
                tts.setLang
