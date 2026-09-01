package com.banglavoicevideo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String PREFS = "BanglaVoiceVideoSettings";
    private static final String PREF_LANGUAGE = "app_language";
    private static final String PREF_SPEED = "voice_speed";

    private TextToSpeech tts;
    private EditText textInput;
    private TextView statusText;
    private Button listenButton, pauseButton, settingsButton, exportAudioButton;

    private volatile boolean ttsReady = false;
    private volatile boolean speaking = false;
    private volatile boolean paused = false;

    private int currentPart = 0;
    private final List<String> speechParts = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean exportingAudio = false;
    private volatile int exportIndex = 0;
    private final List<String> exportParts = Collections.synchronizedList(new ArrayList<>());
    private final List<File> exportFiles = Collections.synchronizedList(new ArrayList<>());
    private File exportDir;

    private SharedPreferences preferences;
    private String appLanguage = "bn";
    private float speechRate = 0.95f;
    private final ExecutorService threadExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        appLanguage = preferences.getString(PREF_LANGUAGE, "bn");
        speechRate = preferences.getFloat(PREF_SPEED, 0.95f);

        createMainInterface();
        initializeTTS();
    }

    private void initializeTTS() {
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setSpeechRate(speechRate);
                tts.setPitch(1.0f);
                setupTTSListener();

                setStatus(getText("প্রস্তুত (অফলাইন)", "Ready (Offline)"));
                updateButtons();
            } else {
                ttsReady = false;
                setStatus(getText(
                        "ভয়েস সিস্টেম প্রস্তুত করা যায়নি",
                        "TTS could not be initialized"
                ));
            }
        });
    }

    private void setupTTSListener() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

            @Override
            public void onStart(String id) {
                runOnUiThread(() -> {
                    if (id != null && id.startsWith("part_")) {
                        speaking = true;
                        paused = false;
                        setStatus(getText("ভয়েস চলছে...", "Voice playing..."));
                        updateButtons();
                    }
                });
            }

            @Override
            public void onDone(String id) {
                runOnUiThread(() -> {
                    if (id != null && id.startsWith("export_")) {
                        if (exportingAudio) {
                            exportIndex++;
                            int percent = (int) (((float) exportIndex / exportParts.size()) * 100);
                            setStatus(getText("অডিও তৈরি হচ্ছে: " + percent + "%", "Creating audio: " + percent + "%"));

                            if (exportIndex < exportParts.size()) {
                                synthesizeNextExportPart();
                            } else {
                                mergeAndSaveExportedAudio();
                            }
                        }
                        return;
                    }

                    if (paused) return;

                    currentPart++;
                    if (currentPart < speechParts.size()) {
                        speakCurrentPart();
                    } else {
                        speaking = false;
                        paused = false;
                        currentPart = 0;
                        setStatus(getText("পড়া শেষ হয়েছে", "Reading finished"));
                        updateButtons();
                    }
                });
            }

            @Override
            public void onError(String id) {
                runOnUiThread(() -> {
                    if (id != null && id.startsWith("export_")) {
                        failAudioExport(getText(
                                "অডিও তৈরি করতে সমস্যা হয়েছে",
                                "Audio export failed"
                        ));
                        return;
                    }

                    speaking = false;
                    paused = false;
                    setStatus(getText(
                            "ভয়েস পড়তে সমস্যা হয়েছে",
                            "Voice playback error"
                    ));
                    updateButtons();
                });
            }
        });
    }

    private void createMainInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("BanglaVoiceVideo");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);

        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        textInput = new EditText(this);
        textInput.setHint(getText("এখানে বাংলা বা English লেখা লিখুন", "Write Bangla or English text here"));
        textInput.setTextSize(18);
        textInput.setTextColor(Color.BLACK);
        textInput.setHintTextColor(Color.GRAY);
        textInput.setGravity(Gravity.TOP | Gravity.START);
        textInput.setPadding(20, 20, 20, 20);
        textInput.setSingleLine(false);
        textInput.setMaxLines(Integer.MAX_VALUE);
        textInput.setVerticalScrollBarEnabled(true);
        textInput.setContentDescription(getText("বাংলা অথবা English লেখা লেখার ঘর", "Text input box for Bangla or English"));

        scroll.addView(textInput, new ScrollView.LayoutParams(-1, 500));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        listenButton = new Button(this);
        listenButton.setOnClickListener(v -> startReading());
        root.addView(listenButton);

        pauseButton = new Button(this);
        pauseButton.setOnClickListener(v -> pauseOrResume());
        root.addView(pauseButton);

        exportAudioButton = new Button(this);
        exportAudioButton.setOnClickListener(v -> checkPermissionAndExportAudio());
        root.addView(exportAudioButton);

        settingsButton = new Button(this);
        settingsButton.setText(getText("⚙ সেটিংস", "⚙ Settings"));
        settingsButton.setOnClickListener(v -> showSettings());
        root.addView(settingsButton);

        statusText = new TextView(this);
        statusText.setTextSize(17);
        statusText.setTextColor(Color.BLACK);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 15, 0, 5);
        root.addView(statusText);

        setContentView(root);
        updateButtons();
    }

    private void startReading() {
        if (!ttsReady) {
            setStatus(getText("ভয়েস সিস্টেম প্রস্তুত নয়", "TTS is not ready"));
            return;
        }

        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) {
            setStatus(getText("আগে কিছু লেখা লিখুন", "Please enter some text first"));
            return;
        }

        tts.stop();
        speechParts.clear();
        speechParts.addAll(splitForNaturalReading(text));

        currentPart = 0;
        paused = false;
        speaking = false;

        speakCurrentPart();
    }

    private List<String> splitForNaturalReading(String text) {
        List<String> result = new ArrayList<>();
        String clean = text.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();

        if (clean.isEmpty()) return result;

        String[] sentences = clean.split("(?<=[।!?])\\s+");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            if (current.length() == 0) {
                current.append(sentence);
            } else if (current.length() + sentence.length() + 1 <= 1500) {
                current.append(" ").append(sentence);
            } else {
                result.add(current.toString().trim());
                current.setLength(0);
                current.append(sentence);
            }
        }

        if (current.length() > 0) {
            result.add(current.toString().trim());
        }

        return result;
    }

    private void speakCurrentPart() {
        if (!ttsReady || currentPart < 0 || currentPart >= speechParts.size()) {
            return;
        }

        String part = speechParts.get(currentPart).trim();
        if (part.isEmpty()) {
            currentPart++;
            speakCurrentPart();
            return;
        }

        chooseOfflineLanguage(part);
        tts.setSpeechRate(speechRate);
        tts.setPitch(1.0f);

        tts.speak(part, TextToSpeech.QUEUE_FLUSH, null, "part_" + currentPart);
        speaking = true;
        paused = false;
        updateButtons();
    }

    private void pauseOrResume() {
        if (!ttsReady) return;

        if (speaking) {
            tts.stop();
            speaking = false;
            paused = true;
            setStatus(getText("ভয়েস বন্ধ আছে (Pause)", "Voice paused"));
            updateButtons();
        } else if (paused && currentPart < speechParts.size()) {
            speakCurrentPart();
        }
    }

    private void chooseOfflineLanguage(String text) {
        try {
            if (containsBangla(text)) {
                setOfflineVoice(new Locale("bn", "BD"));
            } else {
                setOfflineVoice(Locale.US);
            }
        } catch (Exception ignored) {}
    }

    private void setOfflineVoice(Locale locale) {
        if (tts == null) return;
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices != null && !voices.isEmpty()) {
                for (Voice voice : voices) {
                    if (voice.getLocale() != null &&
                            voice.getLocale().getLanguage().equalsIgnoreCase(locale.getLanguage())) {
                        if (!voice.isNetworkConnectionRequired()) {
                            tts.setVoice(voice);
                            return;
                        }
                    }
                }
            }
            tts.setLanguage(locale);
        } catch (Exception e) {
            try {
                tts.setLanguage(locale);
            } catch (Exception ignored) {}
        }
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

    private void updateButtons() {
        if (listenButton == null) return;

        listenButton.setText(getText("লেখা শুনুন", "Listen"));

        if (speaking) {
            pauseButton.setText(getText("ভয়েস থামান (Pause)", "Pause Voice"));
            pauseButton.setEnabled(true);
        } else if (paused) {
            pauseButton.setText(getText("পুনরায় চালু করুন (Resume)", "Resume Voice"));
            pauseButton.setEnabled(true);
        } else {
            pauseButton.setText(getText("ভয়েস থামান", "Pause Voice"));
            pauseButton.setEnabled(false);
        }

        exportAudioButton.setText(exportingAudio ? getText("অডিও তৈরি হচ্ছে...", "Creating audio...") : getText("অডিও এক্সপোর্ট", "Export Audio"));
        exportAudioButton.setEnabled(ttsReady && !exportingAudio);
    }

    private void checkPermissionAndExportAudio() {
        if (!hasEnoughStorage()) {
            setStatus(getText("ডিভাইসে পর্যাপ্ত মেমোরি খালি নেই", "Not enough storage space available"));
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                return;
            }
        }
        exportAudio();
    }

    private boolean hasEnoughStorage() {
        File statFile = getCacheDir();
        StatFs stat = new StatFs(statFile.getPath());
        long bytesAvailable = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        return bytesAvailable > (20 * 1024 * 1024);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportAudio();
            } else {
                setStatus(getText("পারমিশন প্রয়োজন অডিও ফাইল সেভ করতে", "Permission required to save audio file"));
            }
        }
    }

    private void exportAudio() {
        if (!ttsReady || exportingAudio) return;

        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) {
            setStatus(getText("আগে কিছু লেখা লিখুন", "Please enter some text first"));
            return;
        }

        exportParts.clear();
        exportParts.addAll(splitForNaturalReading(text));
        if (exportParts.isEmpty()) return;

        exportFiles.clear();
        exportIndex = 0;
        exportingAudio = true;

        exportDir = new File(getCacheDir(), "BanglaVoiceVideoExport_" + System.currentTimeMillis());
        if (!exportDir.mkdirs()) {
            exportingAudio = false;
            setStatus(getText("অডিও তৈরির স্থান তৈরি করা যায়নি", "Could not create audio folder"));
            updateButtons();
            return;
        }

        setStatus(getText("অডিও তৈরি হচ্ছে: 0%", "Creating audio: 0%"));
        updateButtons();
        synthesizeNextExportPart();
    }

    private void synthesizeNextExportPart() {
        if (!exportingAudio || exportIndex >= exportParts.size()) {
            return;
        }

        String part = exportParts.get(exportIndex).trim();
        File file = new File(exportDir, "part_" + exportIndex + ".wav");

        try {
            chooseOfflineLanguage(part);
            tts.setSpeechRate(speechRate);
            tts.setPitch(1.0f);

            int result = tts.synthesizeToFile(part, new Bundle(), file, "export_" + exportIndex);

            if (result != TextToSpeech.SUCCESS) {
                failAudioExport(getText("অডিও তৈরি করা যায়নি", "Could not create audio"));
                return;
            }

            exportFiles.add(file);
        } catch (Exception e) {
            failAudioExport(getText("অডিও তৈরির সময় সমস্যা হয়েছে", "Audio creation failed"));
        }
    }

    private void mergeAndSaveExportedAudio() {
        if (!exportingAudio || exportFiles.isEmpty()) {
            failAudioExport(getText("কোনো অডিও অংশ তৈরি হয়নি", "No audio parts were created"));
            return;
        }

        File merged = new File(exportDir, "complete.wav");

        threadExecutor.execute(() -> {
            try {
                mergeWavFiles(exportFiles, merged);
                saveExportedAudio(merged, createAudioFileName(textInput.getText().toString()));
            } catch (Exception e) {
                runOnUiThread(() -> failAudioExport(getText("অডিও ফাইল একত্রিত করতে সমস্যা হয়েছে", "Could not merge audio files")));
            }
        });
    }

    private void mergeWavFiles(List<File> files, File output) throws IOException {
        byte[] header = new byte[44];
        try (FileInputStream first = new FileInputStream(files.get(0))) {
            if (first.read(header) < 44) {
                throw new IOException("Invalid WAV header");
            }
        }

        long dataLength = 0;
        for (File f : files) {
            if (f.length() < 44) {
                throw new IOException("WAV part file too small");
            }
            dataLength += (f.length() - 44);
        }

        int channels = readShortLE(header, 22);
        int sampleRate = readIntLE(header, 24);
        int bits = readShortLE(header, 34);

        try (FileOutputStream out = new FileOutputStream(output)) {
            writeWavHeader(out, dataLength, sampleRate, channels, bits);
            byte[] buffer = new byte[8192];

            for (File f : files) {
                try (FileInputStream in = new FileInputStream(f)) {
                    long skip = 0;
                    while (skip < 44) {
                        long n = in.skip(44 - skip);
                        if (n <= 0) break;
                        skip += n;
                    }

                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    private int readIntLE(byte[] b, int o) {
        return (b[o] & 255) | ((b[o + 1] & 255) << 8) | ((b[o + 2] & 255) << 16) | ((b[o + 3] & 255) << 24);
    }

    private int readShortLE(byte[] b, int o) {
        return (b[o] & 255) | ((b[o + 1] & 255) << 8);
    }

    private void writeIntLE(FileOutputStream out, int value) throws IOException {
        out.write(value & 255);
        out.write((value >> 8) & 255);
        out.write((value >> 16) & 255);
        out.write((value >> 24) & 255);
    }

    private void writeShortLE(FileOutputStream out, int value) throws IOException {
        out.write(value & 255);
        out.write((value >> 8) & 255);
    }

    private void writeWavHeader(FileOutputStream out, long dataLength, int sampleRate, int channels, int bits) throws IOException {
        int byteRate = sampleRate * channels * bits / 8;
        int blockAlign = channels * bits / 8;

        out.write(new byte[]{'R', 'I', 'F', 'F'});
        writeIntLE(out, (int) Math.min(36 + dataLength, Integer.MAX_VALUE));
        out.write(new byte[]{'W', 'A', 'V', 'E'});
        out.write(new byte[]{'f', 'm', 't', ' '});
        writeIntLE(out, 16);
        writeShortLE(out, 1);
        writeShortLE(out, channels);
        writeIntLE(out, sampleRate);
        writeIntLE(out, byteRate);
        writeShortLE(out, blockAlign);
        writeShortLE(out, bits);
        out.write(new byte[]{'d', 'a', 't', 'a'});
        writeIntLE(out, (int) Math.min(dataLength, Integer.MAX_VALUE));
    }

    private String createAudioFileName(String text) {
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) return "BanglaVoiceVideo_Audio";

        String[] words = clean.split(" ");
        StringBuilder name = new StringBuilder();

        for (String word : words) {
            String safe = word.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "");
            if (safe.isEmpty()) continue;

            if (name.length() > 0) name.append("_");
            name.append(safe);

            if (name.length() >= 30) break;
        }

        return name.length() == 0 ? "BanglaVoiceVideo_Audio" : name.toString();
    }

    private void saveExportedAudio(File file, String name) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.DISPLAY_NAME, name + ".wav");
                values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
                values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC);
                values.put(MediaStore.Audio.Media.IS_PENDING, 1);

                Uri uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Failed to create MediaStore entry");

                try (FileInputStream in = new FileInputStream(file);
                     OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("OutputStream opening failed");
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                }

                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Audio.Media.IS_PENDING, 0);
                getContentResolver().update(uri, ready, null, null);

            } else {
                File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                if (!musicDir.exists() && !musicDir.mkdirs()) {
                    throw new IOException("Cannot access Music directory");
                }

                File destFile = new File(musicDir, name + ".wav");
                try (FileInputStream in = new FileInputStream(file);
                     FileOutputStream out = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                }
            }

            runOnUiThread(() -> {
                exportingAudio = false;
                setStatus(getText("অডিও সফলভাবে সেভ হয়েছে: Music ফোল্ডারে", "Audio saved: Music folder"));
                updateButtons();
                cleanExportFiles();
            });

        } catch (Exception e) {
            runOnUiThread(() -> failAudioExport(getText("অডিও সংরক্ষণ করা যায়নি", "Could not save audio")));
        }
    }

    private void failAudioExport(String message) {
        exportingAudio = false;
        setStatus(message);
        updateButtons();
        cleanExportFiles();
    }

    private void cleanExportFiles() {
        try {
            if (exportDir != null && exportDir.exists()) {
                File[] files = exportDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        try { f.delete(); } catch (Exception ignored) {}
                    }
                }
                exportDir.delete();
            }
        } catch (Exception ignored) {}

        exportFiles.clear();
        exportParts.clear();
    }

    private void showSettings() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        TextView heading = new TextView(this);
        heading.setText(getText("সেটিংস", "Settings"));
        heading.setTextSize(24);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(Color.BLACK);
        layout.addView(heading);

        Button languageButton = new Button(this);
        languageButton.setText(getText("অ্যাপের ভাষা", "App Language"));
        languageButton.setOnClickListener(v -> showLanguageDialog());
        layout.addView(languageButton);

        TextView speedLabel = new TextView(this);
        speedLabel.setText(getText("ভয়েসের গতি", "Voice Speed"));
        speedLabel.setTextSize(18);
        speedLabel.setTextColor(Color.BLACK);
        speedLabel.setPadding(0, 15, 0, 5);
        layout.addView(speedLabel);

        Spinner speedSpinner = new Spinner(this);
        String[] speeds = {
                getText("ধীর", "Slow"),
                getText("স্বাভাবিক", "Normal"),
                getText("দ্রুত", "Fast"),
                getText("খুব দ্রুত", "Very Fast")
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, speeds);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speedSpinner.setAdapter(adapter);

        int selected = 1;
        if (speechRate <= 0.85f) selected = 0;
        else if (speechRate >= 1.25f) selected = 3;
        else if (speechRate >= 1.10f) selected = 2;

        speedSpinner.setSelection(selected);
        speedSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) speechRate = 0.85f;
                else if (position == 1) speechRate = 0.95f;
                else if (position == 2) speechRate = 1.10f;
                else speechRate = 1.25f;

                preferences.edit().putFloat(PREF_SPEED, speechRate).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        layout.addView(speedSpinner);

        Button aboutButton = new Button(this);
        aboutButton.setText(getText("ℹ About", "ℹ About"));
        aboutButton.setOnClickListener(v -> showAbout());
        layout.addView(aboutButton);

        new AlertDialog.Builder(this)
                .setView(layout)
                .setPositiveButton(getText("বন্ধ করুন", "Close"), null)
                .show();
    }

    private void showLanguageDialog() {
        String[] languages = {"বাংলা", "English"};
        int checked = "en".equals(appLanguage) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle(getText("অ্যাপের ভাষা", "App Language"))
                .setSingleChoiceItems(languages, checked, (dialog, which) -> {
                    appLanguage = which == 1 ? "en" : "bn";
                    preferences.edit().putString(PREF_LANGUAGE, appLanguage).apply();
                    dialog.dismiss();
                    refreshInterfaceLanguage();
                })
                .setNegativeButton(getText("বাতিল", "Cancel"), null)
                .show();
    }

    private void refreshInterfaceLanguage() {
        if (textInput != null) {
            textInput.setHint(getText("এখানে বাংলা বা English লেখা লিখুন", "Write Bangla or English text here"));
            textInput.setContentDescription(getText("বাংলা অথবা English লেখা লেখার ঘর", "Text input box for Bangla or English"));
        }

        if (listenButton != null) {
            listenButton.setText(getText("লেখা শুনুন", "Listen"));
        }

        if (settingsButton != null) {
            settingsButton.setText(getText("⚙ সেটিংস", "⚙ Settings"));
        }

        if (exportAudioButton != null) {
            exportAudioButton.setText(exportingAudio ? getText("অডিও তৈরি হচ্ছে...", "Creating audio...") : getText("অডিও এক্সপোর্ট", "Export Audio"));
        }

        updateButtons();
    }

    private void showAbout() {
        String about = getText(
                "BanglaVoiceVideo\n\nবাংলা ও English লেখা থেকে সম্পূর্ণ অফলাইনে ভয়েস তৈরি করা যায়। পরবর্তী ধাপে ভিডিও ফিচার যুক্ত হবে।",
                "BanglaVoiceVideo\n\nCreates voice offline from Bangla and English text. Video generation features will be added next."
        );

        TextView view = new TextView(this);
        view.setText(about);
        view.setTextSize(17);
        view.setTextColor(Color.BLACK);
        view.setPadding(25, 20, 25, 20);

        new AlertDialog.Builder(this)
                .setTitle("About")
                .setView(view)
                .setPositiveButton(getText("বন্ধ করুন", "Close"), null)
                .show();
    }

    private String getText(String bangla, String english) {
        return "en".equals(appLanguage) ? english : bangla;
    }

    private void setStatus(String message) {
        if (statusText != null) {
            statusText.setText(getText("বর্তমান অবস্থা: ", "Current status: ") + message);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tts != null && speaking) {
            tts.stop();
            speaking = false;
            paused = true;
            updateButtons();
        }
    }

    @Override
    protected void onDestroy() {
        exportingAudio = false;
        cleanExportFiles();

        if (threadExecutor != null && !threadExecutor.isShutdown()) {
            threadExecutor.shutdownNow();
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }
}
