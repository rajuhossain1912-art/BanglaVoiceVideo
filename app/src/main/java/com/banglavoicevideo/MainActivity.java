package com.banglavoicevideo;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextToSpeech tts;

    private EditText textInput;
    private TextView statusText;
    private Button listenButton;
    private Button pauseButton;
    private Button settingsButton;

    private boolean ttsReady = false;
    private boolean speaking = false;
    private boolean paused = false;

    private int currentPart = 0;
    private final ArrayList<String> speechParts = new ArrayList<>();

    private String appLanguage = "bn";
    private float speechRate = 1.0f;

    private static final String PREFS = "BanglaVoiceVideoSettings";
    private static final String PREF_LANGUAGE = "app_language";
    private static final String PREF_SPEED = "voice_speed";

    private static final String STATE_TEXT = "state_text";
    private static final String STATE_PART = "state_part";
    private static final String STATE_PAUSED = "state_paused";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.content.SharedPreferences preferences =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        appLanguage = preferences.getString(PREF_LANGUAGE, "bn");
        speechRate = preferences.getFloat(PREF_SPEED, 1.0f);

        setContentView(R.layout.activity_main);

        bindViews();

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

        updateAllTexts();
        initializeTTS();
    }

    private void bindViews() {

        textInput = findViewById(R.id.text_input);
        statusText = findViewById(R.id.status_text);
        listenButton = findViewById(R.id.listen_button);
        pauseButton = findViewById(R.id.pause_button);
        settingsButton = findViewById(R.id.settings_button);

        textInput.setContentDescription(
                getText(
                        "বাংলা অথবা English লেখা লেখার ঘর",
                        "Text input box for Bangla or English"
                )
        );

        listenButton.setOnClickListener(v -> startReading());

        pauseButton.setOnClickListener(v -> pauseOrResume());

        settingsButton.setOnClickListener(v -> showSettings());

        updateButtons();
    }

    private void initializeTTS() {

        ttsReady = false;

        tts = new TextToSpeech(this, result -> {

            if (result == TextToSpeech.SUCCESS) {

                ttsReady = true;

                try {
                    tts.setSpeechRate(speechRate);
                    tts.setPitch(1.0f);
                } catch (Exception ignored) {
                }

                setupTTSListener();

                if (paused) {
                    setStatus(
                            getText(
                                    "ভয়েস বন্ধ আছে",
                                    "Voice is paused"
                            )
                    );
                } else {
                    setStatus(
                            getText(
                                    "প্রস্তুত",
                                    "Ready"
                            )
                    );
                }

                updateButtons();

            } else {

                ttsReady = false;

                setStatus(
                        getText(
                                "ভয়েস সিস্টেম প্রস্তুত করা যায়নি",
                                "TTS could not be initialized"
                        )
                );

                updateButtons();
            }
        });
    }

    private void setupTTSListener() {

        tts.setOnUtteranceProgressListener(
                new UtteranceProgressListener() {

                    @Override
                    public void onStart(String utteranceId) {

                        runOnUiThread(() -> {

                            speaking = true;
                            paused = false;

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
                    public void onDone(String utteranceId) {

                        runOnUiThread(() -> {

                            if (paused) {
                                updateButtons();
                                return;
                            }

                            currentPart++;

                            if (currentPart < speechParts.size()) {

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
                    public void onError(String utteranceId) {

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

    private void startReading() {

        if (!ttsReady || tts == null) {

            setStatus(
                    getText(
                            "ভয়েস সিস্টেম প্রস্তুত নয়",
                            "TTS is not ready"
                    )
            );

            return;
        }

        String text = textInput.getText().toString().trim();

        if (text.isEmpty()) {

            setStatus(
                    getText(
                            "আগে কিছু লেখা লিখুন",
                            "Please enter some text first"
                    )
            );

            return;
        }

        safeStopTTS();

        speechParts.clear();
        speechParts.addAll(splitText(text));

        currentPart = 0;
        speaking = false;
        paused = false;

        speakCurrentPart();
    }

    private void pauseOrResume() {

        if (!ttsReady || tts == null || speechParts.isEmpty()) {
            return;
        }

        if (speaking) {

            safeStopTTS();

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

        if (paused && currentPart < speechParts.size()) {

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

        if (!ttsReady || tts == null) {
            return;
        }

        if (currentPart < 0 ||
                currentPart >= speechParts.size()) {
            return;
        }

        String part = speechParts
                .get(currentPart)
                .trim();

        if (part.isEmpty()) {

            currentPart++;
            speakCurrentPart();

            return;
        }

        chooseLanguage(part);

        String utteranceId =
                "part_" +
                currentPart +
                "_" +
                System.nanoTime();

        try {

            tts.speak(
                    part,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    utteranceId
            );

            speaking = true;

            updateButtons();

        } catch (Exception e) {

            speaking = false;

            setStatus(
                    getText(
                            "ভয়েস চালু করা যায়নি",
                            "Could not start voice"
                    )
            );

            updateButtons();
        }
    }

    /*
     * Smart Text Splitting
     *
     * প্রতি ৮ শব্দে আর কাটা হবে না।
     * দাঁড়ি, প্রশ্নবোধক, বিস্ময়সূচক, |, কমা
     * এবং English punctuation অনুযায়ী বাক্য ভাগ করা হবে।
     */
    private List<String> splitText(String text) {

        ArrayList<String> result = new ArrayList<>();

        String normalized = text
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) {
            return result;
        }

        String[] sentences = normalized.split(
                "(?<=[।!?|.,])\\s+"
        );

        for (String sentence : sentences) {

            String s = sentence.trim();

            if (s.isEmpty()) {
                continue;
            }

            final int maxChars = 3500;

            if (s.length() <= maxChars) {

                result.add(s);

                continue;
            }

            String[] words = s.split("\\s+");

            StringBuilder chunk =
                    new StringBuilder();

            for (String word : words) {

                if (chunk.length() > 0 &&
                        chunk.length()
                                + word.length()
                                + 1 > maxChars) {

                    result.add(
                            chunk.toString().trim()
                    );

                    chunk.setLength(0);
                }

                if (chunk.length() > 0) {
                    chunk.append(' ');
                }

                chunk.append(word);
            }

            if (chunk.length() > 0) {

                result.add(
                        chunk.toString().trim()
                );
            }
        }

        if (result.isEmpty()) {
            result.add(normalized);
        }

        return result;
    }

    private void chooseLanguage(String text) {

        try {

            boolean bangla =
                    containsBangla(text);

            boolean english =
                    containsEnglish(text);

            if (bangla) {

                setBestVoice(
                        new Locale("bn", "BD")
                );

            } else if (english) {

                setBestVoice(Locale.US);

            } else {

                setBestVoice(
                        new Locale("bn", "BD")
                );
            }

        } catch (Exception ignored) {
        }
    }

    private void setBestVoice(Locale locale) {

        if (tts == null) {
            return;
        }

        android.speech.tts.Voice selected =
                findVoice(locale);

        try {

            if (selected != null) {

                tts.setVoice(selected);

            } else {

                tts.setLanguage(locale);
            }

        } catch (Exception ignored) {

            try {
                tts.setLanguage(locale);
            } catch (Exception ignoredAgain) {
            }
        }
    }

    private android.speech.tts.Voice findVoice(
            Locale locale) {

        try {

            if (tts.getVoices() == null) {
                return null;
            }

            android.speech.tts.Voice fallback =
                    null;

            for (android.speech.tts.Voice voice :
                    tts.getVoices()) {

                Locale voiceLocale =
                        voice.getLocale();

                if (voiceLocale == null) {
                    continue;
                }

                if (!voiceLocale
                        .getLanguage()
                        .equals(locale.getLanguage())) {
                    continue;
                }

                if (!voice.isNetworkConnectionRequired()) {
                    return voice;
                }

                if (fallback == null) {
                    fallback = voice;
                }
            }

            return fallback;

        } catch (Exception e) {

            return null;
        }
    }

    private boolean containsBangla(String text) {

        for (int i = 0; i < text.length(); i++) {

            char c = text.charAt(i);

            if (c >= '\u0980' &&
                    c <= '\u09FF') {

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

    private void updateButtons() {

        if (listenButton == null ||
                pauseButton == null) {
            return;
        }

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

        if (speaking) {

            pauseButton.setText(
                    getText(
                            "ভয়েস বন্ধ করুন",
                            "Pause Voice"
                    )
            );

        } else if (paused) {

            pauseButton.setText(
                    getText(
                            "ভয়েস চালু করুন",
                            "Resume Voice"
                    )
            );

        } else {

            pauseButton.setText(
                    getText(
                            "ভয়েস বন্ধ করুন",
                            "Pause Voice"
                    )
            );
        }

        pauseButton.setContentDescription(
                pauseButton.getText().toString()
        );

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
    }

    private void showSettings() {

        View dialogView =
                getLayoutInflater().inflate(
                        R.layout.dialog_settings,
                        null
                );

        TextView languageLabel =
                dialogView.findViewById(
                        R.id.language_label
                );

        Spinner languageSpinner =
                dialogView.findViewById(
                        R.id.language_spinner
                );

        TextView speedLabel =
                dialogView.findViewById(
                        R.id.speed_label
                );

        Spinner speedSpinner =
                dialogView.findViewById(
                        R.id.speed_spinner
                );

        Button aboutButton =
                dialogView.findViewById(
                        R.id.about_button
                );

        languageLabel.setText(
                getText(
                        "অ্যাপের ভাষা",
                        "App Language"
                )
        );

        speedLabel.setText(
                getText(
                        "ভয়েসের গতি",
                        "Voice Speed"
                )
        );

        aboutButton.setText(
                getText(
                        "ℹ অ্যাপ সম্পর্কে",
                        "ℹ About"
                )
        );

        String[] languages = {
                "বাংলা",
                "English"
        };

        ArrayAdapter<String>
                languageAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout
                                .simple_spinner_item,
                        languages
                );

        languageAdapter
                .setDropDownViewResource(
                        android.R.layout
                                .simple_spinner_dropdown_item
                );

        languageSpinner.setAdapter(
                languageAdapter
        );

        languageSpinner.setContentDescription(
                getText(
                        "অ্যাপের ভাষা নির্বাচন",
                        "Select app language"
                )
        );

        languageSpinner.setSelection(
                "en".equals(appLanguage)
                        ? 1
                        : 0
        );

        final boolean[]
                firstLanguageSelection =
                {true};

        languageSpinner
                .setOnItemSelectedListener(
                        new AdapterView
                                .OnItemSelectedListener() {

                            @Override
                            public void onItemSelected(
                                    AdapterView<?> parent,
                                    View view,
                                    int position,
                                    long id) {

                                if (firstLanguageSelection[0]) {

                                    firstLanguageSelection[0] =
                                            false;

                                    return;
                                }

                                String newLanguage =
                                        position == 1
                                                ? "en"
                                                : "bn";

                                if (newLanguage.equals(
                                        appLanguage)) {
                                    return;
                                }

                                appLanguage =
                                        newLanguage;

                                getSharedPreferences(
                                        PREFS,
                                        MODE_PRIVATE
                                )
                                        .edit()
                                        .putString(
                                                PREF_LANGUAGE,
                                                appLanguage
                                        )
                                        .apply();

                                recreate();
                            }

                            @Override
                            public void onNothingSelected(
                                    AdapterView<?> parent) {
                            }
                        }
                );

        String[] speeds = {
                getText("ধীর", "Slow"),
                getText("স্বাভাবিক", "Normal"),
                getText("দ্রুত", "Fast"),
                getText("খুব দ্রুত", "Very Fast")
        };

        ArrayAdapter<String>
                speedAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout
                                .simple_spinner_item,
                        speeds
                );

        speedAdapter
                .setDropDownViewResource(
                        android.R.layout
                                .simple_spinner_dropdown_item
                );

        speedSpinner.setAdapter(
                speedAdapter
        );

        speedSpinner.setContentDescription(
                getText(
                        "ভয়েসের গতি নির্বাচন",
                        "Select voice speed"
                )
        );

        int selected = 1;

        if (speechRate <= 0.85f) {

            selected = 0;

        } else if (speechRate >= 1.25f) {

            selected = 3;

        } else if (speechRate >= 1.10f) {

            selected = 2;
        }

        speedSpinner.setSelection(
                selected
        );

        speedSpinner
                .setOnItemSelectedListener(
                        new AdapterView
                                .OnItemSelectedListener() {

                            @Override
                            public void onItemSelected(
                                    AdapterView<?> parent,
                                    View view,
                                    int position,
                                    long id) {

                                if (position == 0) {

                                    speechRate = 0.85f;

                                } else if (position == 1) {

                                    speechRate = 1.0f;

                                } else if (position == 2) {

                                    speechRate = 1.15f;

                                } else {

                                    speechRate = 1.30f;
                                }

                                getSharedPreferences(
                                        PREFS,
                                        MODE_PRIVATE
                                )
                                        .edit()
                                        .putFloat(
                                                PREF_SPEED,
                                                speechRate
                                        )
                                        .apply();

                                if (tts != null &&
                                        ttsReady) {

                                    try {
                                        tts.setSpeechRate(
                                                speechRate
                                        );
                                    } catch (Exception ignored) {
                                    }
                                }
                            }

                            @Override
                            public void onNothingSelected(
                                    AdapterView<?> parent) {
                            }
                        }
                );

        aboutButton.setOnClickListener(
                v -> showAbout()
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                getText(
                                        "সেটিংস",
                                        "Settings"
                                )
                        )
                        .setView(dialogView)
                        .setPositiveButton(
                                getText(
                                        "বন্ধ করুন",
                                        "Close"
                                ),
                                null
                        )
                        .create();

        dialog.show();
    }

    private void showAbout() {

        String aboutText =
                getText(
                        "BanglaVoiceVideo\n\n" +
                                "বাংলা ও English লেখা থেকে " +
                                "ভয়েস এবং ভবিষ্যতে ভিডিও তৈরি " +
                                "করার জন্য এই অ্যাপটি তৈরি করা হচ্ছে।\n\n" +
                                "দৃষ্টি প্রতিবন্ধী ব্যবহারকারীদের " +
                                "জন্য অ্যাপটিকে সহজ ও TalkBack " +
                                "সহায়ক রাখার লক্ষ্য রয়েছে।\n\n" +
                                "নাম: MD Raju Hossain\n" +
                                "জেলা: রংপুর\n" +
                                "WhatsApp: 01744614234",
                        "BanglaVoiceVideo\n\n" +
                                "This app is being developed " +
                                "to create voice and, in the " +
                                "future, videos from Bangla and " +
                                "English text.\n\n" +
                                "The goal is to make the app " +
                                "simple and accessible for " +
                                "visually impaired users, " +
                                "including TalkBack support.\n\n" +
                                "Name: MD Raju Hossain\n" +
                                "District: Rangpur\n" +
                                "WhatsApp: 01744614234"
                );

        TextView aboutView =
                new TextView(this);

        aboutView.setText(
                aboutText
        );

        aboutView.setTextSize(17);

        aboutView.setPadding(
                25,
                20,
                25,
                20
        );

        aboutView.setContentDescription(
                aboutText
        );

        android.widget.ScrollView scroll =
                new android.widget.ScrollView(this);

        scroll.addView(aboutView);

        new AlertDialog.Builder(this)
                .setTitle(
                        getText(
                                "অ্যাপ সম্পর্কে",
                                "About"
                        )
                )
                .setView(scroll)
                .setPositiveButton(
                        getText(
                                "বন্ধ করুন",
                                "Close"
                        ),
                        null
                )
                .show();
    }

    private void updateAllTexts() {

        if (textInput == null) {
            return;
        }

        textInput.setHint(
                getText(
                        "এখানে বাংলা বা English লেখা লিখুন",
                        "Write Bangla or English text here"
                )
        );

        setStatus(
                getText(
                        "প্রস্তুত",
                        "Ready"
                )
        );

        updateButtons();
    }

    private String getText(
            String bangla,
            String english) {

        return "en".equals(appLanguage)
                ? english
                : bangla;
    }

    private void setStatus(String message) {

        if (statusText != null) {

            String fullMessage =
                    getText(
                            "বর্তমান অবস্থা: ",
                            "Current status: "
                    ) + message;

            statusText.setText(
                    fullMessage
            );

            statusText.setContentDescription(
                    fullMessage
            );
        }
    }

    private void safeStopTTS() {

        if (tts != null && ttsReady) {

            try {
                tts.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private void restoreState(
            Bundle state) {

        String savedText =
                state.getString(
                        STATE_TEXT,
                        ""
                );

        currentPart =
                state.getInt(
                        STATE_PART,
                        0
                );

        paused =
                state.getBoolean(
                        STATE_PAUSED,
                        false
                );

        if (textInput != null &&
                !savedText.isEmpty()) {

            textInput.setText(
                    savedText
            );

            textInput.setSelection(
                    savedText.length()
            );
        }

        if (!savedText.isEmpty()) {

            speechParts.clear();

            speechParts.addAll(
                    splitText(savedText)
            );

            if (currentPart >=
                    speechParts.size()) {

                currentPart = 0;
            }
        }

        speaking = false;
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState) {

        super.onSaveInstanceState(
                outState
        );

        if (textInput != null) {

            outState.putString(
                    STATE_TEXT,
                    textInput.getText().toString()
            );
        }

        outState.putInt(
                STATE_PART,
                currentPart
        );

        outState.putBoolean(
                STATE_PAUSED,
                paused
        );
    }

    @Override
    protected void onDestroy() {

        safeStopTTS();

        if (tts != null) {

            try {
                tts.shutdown();
            } catch (Exception ignored) {
            }

            tts = null;
        }

        ttsReady = false;

        super.onDestroy();
    }
}
