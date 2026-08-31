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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

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
    private Button exportButton;

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

    /* Audio export state */
    private boolean exporting = false;
    private int exportIndex = 0;

    private List<String> exportParts =
            new ArrayList<>();

    private List<File> exportFiles =
            new ArrayList<>();

    private File exportDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        preferences =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        appLanguage =
                preferences.getString(
                        PREF_LANGUAGE,
                        "bn"
                );

        speechRate =
                preferences.getFloat(
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

                    if (result ==
                            TextToSpeech.SUCCESS) {

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

                            if (utteranceId != null
                                    && utteranceId.startsWith(
                                    "export_")) {
                                return;
                            }

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

                            /*
                             * Audio export completion
                             */
                            if (utteranceId != null
                                    && utteranceId.startsWith(
                                    "export_")) {

                                if (!exporting) {
                                    return;
                                }

                                exportIndex++;

                                if (exportIndex >=
                                        exportParts.size()) {

                                    finishAudioExport();

                                } else {

                                    synthesizeNextExportPart();
                                }

                                return;
                            }

                            /*
                             * Normal reading
                             */
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

                            if (utteranceId != null
                                    && utteranceId.startsWith(
                                    "export_")) {

                                failAudioExport(
                                        getText(
                                                "অডিও তৈরি করতে সমস্যা হয়েছে",
                                                "Audio export failed"
                                        )
                                );

                                return;
                            }

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

        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(true);

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

        scroll.addView(
                textInput,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        500
                )
        );

        root.addView(
                scroll,
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

        exportButton =
                new Button(this);

        exportButton.setText(
                getText(
                        "অডিও এক্সপোর্ট",
                        "Export Audio"
                )
        );

        exportButton.setContentDescription(
                getText(
                        "সম্পূর্ণ লেখা অডিও ফাইলে সংরক্ষণ করুন",
                        "Save the complete text as an audio file"
                )
        );

        exportButton.setOnClickListener(
                v -> exportAudio()
        );

        root.addView(
                exportButton,
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

        try {
            tts.stop();
        } catch (Exception ignored) {
        }

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

            try {
                tts.stop();
            } catch (Exception ignored) {
            }

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

        try {

            tts.speak(
                    part,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "part_" + currentPart
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
     * Normal reading:
     * sentence punctuation is preferred instead of
     * cutting every 8 words.
     */
    private List<String> splitText(
            String text) {

        ArrayList<String> result =
                new ArrayList<>();

        String normalized =
                text.replace(
                        '\n',
                        ' '
                ).replace(
                        '\r',
                        ' '
                ).replaceAll(
                        "\\s+",
                        " "
                ).trim();

        if (normalized.isEmpty()) {
            return result;
        }

        String[] sentences =
                normalized.split(
                        "(?<=[।|,.?!?])\\s+"
                );

        StringBuilder current =
                new StringBuilder();

        for (String sentence :
                sentences) {

            sentence =
                    sentence.trim();

            if (sentence.isEmpty()) {
                continue;
            }

            if (current.length() == 0) {

                current.append(
                        sentence
                );

            } else if (
                    current.length()
                            + sentence.length()
                            + 1 <= 3000) {

                current.append(" ")
                        .append(sentence);

            } else {

                result.addAll(
                        splitLongText(
                                current.toString(),
                                3000
                        )
                );

                current.setLength(0);

                current.append(sentence);
            }
        }

        if (current.length() > 0) {

            result.addAll(
                    splitLongText(
                            current.toString(),
                            3000
                    )
            );
        }

        return result;
    }

    private List<String> splitLongText(
            String text,
            int maxChars) {

        ArrayList<String> result =
                new ArrayList<>();

        String[] words =
                text.split("\\s+");

        StringBuilder current =
                new StringBuilder();

        for (String word :
                words) {

            if (word.isEmpty()) {
                continue;
            }

            if (current.length() > 0
                    && current.length()
                    + word.length()
                    + 1 > maxChars) {

                result.add(
                        current.toString()
                                .trim()
                );

                current.setLength(0);
            }

            if (current.length() > 0) {
                current.append(" ");
            }

            current.append(word);
        }

        if (current.length() > 0) {

            result.add(
                    current.toString()
                            .trim()
            );
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

                tts.setVoice(
                        fallback
                );

            } else {

                tts.setLanguage(
                        locale
                );
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

            if (c >= '\u0980'
                    && c <= '\u09FF') {

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

            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a'
                    && c <= 'z')) {

                return true;
            }
        }

        return false;
    }

    /*
     * ============================
     * COMPLETE AUDIO EXPORT
     * ============================
     */

    private void exportAudio() {

        if (!ttsReady) {

            setStatus(
                    getText(
                            "ভয়েস সিস্টেম প্রস্তুত নয়",
                            "TTS is not ready"
                    )
            );

            return;
        }

        if (exporting) {

            setStatus(
                    getText(
                            "অডিও তৈরি হচ্ছে, অপেক্ষা করুন",
                            "Audio export is already running"
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

        exportParts =
                splitText(text);

        if (exportParts.isEmpty()) {
            return;
        }

        exportDirectory =
                new File(
                        getCacheDir(),
                        "BanglaVoiceVideoExport_"
                                + System.currentTimeMillis()
                );

        if (!exportDirectory.mkdirs()) {

            setStatus(
                    getText(
                            "অডিও তৈরির স্থান তৈরি করা যায়নি",
                            "Could not create export folder"
                    )
            );

            return;
        }

        exportFiles.clear();

        exportIndex = 0;
        exporting = true;

        setStatus(
                getText(
                        "সম্পূর্ণ অডিও তৈরি হচ্ছে...",
                        "Creating complete audio..."
                )
        );

        updateButtons();

        synthesizeNextExportPart();
    }

    private void synthesizeNextExportPart() {

        if (!exporting) {
            return;
        }

        if (exportIndex >=
                exportParts.size()) {

            finishAudioExport();

            return;
        }

        String part =
                exportParts
                        .get(exportIndex)
                        .trim();

        if (part.isEmpty()) {

            exportIndex++;

            synthesizeNextExportPart();

            return;
        }

        File output =
                new File(
                        exportDirectory,
                        "part_"
                                + exportIndex
                                + ".wav"
                );

        try {

            chooseLanguage(part);

            int result =
                    tts.synthesizeToFile(
                            part,
                            new Bundle(),
                            output,
                            "export_"
                                    + exportIndex
                    );

            if (result !=
                    TextToSpeech.SUCCESS) {

                failAudioExport(
                        getText(
                                "অডিও তৈরি করা যায়নি",
                                "Could not create audio"
                        )
                );

                return;
            }

            exportFiles.add(output);

        } catch (Exception e) {

            failAudioExport(
                    getText(
                            "অডিও তৈরির সময় সমস্যা হয়েছে",
                            "An error occurred while creating audio"
                    )
            );
        }
    }

    private void finishAudioExport() {

        if (!exporting) {
            return;
        }

        try {

            if (exportFiles.isEmpty()) {

                failAudioExport(
                        getText(
                                "কোনো অডিও তৈরি হয়নি",
                                "No audio was created"
                        )
                );

                return;
            }

            String name =
                    createAudioName(
                            textInput
                                    .getText()
                                    .toString()
                    );

            File merged =
                    new File(
                            exportDirectory,
                            name + ".wav"
                    );

            mergeWavFiles(
                    exportFiles,
                    merged
            );

            saveAudio(
                    merged,
                    name
            );

        } catch (Exception e) {

            failAudioExport(
                    getText(
                            "অডিও ফাইল তৈরি করা যায়নি",
                            "Could not create audio file"
                    )
            );
        }
    }

    private String createAudioName(
            String text) {

        String clean =
                text.replaceAll(
                        "\\s+",
                        " "
                ).trim();

        if (clean.isEmpty()) {

            return "BanglaVoiceVideo_Audio";
        }

        String[] words =
                clean.split(" ");

        StringBuilder name =
                new StringBuilder();

        for (String word :
                words) {

            String safe =
                    word.replaceAll(
                            "[\\\\/:*?\"<>|\\p{Cntrl}]",
                            ""
                    );

            if (safe.isEmpty()) {
                continue;
            }

            if (name.length() > 0) {
                name.append("_");
            }

            name.append(safe);

            if (name.length() >= 45) {
                break;
            }
        }

        if (name.length() == 0) {

            return "BanglaVoiceVideo_Audio";
        }

        return name.substring(
                0,
                Math.min(
                        name.length(),
                        50
                )
        );
    }

    private void mergeWavFiles(
            List<File> files,
            File output)
            throws IOException {

        if (files == null
                || files.isEmpty()) {

            throw new IOException(
                    "No WAV files"
            );
        }

        byte[] header =
                new byte[44];

        try (FileInputStream in =
                     new FileInputStream(
                             files.get(0)
                     )) {

            if (in.read(header) != 44) {

                throw new IOException(
                        "Invalid WAV"
                );
            }
        }

        if (header[0] != 'R'
                || header[1] != 'I'
                || header[2] != 'F'
                || header[3] != 'F'
                || header[8] != 'W'
                || header[9] != 'A'
                || header[10] != 'V'
                || header[11] != 'E') {

            throw new IOException(
                    "Invalid WAV header"
            );
        }

        int channels =
                readShortLE(
                        header,
                        22
                );

        int sampleRate =
                readIntLE(
                        header,
                        24
                );

        int bits =
                readShortLE(
                        header,
                        34
                );

        long dataLength = 0;

        for (File file : files) {

            if (file.length() < 44) {

                throw new IOException(
                        "Invalid WAV part"
                );
            }

            dataLength +=
                    file.length() - 44;
        }

        try (FileOutputStream out =
                     new FileOutputStream(
                             output
                     )) {

            writeWavHeader(
                    out,
                    dataLength,
                    sampleRate,
                    channels,
                    bits
            );

            byte[] buffer =
                    new byte[8192];

            for (File file : files) {

                try (FileInputStream in =
                             new FileInputStream(
                                     file
                             )) {

                    long skipped = 0;

                    while (skipped < 44) {

                        long n =
                                in.skip(
                                        44 - skipped
                                );

                        if (n <= 0) {
                            break;
                        }

                        skipped += n;
                    }

                    int count;

                    while ((count =
                            in.read(buffer))
                            != -1) {

                        out.write(
                                buffer,
                                0,
                                count
                        );
                    }
                }
            }
        }
    }

    private int readIntLE(
            byte[] data,
            int offset) {

        return (data[offset] & 0xff)
                | ((data[offset + 1]
                & 0xff) << 8)
                | ((data[offset + 2]
                & 0xff) << 16)
                | ((data[offset + 3]
                & 0xff) << 24);
    }

    private int readShortLE(
            byte[] data,
            int offset) {

        return (data[offset] & 0xff)
                | ((data[offset + 1]
                & 0xff) << 8);
    }

    private void writeWavHeader(
            FileOutputStream out,
            long dataLength,
            int sampleRate,
            int channels,
            int bits)
            throws IOException {

        int byteRate =
                sampleRate
                        * channels
                        * bits
                        / 8;

        int blockAlign =
                channels
                        * bits
                        / 8;

        out.write(
                new byte[]{
                        'R','I','F','F'
                }
        );

        writeIntLE(
                out,
                (int)(36 + dataLength)
        );

        out.write(
                new byte[]{
                        'W','A','V','E',
                        'f','m','t',' '
                }
        );

        writeIntLE(
                out,
                16
        );

        writeShortLE(
                out,
                1
        );

        writeShortLE(
                out,
                channels
        );

        writeIntLE(
                out,
                sampleRate
        );

        writeIntLE(
                out,
                byteRate
        );

        writeShortLE(
                out,
                blockAlign
        );

        writeShortLE(
                out,
                bits
        );

        out.write(
                new byte[]{
                        'd','a','t','a'
                }
        );

        writeIntLE(
                out,
                (int)dataLength
        );
    }

    private void writeIntLE(
            FileOutputStream out,
            int value)
            throws IOException {

        out.write(
                value & 0xff
        );

        out.write(
                (value >> 8)
                        & 0xff
        );

        out.write(
                (value >> 16)
                        & 0xff
        );

        out.write(
                (value >> 24)
                        & 0xff
        );
    }

    private void writeShortLE(
            FileOutputStream out,
            int value)
            throws IOException {

        out.write(
                value & 0xff
        );

        out.write(
                (value >> 8)
                        & 0xff
        );
    }

    private void saveAudio(
            File source,
            String displayName) {

        new Thread(() -> {

            try {

                if (Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.Q) {

                    ContentValues values =
                            new ContentValues();

                    values.put(
                            MediaStore.Audio.Media
                                    .DISPLAY_NAME,
                            displayName
                                    + ".wav"
                    );

                    values.put(
                            MediaStore.Audio.Media
                                    .MIME_TYPE,
                            "audio/wav"
                    );

                    values.put(
                            MediaStore.Audio.Media
                                    .RELATIVE_PATH,
                            "Music/BanglaVoiceVideo"
                    );

                    values.put(
                            MediaStore.Audio.Media
                                    .IS_PENDING,
                            1
                    );

                    Uri uri =
                            getContentResolver()
                                    .insert(
                                            MediaStore.Audio.Media
                                                    .EXTERNAL_CONTENT_URI,
                                            values
                                    );

                    if (uri == null) {

                        throw new IOException(
                                "MediaStore insert failed"
                        );
                    }

                    try (
                            FileInputStream in =
                                    new FileInputStream(
                                            source
                                    );

                            java.io.OutputStream out =
                                    getContentResolver()
                                            .openOutputStream(
                                                    uri
                                            )
                    ) {

                        if (out == null) {

                            throw new IOException(
                                    "Output unavailable"
                            );
                        }

                        byte[] buffer =
                                new byte[8192];

                        int count;

                        while ((count =
                                in.read(buffer))
                                != -1) {

                            out.write(
                                    buffer,
                                    0,
                                    count
                            );
                        }
                    }

                    ContentValues done =
                            new ContentValues();

                    done.put(
                            MediaStore.Audio.Media
                                    .IS_PENDING,
                            0
                    );

                    getContentResolver()
                            .update(
                                    uri,
                                    done,
                                    null,
                                    null
                            );

                } else {

                    File music =
                            android.os.Environment
                                    .getExternalStoragePublicDirectory(
                                            android.os.Environment
                                                    .DIRECTORY_MUSIC
                                    );

                    File folder =
                            new File(
                                    music,
                                    "BanglaVoiceVideo"
                            );

                    if (!folder.exists()
                            && !folder.mkdirs()) {

                        throw new IOException(
                                "Could not create folder"
                        );
                    }

                    copyFile(
                            source,
                            new File(
                                    folder,
                                    displayName
                                            + ".wav"
                            )
                    );
                }

                runOnUiThread(() -> {

                    exporting = false;

                    setStatus(
                            getText(
                                    "অডিও সফলভাবে সংরক্ষণ হয়েছে",
                                    "Audio saved successfully"
                            )
                    );

                    updateButtons();

                    cleanExportFiles();
                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        failAudioExport(
                                getText(
                                        "অডিও সংরক্ষণ করা যায়নি",
                                        "Could not save audio"
                                )
                        )
                );
            }

        }).start();
    }

    private void copyFile(
            File source,
            File destination)
            throws IOException {

        try (
                FileInputStream in =
                        new FileInputStream(
                                source
                        );

                FileOutputStream out =
                        new FileOutputStream(
                                destination
                        )
        ) {

            byte[] buffer =
                    new byte[8192];

            int count;

            while ((count =
                    in.read(buffer))
                    != -1) {

                out.write(
                        buffer,
                        0,
                        count
                );
            }
        }
    }

    private void failAudioExport(
            String message) {

        exporting = false;

        setStatus(message);

        updateButtons();

        cleanExportFiles();
    }

    private void cleanExportFiles() {

        try {

            if (exportDirectory != null
                    && exportDirectory.exists()) {

                File[] files =
                        exportDirectory
                                .listFiles();

                if (files != null) {

                    for (File file :
                            files) {

                        try {
                            file.delete();
                        } catch (Exception ignored) {
                        }
                    }
                }

                exportDirectory.delete();
            }

        } catch (Exception ignored) {
        }

        exportFiles.clear();
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

        String[] languages = {
                "বাংলা",
                "English"
        };

        Spinner languageSpinner =
                new Spinner(this);

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

        languageSpinner
                .setAdapter(
                        languageAdapter
                );

        languageSpinner.setSelection(
                "en".equals(appLanguage)
                        ? 1
                        : 0
        );

        languageSpinner.setContentDescription(
                getText(
                        "অ্যাপের ভাষা নির্বাচন করুন",
                        "Select app language"
                )
        );

        languageSpinner
                .setOnItemSelectedListener(
                        new android.widget
                                .AdapterView
                                .OnItemSelectedListener() {

                            @Override
                            public void onItemSelected(
                                    android.widget
                                            .AdapterView<?> parent,
                                    View view,
                                    int position,
                                    long id) {

                                String newLanguage =
                                        position == 0
                                                ? "bn"
                                                : "en";

                                if (!newLanguage
                                        .equals(
                                                appLanguage
                                        )) {

                                    appLanguage =
                                            newLanguage;

                                    preferences
                                            .edit()
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
                                    android.widget
                                            .AdapterView<?> parent) {
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

        String[] speeds = {
                getText("ধীর", "Slow"),
                getText("স্বাভাবিক", "Normal"),
                getText("দ্রুত", "Fast"),
                getText("খুব দ্রুত", "Very Fast")
        };

        Spinner speedSpinner =
                new Spinner(this);

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
                        "ভয়েসের গতি নির্বাচন করুন",
                        "Select voice speed"
                )
        );

        speedSpinner
                .setOnItemSelectedListener(
                        new android.widget
                                .AdapterView
                                .OnItemSelectedListener() {

                            @Override
                            public void onItemSelected(
                                    android.widget
                                            .AdapterView<?> parent,
                                    View view,
                                    int position,
                                    long id) {

                                if (position == 0) {
                                    speechRate = 0.85f;
                                } else if (
                                        position == 1) {
                                    speechRate = 1.0f;
                                } else if (
                                        position == 2) {
                                    speechRate = 1.15f;
                                } else {
                                    speechRate = 1.30f;
                                }

                                preferences
                                        .edit()
                                        .putFloat(
                                                PREF_SPEED,
                                                speechRate
                                        )
                                        .apply();

                                if (tts != null
                                        && ttsReady) {

                                    tts.setSpeechRate(
                                            speechRate
                                    );
                                }
                            }

                            @Override
                            public void onNothingSelected(
                                    android.widget
                                            .AdapterView<?> parent) {
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
                .show();
    }

    private void showAbout() {

        String aboutText =
                getText(
                        "BanglaVoiceVideo\n\n"
                                + "বাংলা ও English লেখা থেকে "
                                + "ভয়েস তৈরি করার জন্য এই অ্যাপটি তৈরি করা হচ্ছে।\n\n"
                                + "দৃষ্টি প্রতিবন্ধী ব্যবহারকারীদের "
                                + "জন্য TalkBack সহায়ক রাখার লক্ষ্য রয়েছে।",
                        "BanglaVoiceVideo\n\n"
                                + "This app is being developed to "
                                + "create voice from Bangla and English text.\n\n"
                                + "The goal is to keep the app accessible "
                                + "for visually impaired users, including TalkBack."
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
                        "About"
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

    private void updateButtons() {

        if (listenButton == null
                || pauseButton == null) {

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

        if (exportButton != null) {

            exportButton.setText(
                    getText(
                            "অডিও এক্সপোর্ট",
                            "Export Audio"
                    )
            );

            exportButton.setEnabled(
                    ttsReady && !exporting
            );
        }
    }

    @Override
    protected void onDestroy() {

        exporting = false;

        cleanExportFiles();

        if (tts != null) {

            try {
                tts.stop();
            } catch (Exception ignored) {
            }

            try {
                tts.shutdown();
            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }
}
``` [❶](code://python)
