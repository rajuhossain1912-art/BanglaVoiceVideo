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

        android.content.SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        appLanguage = prefs.getString(PREF_LANGUAGE, "bn");
        speechRate = prefs.getFloat(PREF_SPEED, 1.0f);

        setContentView(R.layout.activity_main);

        textInput = findViewById(R.id.text_input);
        statusText = findViewById(R.id.status_text);
        listenButton = findViewById(R.id.listen_button);
        pauseButton = findViewById(R.id.pause_button);
        settingsButton = findViewById(R.id.settings_button);

        listenButton.setOnClickListener(v -> startReading());
        pauseButton.setOnClickListener(v -> pauseOrResume());
        settingsButton.setOnClickListener(v -> showSettings());

        if (savedInstanceState != null) {
            String text = savedInstanceState.getString(STATE_TEXT, "");
            currentPart = savedInstanceState.getInt(STATE_PART, 0);
            paused = savedInstanceState.getBoolean(STATE_PAUSED, false);

            if (!text.isEmpty()) {
                textInput.setText(text);
                textInput.setSelection(text.length());
                speechParts.addAll(splitText(text));
            }
        }

        updateUI();
        initializeTTS();
    }

    private void initializeTTS() {
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setSpeechRate(speechRate);
                tts.setPitch(1.0f);
                setupListener();
                updateUI();
            } else {
                ttsReady = false;
                setStatus(getText("ভয়েস প্রস্তুত নয়", "Voice is not ready"));
            }
        });
    }

    private void setupListener() {
        tts.setOnUtteranceProgressListener(
                new UtteranceProgressListener() {

            @Override
            public void onStart(String id) {
                runOnUiThread(() -> {
                    speaking = true;
                    setStatus(getText("ভয়েস চলছে", "Voice is playing"));
                    updateUI();
                });
            }

            @Override
            public void onDone(String id) {
                runOnUiThread(() -> {

                    if (paused) return;

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

                        updateUI();
                    }
                });
            }

            @Override
            public void onError(String id) {
                runOnUiThread(() -> {
                    speaking = false;
                    setStatus(
                            getText(
                                    "ভয়েস পড়তে সমস্যা হয়েছে",
                                    "Voice playback error"
                            )
                    );
                    updateUI();
                });
            }
        });
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

        safeStop();

        speechParts.clear();
        speechParts.addAll(splitText(text));

        currentPart = 0;
        speaking = false;
        paused = false;

        speakCurrentPart();
    }

    private void pauseOrResume() {

        if (!ttsReady || speechParts.isEmpty()) return;

        if (speaking) {

            safeStop();

            speaking = false;
            paused = true;

            setStatus(
                    getText(
                            "ভয়েস বন্ধ আছে",
                            "Voice is paused"
                    )
            );

            updateUI();

        } else if (paused) {

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

        if (!ttsReady || tts == null) return;

        if (currentPart >= speechParts.size()) return;

        String text = speechParts.get(currentPart).trim();

        if (text.isEmpty()) {
            currentPart++;
            speakCurrentPart();
            return;
        }

        try {

            if (containsBangla(text)) {
                tts.setLanguage(new Locale("bn", "BD"));
            } else {
                tts.setLanguage(Locale.US);
            }

            String id =
                    "speech_" +
                    currentPart +
                    "_" +
                    System.currentTimeMillis();

            tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    id
            );

            speaking = true;
            updateUI();

        } catch (Exception e) {

            speaking = false;

            setStatus(
                    getText(
                            "ভয়েস চালু করা যায়নি",
                            "Could not start voice"
                    )
            );

            updateUI();
        }
    }

    private List<String> splitText(String text) {

        ArrayList<String> parts = new ArrayList<>();

        String normalized = text
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) return parts;

        String[] sentences =
                normalized.split(
                        "(?<=[।!?|.,])\\s+"
                );

        for (String sentence : sentences) {

            sentence = sentence.trim();

            if (!sentence.isEmpty()) {
                parts.add(sentence);
            }
        }

        if (parts.isEmpty()) {
            parts.add(normalized);
        }

        return parts;
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

    private void updateUI() {

        if (listenButton == null) return;

        listenButton.setText(
                getText("ভয়েস শুনুন", "Listen Voice")
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

        settingsButton.setText(
                getText("⚙ সেটিংস", "⚙ Settings")
        );

        textInput.setContentDescription(
                getText(
                        "বাংলা অথবা English লেখা লেখার ঘর",
                        "Bangla or English text input"
                )
        );

        listenButton.setContentDescription(
                listenButton.getText().toString()
        );

        pauseButton.setContentDescription(
                pauseButton.getText().toString()
        );

        settingsButton.setContentDescription(
                getText("সেটিংস", "Settings")
        );
    }

    private void showSettings() {

        View view = getLayoutInflater().inflate(
                R.layout.dialog_settings,
                null
        );

        TextView languageLabel =
                view.findViewById(R.id.language_label);

        Spinner languageSpinner =
                view.findViewById(R.id.language_spinner);

        TextView speedLabel =
                view.findViewById(R.id.speed_label);

        Spinner speedSpinner =
                view.findViewById(R.id.speed_spinner);

        Button aboutButton =
                view.findViewById(R.id.about_button);

        languageLabel.setText(
                getText("অ্যাপের ভাষা", "App Language")
        );

        speedLabel.setText(
                getText("ভয়েসের গতি", "Voice Speed")
        );

        aboutButton.setText(
                getText("ℹ অ্যাপ সম্পর্কে", "ℹ About")
        );

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

        languageSpinner.setAdapter(languageAdapter);

        languageSpinner.setSelection(
                "en".equals(appLanguage) ? 1 : 0
        );

        languageSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    private boolean first = true;

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {

                        if (first) {
                            first = false;
                            return;
                        }

                        String newLanguage =
                                position == 1 ? "en" : "bn";

                        if (!newLanguage.equals(appLanguage)) {

                            appLanguage = newLanguage;

                            getSharedPreferences(
                                    PREFS,
                                    MODE_PRIVATE
                            ).edit()
                                    .putString(
                                            PREF_LANGUAGE,
                                            appLanguage
                                    )
                                    .apply();

                            recreate();
                        }
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

        ArrayAdapter<String> speedAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        speeds
                );

        speedAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        speedSpinner.setAdapter(speedAdapter);

        int selected = 1;

        if (speechRate <= 0.85f) {
            selected = 0;
        } else if (speechRate >= 1.25f) {
            selected = 3;
        } else if (speechRate >= 1.10f) {
            selected = 2;
        }

        speedSpinner.setSelection(selected);

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

                        getSharedPreferences(
                                PREFS,
                                MODE_PRIVATE
                        ).edit()
                                .putFloat(
                                        PREF_SPEED,
                                        speechRate
                                )
                                .apply();

                        if (tts != null && ttsReady) {
                            tts.setSpeechRate(speechRate);
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

        new AlertDialog.Builder(this)
                .setTitle(
                        getText("সেটিংস", "Settings")
                )
                .setView(view)
                .setPositiveButton(
                        getText("বন্ধ করুন", "Close"),
                        null
                )
                .show();
    }

    private void showAbout() {

        String about = getText(
                "BanglaVoiceVideo\n\n" +
                        "বাংলা ও English লেখা থেকে " +
                        "ভয়েস তৈরি করার জন্য এই অ্যাপ।\n\n" +
                        "দৃষ্টি প্রতিবন্ধী ব্যবহারকারীদের " +
                        "জন্য TalkBack সহায়তা বজায় রাখা হয়েছে।\n\n" +
                        "নাম: MD Raju Hossain\n" +
                        "জেলা: রংপুর\n" +
                        "WhatsApp: 01744614234",

                "BanglaVoiceVideo\n\n" +
                        "An app for creating voice from " +
                        "Bangla and English text.\n\n" +
                        "TalkBack accessibility is maintained " +
                        "for visually impaired users.\n\n" +
                        "Name: MD Raju Hossain\n" +
                        "District: Rangpur\n" +
                        "WhatsApp: 01744614234"
        );

        TextView text = new TextView(this);

        text.setText(about);
        text.setTextSize(17);
        text.setPadding(25, 20, 25, 20);
        text.setContentDescription(about);

        new AlertDialog.Builder(this)
                .setTitle(
                        getText(
                                "অ্যাপ সম্পর্কে",
                                "About"
                        )
                )
                .setView(text)
                .setPositiveButton(
                        getText("বন্ধ করুন", "Close"),
                        null
                )
                .show();
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

            String full =
                    getText(
                            "বর্তমান অবস্থা: ",
                            "Current status: "
                    ) + message;

            statusText.setText(full);
            statusText.setContentDescription(full);
        }
    }

    private void safeStop() {

        if (tts != null && ttsReady) {

            try {
                tts.stop();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState) {

        super.onSaveInstanceState(outState);

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

        safeStop();

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
