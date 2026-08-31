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
import android.view.View;
import android.widget.AdapterView;

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
    private List<String> speechParts = new ArrayList<>();

    private SharedPreferences preferences;

    private static final String PREFS =
            "BanglaVoiceVideoSettings";

    private static final String PREF_LANGUAGE =
            "app_language";

    private static final String PREF_SPEED =
            "voice_speed";

    private String appLanguage = "bn";
    private float speechRate = 1.0f;

    private static final int WORDS_PER_PART = 8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(
                PREFS,
                MODE_PRIVATE
        );

        appLanguage = preferences.getString(
                PREF_LANGUAGE,
                "bn"
        );

        speechRate = preferences.getFloat(
                PREF_SPEED,
                1.0f
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
                                getText(
                                        "প্রস্তুত",
                                        "Ready"
                                )
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

        TextView title =
                new TextView(this);

        title.setText(
                "BanglaVoiceVideo"
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
                textInput.getText()
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
                speechParts.get(currentPart)
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

    private List<String> splitText(
            String text) {

        List<String> result =
                new ArrayList<>();

        String[] words =
                text.split("\\s+");

        StringBuilder part =
                new StringBuilder();

        int count = 0;

        for (String word : words) {

            if (word.isEmpty()) {
                continue;
            }

            if (count >=
                    WORDS_PER_PART) {

                String completed =
                        part.toString().trim();

                if (!completed.isEmpty()) {
                    result.add(completed);
                }

                part.setLength(0);
                count = 0;
            }

            if (part.length() > 0) {
                part.append(" ");
            }

            part.append(word);

            count++;
        }

        String last =
                part.toString().trim();

        if (!last.isEmpty()) {
            result.add(last);
        }

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
                tts.setLanguage(locale);
            } catch (Exception ignoredAgain) {
            }
        }
    }

    private boolean containsBangla(
            String text) {

        for (int i = 0;
                i < text.length();
                i++) {

            char c =
                    text.charAt(i);

            if (c >= '\u0980' &&
                    c <= '\u09FF') {

                return true;
            }
        }

        return false;
    }

    private boolean containsEnglish(
            String text) {

        for (int i = 0;
                i < text.length();
                i++) {

            char c =
                    text.charAt(i);

            if ((c >= 'A' &&
                    c <= 'Z') ||
                    (c >= 'a' &&
                    c <= 'z')) {

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

        if (speaking) {

            pauseButton.setText(
                    getText(
                            "ভয়েস বন্ধ করুন",
                            "Pause Voice"
                    )
            );

            pauseButton.setContentDescription(
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

            pauseButton.setContentDescription(
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

            pauseButton.setContentDescription(
                    getText(
                            "ভয়েস বন্ধ করুন",
                            "Pause Voice"
                    )
            );
        }
    }

    private void showSettings() {

        LinearLayout layout =
                new LinearLayout(this);

        layout.setOrientation(
                LinearLayout.VERTICAL
        );

        layout.setPadding(
                30,
                20,
                30,
                20
        );

        TextView heading =
                new TextView(this);

        heading.setText(
                getText(
                        "সেটিংস",
                        "Settings"
                )
        );

        heading.setTextSize(24);

        heading.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        heading.setTextColor(
                Color.BLACK
        );

        layout.addView(heading);

        /*
         * App Language
         * একটি অপশন হিসেবে থাকবে।
         * চাপ দিলে বাংলা / English নির্বাচন করা যাবে।
         */
        TextView languageLabel =
                new TextView(this);

        languageLabel.setText(
                getText(
                        "অ্যাপের ভাষা",
                        "App Language"
                )
        );

        languageLabel.setTextSize(18);

        languageLabel.setTextColor(
                Color.BLACK
        );

        languageLabel.setPadding(
                0,
                25,
                0,
                10
        );

        layout.addView(languageLabel);

        Spinner languageSpinner =
                new Spinner(this);

        String[] languages = {
                "বাংলা",
                "English"
        };

        ArrayAdapter<String> languageAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        languages
                );

        languageAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        languageSpinner.setAdapter(
                languageAdapter
        );

        if ("en".equals(appLanguage)) {
            languageSpinner.setSelection(1);
        } else {
            languageSpinner.setSelection(0);
        }

        languageSpinner.setContentDescription(
                getText(
                        "অ্যাপের ভাষা নির্বাচন",
                        "Select app language"
                )
        );

        languageSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    private boolean firstSelection = true;

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {

                        String newLanguage;

                        if (position == 1) {
                            newLanguage = "en";
                        } else {
                            newLanguage = "bn";
                        }

                        if (newLanguage.equals(
                                appLanguage)) {
                            return;
                        }

                        appLanguage =
                                newLanguage;

                        preferences.edit()
                                .putString(
                                        PREF_LANGUAGE,
                                        appLanguage
                                )
                                .apply();

                        /*
                         * কোনো Restart message নয়।
                         * সরাসরি UI refresh হবে।
                         */
                        recreate();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {
                    }
                }
        );

        layout.addView(
                languageSpinner
        );

        TextView speedLabel =
                new TextView(this);

        speedLabel.setText(
                getText(
                        "ভয়েসের গতি",
                        "Voice Speed"
                )
        );

        speedLabel.setTextSize(18);

        speedLabel.setTextColor(
                Color.BLACK
        );

        speedLabel.setPadding(
                0,
                25,
                0,
                10
        );

        layout.addView(speedLabel);

        Spinner speedSpinner =
                new Spinner(this);

        String[] speeds = {

                getText(
                        "ধীর",
                        "Slow"
                ),

                getText(
                        "স্বাভাবিক",
                        "Normal"
                ),

                getText(
                        "দ্রুত",
                        "Fast"
                ),

                getText(
                        "খুব দ্রুত",
                        "Very Fast"
                )
        };

        ArrayAdapter<String> speedAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        speeds
                );

        speedAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        speedSpinner.setAdapter(
                speedAdapter
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

        speedSpinner.setContentDescription(
                getText(
                        "ভয়েসের গতি নির্বাচন",
                        "Select voice speed"
                )
        );

        speedSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

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

                        preferences.edit()
                                .putFloat(
                                        PREF_SPEED,
                                        speechRate
                                )
                                .apply();

                        if (tts != null &&
                                ttsReady) {

                            tts.setSpeechRate(
                                    speechRate
                            );
                        }
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {
                    }
                }
        );

        layout.addView(
                speedSpinner
        );

        Button aboutButton =
                new Button(this);

        aboutButton.setText(
                getText(
                        "ℹ About",
                        "ℹ About"
                )
        );

        aboutButton.setContentDescription(
                getText(
                        "অ্যাপ সম্পর্কে",
                        "About the app"
                )
        );

        aboutButton.setOnClickListener(
                v -> showAbout()
        );

        layout.addView(
                aboutButton
        );

        android.app.AlertDialog dialog =
                new android.app.AlertDialog.Builder(
                        this
                )
                        .setView(layout)
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
                        "BanglaVoiceVideo\n\n"
                                + "বাংলা ও English লেখা থেকে "
                                + "ভয়েস এবং ভবিষ্যতে ভিডিও তৈরি "
                                + "করার জন্য এই অ্যাপটি তৈরি করা হচ্ছে।\n\n"
                                + "দৃষ্টি প্রতিবন্ধী ব্যবহারকারীদের "
                                + "জন্য অ্যাপটিকে সহজ ও TalkBack "
                                + "সহায়ক রাখার লক্ষ্য রয়েছে।\n\n"
                                + "নাম: MD Raju Hossain\n"
                                + "জেলা: রংপুর\n"
                                + "WhatsApp: 01744614234\n\n"
                                + "এই অ্যাপটি ধাপে ধাপে উন্নত করা হবে।",

                        "BanglaVoiceVideo\n\n"
                                + "This app is being developed to "
                                + "create voice and, in the future, "
                                + "videos from Bangla and English text.\n\n"
                                + "The goal is to make the app simple "
                                + "and accessible for visually impaired "
                                + "users, including TalkBack support.\n\n"
                                + "Name: MD Raju Hossain\n"
                                + "District: Rangpur\n"
                                + "WhatsApp: 01744614234\n\n"
                                + "This app will be improved step by step."
                );

        TextView aboutView =
                new TextView(this);

        aboutView.setText(
                aboutText
        );

        aboutView.setTextSize(17);

        aboutView.setTextColor(
                Color.BLACK
        );

        aboutView.setPadding(
                25,
                20,
                25,
                20
        );

        aboutView.setContentDescription(
                aboutText
        );

        ScrollView scroll =
                new ScrollView(this);

        scroll.addView(
                aboutView
        );

        new android.app.AlertDialog.Builder(
                this
        )
                .setTitle(
                        getText(
                                "About",
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

    private String getText(
            String bangla,
            String english) {

        if ("en".equals(appLanguage)) {
            return english;
        }

        return bangla;
    }

    private void setStatus(
            String message) {

        if (statusText != null) {

            statusText.setText(
                    getText(
                            "বর্তমান অবস্থা: ",
                            "Current status: "
                    ) + message
            );
        }
    }

    @Override
    protected void onDestroy() {

        if (tts != null) {

            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }
}
