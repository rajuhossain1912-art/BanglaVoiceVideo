package com.banglavoicevideo;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.ArrayList;
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

    private final List<String> speechParts = new ArrayList<>();
    private int currentPart = 0;

    /*
     * প্রতিটি অংশ ছোট রাখা হচ্ছে যাতে Pause/Resume
     * করার সময় শুরু থেকে না পড়ে।
     */
    private static final int WORDS_PER_PART = 35;

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

                                    statusText.setText(
                                            "ভয়েস চলছে"
                                    );

                                    updateButtons();
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

                                        statusText.setText(
                                                "পড়া শেষ হয়েছে"
                                        );

                                        updateButtons();
                                    }
                                });
                            }

                            @Override
                            public void onError(String utteranceId) {

                                runOnUiThread(() -> {

                                    isSpeaking = false;

                                    statusText.setText(
                                            "ভয়েস পড়তে সমস্যা হয়েছে"
                                    );

                                    updateButtons();
                                });
                            }
                        }
                );

                statusText.setText("প্রস্তুত");
                updateButtons();

            } else {

                ttsReady = false;

                statusText.setText(
                        "TTS প্রস্তুত করা যায়নি"
                );
            }
        });
    }

    private void createInterface() {

        LinearLayout root = new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                30,
                30,
                30,
                30
        );

        root.setBackgroundColor(
                Color.WHITE
        );

        TextView title = new TextView(this);

        title.setText(
                "BanglaVoiceVideo"
        );

        title.setTextSize(28);

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
                25
        );

        title.setContentDescription(
                "BanglaVoiceVideo অ্যাপ"
        );

        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        /*
         * বড় লেখা হলেও শুধু Text box-এর ভেতরে scroll হবে।
         * নিচের বাটনগুলো স্ক্রিনে থাকবে।
         */
        ScrollView textScroll = new ScrollView(this);

        textScroll.setFillViewport(true);

        EditText input = new EditText(this);

        textInput = input;

        textInput.setHint(
                "এখানে বাংলা বা English লেখা লিখুন"
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
                "বাংলা অথবা English লেখা লেখার ঘর"
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

        listenButton = new Button(this);

        listenButton.setText(
                "লেখা শুনুন"
        );

        listenButton.setContentDescription(
                "লেখা শুনুন"
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

        pauseButton = new Button(this);

        pauseButton.setText(
                "ভয়েস বন্ধ করুন"
        );

        pauseButton.setContentDescription(
                "ভয়েস বন্ধ করুন"
        );

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

        statusText = new TextView(this);

        statusText.setText(
                "প্রস্তুত"
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
                "বর্তমান অবস্থা"
        );

        root.addView(
                statusText,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(root);
    }

    private void startReading() {

        if (!ttsReady) {

            statusText.setText(
                    "TTS এখনো প্রস্তুত নয়"
            );

            return;
        }

        String text =
                textInput.getText()
                        .toString()
                        .trim();

        if (text.isEmpty()) {

            statusText.setText(
                    "আগে কিছু লেখা লিখুন"
            );

            return;
        }

        /*
         * নতুন করে লেখা শুনলে আগের অবস্থান মুছে
         * একেবারে শুরু থেকে শুরু হবে।
         */
        textToSpeech.stop();

        speechParts.clear();

        speechParts.addAll(
                splitIntoSmallParts(text)
        );

        currentPart = 0;

        isPaused = false;

        isSpeaking = false;

        speakCurrentPart();
    }

    private void pauseOrResume() {

        if (!ttsReady) {
            return;
        }

        /*
         * বর্তমানে পড়লে Pause।
         */
        if (isSpeaking) {

            textToSpeech.stop();

            isSpeaking = false;

            isPaused = true;

            statusText.setText(
                    "ভয়েস বন্ধ আছে"
            );

            updateButtons();

            return;
        }

        /*
         * Pause অবস্থায় থাকলে পরের ছোট অংশ থেকে Resume।
         */
        if (isPaused &&
                currentPart < speechParts.size()) {

            isPaused = false;

            statusText.setText(
                    "ভয়েস চালু হয়েছে"
            );

            updateButtons();

            speakCurrentPart();
        }
    }

    private void speakCurrentPart() {

        if (!ttsReady) {
            return;
        }

        if (currentPart < 0 ||
                currentPart >= speechParts.size()) {

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

        selectVoiceForText(part);

        String utteranceId =
                "BanglaVoiceVideo_part_"
                        + currentPart;

        textToSpeech.speak(
                part,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
        );

        isSpeaking = true;

        updateButtons();
    }

    /*
     * বড় লেখাকে ছোট ছোট অংশে ভাগ করা।
     * প্রতিটি অংশ সর্বোচ্চ 35টি শব্দ।
     */
    private List<String> splitIntoSmallParts(
            String text) {

        List<String> parts =
                new ArrayList<>();

        String[] words =
                text.split("\\s+");

        StringBuilder current =
                new StringBuilder();

        int count = 0;

        for (String word : words) {

            if (word.isEmpty()) {
                continue;
            }

            if (count >= WORDS_PER_PART) {

                String part =
                        current.toString().trim();

                if (!part.isEmpty()) {
                    parts.add(part);
                }

                current.setLength(0);

                count = 0;
            }

            if (current.length() > 0) {
                current.append(" ");
            }

            current.append(word);

            count++;
        }

        String lastPart =
                current.toString().trim();

        if (!lastPart.isEmpty()) {
            parts.add(lastPart);
        }

        return parts;
    }

    private void selectVoiceForText(
            String text) {

        boolean hasBangla =
                containsBangla(text);

        boolean hasEnglish =
                containsEnglish(text);

        try {

            /*
             * শুধু বাংলা হলে বাংলা voice।
             */
            if (hasBangla && !hasEnglish) {

                Voice voice =
                        findVoice(
                                new Locale(
                                        "bn",
                                        "BD"
                                )
                        );

                if (voice != null) {

                    textToSpeech.setVoice(
                            voice
                    );

                } else {

                    textToSpeech.setLanguage(
                            new Locale(
                                    "bn",
                                    "BD"
                            )
                    );
                }

            /*
             * শুধু English হলে English voice।
             */
            } else if (hasEnglish &&
                    !hasBangla) {

                Voice voice =
                        findVoice(
                                Locale.US
                        );

                if (voice != null) {

                    textToSpeech.setVoice(
                            voice
                    );

                } else {

                    textToSpeech.setLanguage(
                            Locale.US
                    );
                }

            /*
             * মিশ্র লেখা হলে বাংলা voice-কে
             * অগ্রাধিকার দেওয়া হচ্ছে।
             */
            } else if (hasBangla) {

                Voice voice =
                        findVoice(
                                new Locale(
                                        "bn",
                                        "BD"
                                )
                        );

                if (voice != null) {

                    textToSpeech.setVoice(
                            voice
                    );

                } else {

                    textToSpeech.setLanguage(
                            new Locale(
                                    "bn",
                                    "BD"
                            )
                    );
                }

            } else {

                textToSpeech.setLanguage(
                        Locale.US
                );
            }

        } catch (Exception e) {

            try {

                textToSpeech.setLanguage(
                        Locale.US
                );

            } catch (Exception ignored) {
            }
        }
    }

    private Voice findVoice(
            Locale wantedLocale) {

        if (textToSpeech.getVoices() == null) {
            return null;
        }

        Voice fallback = null;

        for (Voice voice :
                textToSpeech.getVoices()) {

            Locale locale =
                    voice.getLocale();

            if (locale == null) {
                continue;
            }

            if (locale.equals(
                    wantedLocale)) {

                if (!voice
                        .isNetworkConnectionRequired()) {

                    return voice;
                }

                if (fallback == null) {
                    fallback = voice;
                }
            }
        }

        return fallback;
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
                "লেখা শুনুন"
        );

        listenButton.setContentDescription(
                "লেখা শুনুন"
        );

        if (isSpeaking) {

            pauseButton.setText(
                    "ভয়েস বন্ধ করুন"
            );

            pauseButton.setContentDescription(
                    "ভয়েস বন্ধ করুন"
            );

        } else if (isPaused) {

            pauseButton.setText(
                    "ভয়েস চালু করুন"
            );

            pauseButton.setContentDescription(
                    "ভয়েস চালু করুন"
            );

        } else {

            pauseButton.setText(
                    "ভয়েস বন্ধ করুন"
            );

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
