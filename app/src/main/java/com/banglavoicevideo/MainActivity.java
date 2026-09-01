package com.banglavoicevideo;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {

    private TextToSpeech tts;
    private EditText textInput;
    private TextView statusText;
    private Button listenButton, pauseButton, settingsButton, exportAudioButton;

    private boolean ttsReady = false;
    private boolean speaking = false;
    private boolean paused = false;
    private boolean backgroundReading = false;
    private boolean exportingAudio = false;

    private int currentPart = 0;
    private final ArrayList<String> speechParts = new ArrayList<>();
    private final ArrayList<String> exportParts = new ArrayList<>();
    private final ArrayList<File> exportFiles = new ArrayList<>();

    private File exportDirectory;
    private int exportIndex = 0;

    private SharedPreferences preferences;

    private static final String PREFS = "BanglaVoiceVideoSettings";
    private static final String PREF_LANGUAGE = "app_language";
    private static final String PREF_SPEED = "voice_speed";

    private String appLanguage = "bn";
    private float speechRate = 1.0f;

    private static final int WORDS_PER_PART = 8;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        appLanguage = preferences.getString(PREF_LANGUAGE, "bn");
        speechRate = preferences.getFloat(PREF_SPEED, 1.0f);

        createInterface();
        initializeTTS();
    }

    private void initializeTTS() {
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setSpeechRate(speechRate);
                tts.setPitch(1.0f);
                setupListener();
                setStatus(getText("প্রস্তুত", "Ready"));
                updateButtons();
            } else {
                ttsReady = false;
                setStatus(getText(
                        "ভয়েস সিস্টেম প্রস্তুত করা যায়নি",
                        "TTS could not be initialized"));
            }
        });
    }

    private void setupListener() {
        tts.setOnUtteranceProgressListener(
                new UtteranceProgressListener() {

            @Override
            public void onStart(String id) {
                runOnUiThread(() -> {
                    if (id != null && id.startsWith("part_")) {
                        speaking = true;
                        paused = false;
                        setStatus(getText(
                                "ভয়েস চলছে",
                                "Voice is playing"));
                        updateButtons();
                    }
                });
            }

            @Override
            public void onDone(String id) {
                runOnUiThread(() -> {

                    if (id != null && id.startsWith("export_")) {
                        if (!exportingAudio) return;

                        exportIndex++;

                        if (exportIndex >= exportParts.size()) {
                            finishAudioExport();
                        } else {
                            synthesizeNextExportPart();
                        }
                        return;
                    }

                    if (backgroundReading) return;
                    if (paused) return;

                    currentPart++;

                    if (currentPart < speechParts.size()) {
                        speakCurrentPart();
                    } else {
                        speaking = false;
                        paused = false;
                        currentPart = 0;

                        setStatus(getText(
                                "পড়া শেষ হয়েছে",
                                "Reading finished"));
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
                                "Audio export failed"));
                        return;
                    }

                    speaking = false;
                    paused = false;

                    setStatus(getText(
                            "ভয়েস পড়তে সমস্যা হয়েছে",
                            "Voice playback error"));
                    updateButtons();
                });
            }
        });
    }

    private void createInterface() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("BanglaVoiceVideo");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0,0,0,20);

        root.addView(title,
                new LinearLayout.LayoutParams(-1,-2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        textInput = new EditText(this);
        textInput.setHint(getText(
                "এখানে বাংলা বা English লেখা লিখুন",
                "Write Bangla or English text here"));
        textInput.setTextSize(18);
        textInput.setTextColor(Color.BLACK);
        textInput.setHintTextColor(Color.GRAY);
        textInput.setGravity(Gravity.TOP | Gravity.START);
        textInput.setPadding(20,20,20,20);
        textInput.setSingleLine(false);
        textInput.setMaxLines(Integer.MAX_VALUE);
        textInput.setVerticalScrollBarEnabled(true);
        textInput.setContentDescription(getText(
                "বাংলা অথবা English লেখা লেখার ঘর",
                "Text input box for Bangla or English"));

        scroll.addView(textInput,
                new ScrollView.LayoutParams(-1,500));

        root.addView(scroll,
                new LinearLayout.LayoutParams(-1,0,1));

        listenButton = new Button(this);
        listenButton.setText(getText("লেখা শুনুন","Listen"));
        listenButton.setOnClickListener(v -> startReading());
        root.addView(listenButton);

        pauseButton = new Button(this);
        pauseButton.setOnClickListener(v -> pauseOrResume());
        root.addView(pauseButton);

        settingsButton = new Button(this);
        settingsButton.setText(getText("⚙ সেটিংস","⚙ Settings"));
        settingsButton.setOnClickListener(v -> showSettings());
        root.addView(settingsButton);

        exportAudioButton = new Button(this);
        exportAudioButton.setText(
                getText("অডিও এক্সপোর্ট","Export Audio"));
        exportAudioButton.setContentDescription(
                getText(
                        "সম্পূর্ণ লেখা অডিও ফাইলে সংরক্ষণ করুন",
                        "Save the complete text as an audio file"));
        exportAudioButton.setOnClickListener(v -> exportAudio());
        root.addView(exportAudioButton);

        statusText = new TextView(this);
        statusText.setTextSize(17);
        statusText.setTextColor(Color.BLACK);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0,15,0,5);
        root.addView(statusText);

        setContentView(root);
        updateButtons();
    }

    private void startReading() {

        if (!ttsReady) {
            setStatus(getText(
                    "ভয়েস সিস্টেম প্রস্তুত নয়",
                    "TTS is not ready"));
            return;
        }

        String text = textInput.getText().toString().trim();

        if (text.isEmpty()) {
            setStatus(getText(
                    "আগে কিছু লেখা লিখুন",
                    "Please enter some text first"));
            return;
        }

        stopBackgroundService();

        tts.stop();

        speechParts.clear();
        speechParts.addAll(splitText(text));

        currentPart = 0;
        paused = false;
        speaking = false;
        backgroundReading = false;

        speakCurrentPart();
    }

    private void speakCurrentPart() {

        if (!ttsReady ||
                currentPart < 0 ||
                currentPart >= speechParts.size()) return;

        String part = cleanTextForSpeech(
                speechParts.get(currentPart));

        if (part.isEmpty()) {
            currentPart++;
            speakCurrentPart();
            return;
        }

        chooseLanguage(part);

        tts.setSpeechRate(speechRate);
        tts.setPitch(1.0f);

        tts.speak(
                part,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "part_" + currentPart
        );

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

            setStatus(getText(
                    "ভয়েস বন্ধ আছে",
                    "Voice is paused"));

            updateButtons();
            return;
        }

        if (paused &&
                currentPart < speechParts.size()) {

            paused = false;
            speakCurrentPart();
        }
    }

    private ArrayList<String> splitText(String text) {

        ArrayList<String> result = new ArrayList<>();

        String clean = cleanTextForSpeech(text);
        String[] words = clean.split("\\s+");

        StringBuilder part = new StringBuilder();
        int count = 0;

        for (String word : words) {

            if (word.isEmpty()) continue;

            if (count >= WORDS_PER_PART) {

                if (part.length() > 0)
                    result.add(part.toString().trim());

                part.setLength(0);
                count = 0;
            }

            if (part.length() > 0)
                part.append(" ");

            part.append(word);
            count++;
        }

        if (part.length() > 0)
            result.add(part.toString().trim());

        return result;
    }

    private String cleanTextForSpeech(String text) {

        if (text == null) return "";

        return text
                .replaceAll("[*_#@|~^`]+"," ")
                .replaceAll("[\\[\\]{}<>]+"," ")
                .replaceAll("\\s+"," ")
                .trim();
    }

    private void chooseLanguage(String text) {

        try {

            if (containsBangla(text)) {
                setBestVoice(new Locale("bn","BD"));
            } else {
                setBestVoice(Locale.US);
            }

        } catch (Exception ignored) {}
    }

    private void setBestVoice(Locale locale) {

        if (tts == null) return;

        try {

            Set<Voice> voices = tts.getVoices();

            if (voices != null) {

                Voice fallback = null;

                for (Voice voice : voices) {

                    Locale vl = voice.getLocale();

                    if (vl == null) continue;

                    if (vl.getLanguage()
                            .equalsIgnoreCase(
                                    locale.getLanguage())) {

                        if (!voice.isNetworkConnectionRequired()) {
                            tts.setVoice(voice);
                            return;
                        }

                        if (fallback == null)
                            fallback = voice;
                    }
                }

                if (fallback != null) {
                    tts.setVoice(fallback);
                    return;
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

        for (int i=0;i<text.length();i++) {

            char c=text.charAt(i);

            if (c >= '\u0980' && c <= '\u09FF')
                return true;
        }

        return false;
    }

    private void updateButtons() {

        if (listenButton == null) return;

        listenButton.setText(
                getText("লেখা শুনুন","Listen"));

        if (speaking) {

            pauseButton.setText(
                    getText(
                            "ভয়েস বন্ধ করুন",
                            "Pause Voice"));

            pauseButton.setEnabled(true);

        } else if (paused) {

            pauseButton.setText(
                    getText(
                            "ভয়েস চালু করুন",
                            "Resume Voice"));

            pauseButton.setEnabled(true);

        } else {

            pauseButton.setText(
                    getText(
                            "ভয়েস বন্ধ করুন",
                            "Pause Voice"));

            pauseButton.setEnabled(false);
        }

        exportAudioButton.setText(
                exportingAudio
                        ? getText(
                                "অডিও তৈরি হচ্ছে...",
                                "Creating audio...")
                        : getText(
                                "অডিও এক্সপোর্ট",
                                "Export Audio"));

        exportAudioButton.setEnabled(
                ttsReady && !exportingAudio);
    }

    private void exportAudio() {

        if (!ttsReady || exportingAudio) return;

        String text =
                textInput.getText().toString().trim();

        if (text.isEmpty()) {

            setStatus(getText(
                    "আগে কিছু লেখা লিখুন",
                    "Please enter some text first"));
            return;
        }

        exportParts.clear();
        exportParts.addAll(splitForAudioExport(text));

        if (exportParts.isEmpty()) return;

        exportDirectory = new File(
                getCacheDir(),
                "BanglaVoiceVideoExport_"
                        + System.currentTimeMillis());

        if (!exportDirectory.mkdirs()) {

            setStatus(getText(
                    "অডিও তৈরির স্থান তৈরি করা যায়নি",
                    "Could not create export folder"));
            return;
        }

        exportFiles.clear();
        exportIndex = 0;
        exportingAudio = true;

        setStatus(getText(
                "সম্পূর্ণ অডিও তৈরি হচ্ছে...",
                "Creating complete audio..."));

        updateButtons();

        synthesizeNextExportPart();
    }

    private ArrayList<String> splitForAudioExport(String text) {

        ArrayList<String> result = new ArrayList<>();

        String normalized =
                cleanTextForSpeech(text);

        if (normalized.isEmpty()) return result;

        String[] sentences =
                normalized.split(
                        "(?<=[।,?.!])\\s+");

        StringBuilder current =
                new StringBuilder();

        for (String sentence : sentences) {

            sentence = sentence.trim();

            if (sentence.isEmpty()) continue;

            if (current.length() == 0) {

                current.append(sentence);

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
                                3000));

                current.setLength(0);
                current.append(sentence);
            }
        }

        if (current.length() > 0) {

            result.addAll(
                    splitLongText(
                            current.toString(),
                            3000));
        }

        return result;
    }

    private ArrayList<String> splitLongText(
            String text,
            int max) {

        ArrayList<String> result =
                new ArrayList<>();

        String[] words =
                text.split("\\s+");

        StringBuilder current =
                new StringBuilder();

        for (String word : words) {

            if (word.isEmpty()) continue;

            if (current.length() > 0 &&
                    current.length()
                            + word.length()
                            + 1 > max) {

                result.add(
                        current.toString().trim());

                current.setLength(0);
            }

            if (current.length() > 0)
                current.append(" ");

            current.append(word);
        }

        if (current.length() > 0)
            result.add(current.toString().trim());

        return result;
    }

    private void synthesizeNextExportPart() {

        if (!exportingAudio) return;

        if (exportIndex >= exportParts.size()) {
            finishAudioExport();
            return;
        }

        String part =
                exportParts.get(exportIndex).trim();

        if (part.isEmpty()) {

            exportIndex++;
            synthesizeNextExportPart();
            return;
        }

        File file =
                new File(
                        exportDirectory,
                        "part_" + exportIndex + ".wav");

        try {

            if (file.exists()) file.delete();

            chooseLanguage(part);

            int result =
                    tts.synthesizeToFile(
                            part,
                            new Bundle(),
                            file,
                            "export_" + exportIndex);

            if (result != TextToSpeech.SUCCESS) {

                failAudioExport(getText(
                        "অডিও তৈরি করা যায়নি",
                        "Could not create audio"));
                return;
            }

            exportFiles.add(file);

        } catch (Exception e) {

            failAudioExport(getText(
                    "অডিও তৈরির সময় সমস্যা হয়েছে",
                    "Audio creation failed"));
        }
    }

    private void finishAudioExport() {

        if (!exportingAudio) return;

        new Thread(() -> {

            try {

                File output =
                        new File(
                                exportDirectory,
                                createAudioFileName(
                                        textInput.getText()
                                                .toString())
                                        + ".wav");

                mergeWavFiles(
                        exportFiles,
                        output);

                saveAudio(
                        output,
                        createAudioFileName(
                                textInput.getText()
                                        .toString()));

            } catch (Exception e) {

                runOnUiThread(() ->
                        failAudioExport(getText(
                                "অডিও ফাইল তৈরি করা যায়নি",
                                "Could not create audio file")));
            }

        }).start();
    }

    private void mergeWavFiles(
            ArrayList<File> files,
            File output)
            throws IOException {

        if (files.isEmpty())
            throw new IOException("No audio");

        byte[] header = new byte[44];

        try (FileInputStream in =
                     new FileInputStream(files.get(0))) {

            if (in.read(header) != 44)
                throw new IOException("Invalid WAV");
        }

        int channels =
                readShort(header,22);

        int sampleRate =
                readInt(header,24);

        int bits =
                readShort(header,34);

        long dataLength=0;

        for (File f:files) {

            if (f.length()<44)
                throw new IOException("Invalid WAV");

            dataLength += f.length()-44;
        }

        try (FileOutputStream out =
                     new FileOutputStream(output)) {

            writeWavHeader(
                    out,
                    dataLength,
                    sampleRate,
                    channels,
                    bits);

            byte[] buffer=new byte[8192];

            for (File f:files) {

                try (FileInputStream in =
                             new FileInputStream(f)) {

                    long skipped=0;

                    while(skipped<44) {

                        long n=in.skip(44-skipped);

                        if(n<=0) break;

                        skipped+=n;
                    }

                    int n;

                    while((n=in.read(buffer))!=-1)
                        out.write(buffer,0,n);
                }
            }
        }
    }

    private int readInt(byte[] b,int o) {

        return (b[o]&255)
                |((b[o+1]&255)<<8)
                |((b[o+2]&255)<<16)
                |((b[o+3]&255)<<24);
    }

    private int readShort(byte[] b,int o) {

        return (b[o]&255)
                |((b[o+1]&255)<<8);
    }

    private void writeInt(
            FileOutputStream out,
            int v)
            throws IOException {

        out.write(v&255);
        out.write((v>>8)&255);
        out.write((v>>16)&255);
        out.write((v>>24)&255);
    }

    private void writeShort(
            FileOutputStream out,
            int v)
            throws IOException {

        out.write(v&255);
        out.write((v>>8)&255);
    }

    private void writeWavHeader(
            FileOutputStream out,
            long dataLength,
            int sampleRate,
            int channels,
            int bits)
            throws IOException {

        int byteRate =
                sampleRate*channels*bits/8;

        int blockAlign =
                channels*bits/8;

        out.write(new byte[]{
                'R','I','F','F'
        });

        writeInt(
                out,
                (int)(36+dataLength));

        out.write(new byte[]{
                'W','A','V','E',
                'f','m','t',' '
        });

        writeInt(out,16);
        writeShort(out,1);
        writeShort(out,channels);
        writeInt(out,sampleRate);
        writeInt(out,byteRate);
        writeShort(out,blockAlign);
        writeShort(out,bits);

        out.write(new byte[]{
                'd','a','t','a'
        });

        writeInt(
                out,
                (int)dataLength);
    }

    private String createAudioFileName(
            String text) {

        String clean =
                text.replaceAll(
                        "\\s+"," ")
                        .trim();

        if(clean.isEmpty())
            return "BanglaVoiceVideo_Audio";

        String[] words =
                clean.split(" ");

        StringBuilder name =
                new StringBuilder();

        int count=0;

        for(String word:words) {

            String safe =
                    word.replaceAll(
                            "[\\\\/:*?\"<>|\\p{Cntrl}]",
                            "");

            if(safe.isEmpty()) continue;

            if(name.length()>0)
                name.append("_");

            name.append(safe);

            count++;

            if(count>=5) break;
        }

        if(name.length()==0)
            return "BanglaVoiceVideo_Audio";

        String result=name.toString();

        if(result.length()>50)
            result=result.substring(0,50);

        return result;
    }

    private void saveAudio(
            File source,
            String displayName) {

        try {

            if(Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q) {

                ContentValues values =
                        new ContentValues();

                values.put(
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        displayName+".wav");

                values.put(
                        MediaStore.Audio.Media.MIME_TYPE,
                        "audio/wav");

                values.put(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        "Music/BanglaVoiceVideo");

                values.put(
                        MediaStore.Audio.Media.IS_PENDING,
                        1);

                Uri uri =
                        getContentResolver().insert(
                                MediaStore.Audio.Media
                                        .EXTERNAL_CONTENT_URI,
                                values);

                if(uri==null)
                    throw new IOException(
                            "MediaStore failed");

                try(FileInputStream in=
                            new FileInputStream(source);
                    OutputStream out=
                            getContentResolver()
                                    .openOutputStream(uri)) {

                    if(out==null)
                        throw new IOException(
                                "Output failed");

                    byte[] buffer=
                            new byte[8192];

                    int n;

                    while((n=in.read(buffer))!=-1)
                        out.write(buffer,0,n);
                }

                ContentValues done=
                        new ContentValues();

                done.put(
                        MediaStore.Audio.Media.IS_PENDING,
                        0);

                getContentResolver().update(
                        uri,
                        done,
                        null,
                        null);

            } else {

                File music =
                        Environment
                                .getExternalStoragePublicDirectory(
                                        Environment
                                                .DIRECTORY_MUSIC);

                File folder =
                        new File(
                                music,
                                "BanglaVoiceVideo");

                if(!folder.exists() &&
                        !folder.mkdirs())
                    throw new IOException(
                            "Folder failed");

                copyFile(
                        source,
                        new File(
                                folder,
                                displayName+".wav"));
            }

            runOnUiThread(() -> {

                exportingAudio=false;

                setStatus(getText(
                        "অডিও সফলভাবে সংরক্ষণ হয়েছে",
                        "Audio saved successfully"));

                updateButtons();
                cleanExportFiles();
            });

        } catch(Exception e) {

            runOnUiThread(() ->
                    failAudioExport(getText(
                            "অডিও সংরক্ষণ করা যায়নি",
                            "Could not save audio")));
        }
    }

    private void copyFile(
            File source,
            File destination)
            throws IOException {

        try(FileInputStream in=
                    new FileInputStream(source);
            FileOutputStream out=
                    new FileOutputStream(destination)) {

            byte[] buffer=new byte[8192];
            int n;

            while((n=in.read(buffer))!=-1)
                out.write(buffer,0,n);
        }
    }

    private void failAudioExport(
            String message) {

        exportingAudio=false;
        setStatus(message);
        updateButtons();
        cleanExportFiles();
    }

    private void cleanExportFiles() {

        try {

            if(exportDirectory!=null &&
                    exportDirectory.exists()) {

                File[] files=
                        exportDirectory.listFiles();

                if(files!=null) {

                    for(File f:files)
                        f.delete();
                }

                exportDirectory.delete();
            }

        } catch(Exception ignored) {}

        exportFiles.clear();
        exportParts.clear();
    }

    private void showSettings() {

        LinearLayout layout =
                new LinearLayout(this);

        layout.setOrientation(
                LinearLayout.VERTICAL);

        layout.setPadding(
                30,20,30,20);

        TextView heading =
                new TextView(this);

        heading.setText(
                getText("সেটিংস","Settings"));

        heading.setTextSize(24);
        heading.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD);
        heading.setTextColor(Color.BLACK);

        layout.addView(heading);

        Button languageButton =
                new Button(this);

        languageButton.setText(
                getText(
                        "অ্যাপের ভাষা",
                        "App Language"));

        languageButton.setContentDescription(
                getText(
                        "অ্যাপের ভাষা পরিবর্তন করুন",
                        "Change app language"));

        languageButton.setOnClickListener(
                v -> showLanguageDialog());

        layout.addView(languageButton);

        TextView speedLabel =
                new TextView(this);

        speedLabel.setText(
                getText(
                        "ভয়েসের গতি",
                        "Voice Speed"));

        speedLabel.setTextSize(18);
        speedLabel.setTextColor(Color.BLACK);
        speedLabel.setPadding(
                0,20,0,10);

        layout.addView(speedLabel);

        Spinner spinner =
                new Spinner(this);

        String[] speeds={
                getText("ধীর","Slow"),
                getText("স্বাভাবিক","Normal"),
                getText("দ্রুত","Fast"),
                getText("খুব দ্রুত","Very Fast")
        };

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout
                                .simple_spinner_item,
                        speeds);

        adapter.setDropDownViewResource(
                android.R.layout
                        .simple_spinner_dropdown_item);

        spinner.setAdapter(adapter);

        int selected=1;

        if(speechRate<=0.85f)
            selected=0;
        else if(speechRate>=1.25f)
            selected=3;
        else if(speechRate>=1.10f)
            selected=2;

        spinner.setSelection(selected);

        spinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener(){

            @Override
            public void onItemSelected(
                    AdapterView<?> p,
                    View v,
                    int position,
                    long id){

                if(position==0)
                    speechRate=0.85f;
                else if(position==1)
                    speechRate=1.0f;
                else if(position==2)
                    speechRate=1.15f;
                else
                    speechRate=1.30f;

                preferences.edit()
                        .putFloat(
                                PREF_SPEED,
                                speechRate)
                        .apply();

                if(tts!=null && ttsReady)
                    tts.setSpeechRate(
                            speechRate);
            }

            @Override
            public void onNothingSelected(
                    AdapterView<?> p){}
        });

        layout.addView(spinner);

        Button about =
                new Button(this);

        about.setText(
                getText("ℹ About","ℹ About"));

        about.setOnClickListener(
                v -> showAbout());

        layout.addView(about);

        new AlertDialog.Builder(this)
                .setView(layout)
                .setPositiveButton(
                        getText(
                                "বন্ধ করুন",
                                "Close"),
                        null)
                .show();
    }

    private void showLanguageDialog() {

        String[] languages={
                "বাংলা",
                "English"
        };

        int checked=
                "en".equals(appLanguage)
                        ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle(
                        getText(
                                "অ্যাপের ভাষা",
                                "App Language"))
                .setSingleChoiceItems(
                        languages,
                        checked,
                        (dialog,which)->{

                            appLanguage=
                                    which==1
                                            ? "en"
                                            : "bn";

                            preferences.edit()
                                    .putString(
                                            PREF_LANGUAGE,
                                            appLanguage)
                                    .apply();

                            dialog.dismiss();

                            refreshLanguage();
                        })
                .setNegativeButton(
                        getText(
                                "বাতিল",
                                "Cancel"),
                        null)
                .show();
    }

    private void refreshLanguage() {

        textInput.setHint(
                getText(
                        "এখানে বাংলা বা English লেখা লিখুন",
                        "Write Bangla or English text here"));

        textInput.setContentDescription(
                getText(
                        "বাংলা অথবা English লেখা লেখার ঘর",
                        "Text input box for Bangla or English"));

        settingsButton.setText(
                getText(
                        "⚙ সেটিংস",
                        "⚙ Settings"));

        updateButtons();

        setStatus(
                getText(
                        "ভাষা পরিবর্তন হয়েছে",
                        "Language changed"));
    }

    private void showAbout() {

        TextView view =
                new TextView(this);

        view.setText(
                getText(
                        "BanglaVoiceVideo\n\n"
                        + "বাংলা ও English লেখা থেকে "
                        + "ভয়েস এবং অডিও তৈরি করার জন্য "
                        + "এই অ্যাপটি তৈরি করা হচ্ছে।\n\n"
                        + "TalkBack ব্যবহারকারীদের জন্য "
                        + "সহজ করার লক্ষ্য রয়েছে।",
                        "BanglaVoiceVideo\n\n"
                        + "This app is being developed "
                        + "to create voice and audio "
                        + "from Bangla and English text.\n\n"
                        + "The goal is to make it "
                        + "accessible for TalkBack users."));

        view.setTextSize(17);
        view.setTextColor(Color.BLACK);
        view.setPadding(25,20,25,20);

        ScrollView scroll =
                new ScrollView(this);

        scroll.addView(view);

        new AlertDialog.Builder(this)
                .setTitle("About")
                .setView(scroll)
                .setPositiveButton(
                        getText(
                                "বন্ধ করুন",
                                "Close"),
                        null)
                .show();
    }

    private String getText(
            String bangla,
            String english){

        return "en".equals(appLanguage)
                ? english
                : bangla;
    }

    private void setStatus(
            String message){

        if(statusText!=null)
            statusText.setText(
                    getText(
                            "বর্তমান অবস্থা: ",
                            "Current status: ")
                            + message);
    }

    private void startBackgroundService() {

        if(!speaking ||
                speechParts.isEmpty() ||
                currentPart>=speechParts.size())
            return;

        try {

            StringBuilder all =
                    new StringBuilder();

            for(int i=currentPart;
                i<speechParts.size();
                i++){

                if(all.length()>0)
                    all.append(" ");

                all.append(
                        cleanTextForSpeech(
                                speechParts.get(i)));
            }

            Intent intent =
                    new Intent(
                            this,
                            VoiceReadingService.class);

            intent.putExtra(
                    "text",
                    all.toString());

            intent.putExtra(
                    "language",
                    containsBangla(all.toString())
                            ? "bn"
                            : "en");

            if(Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O){

                startForegroundService(intent);

            } else {

                startService(intent);
            }

            backgroundReading=true;

            if(tts!=null)
                tts.stop();

            speaking=true;

            setStatus(getText(
                    "স্ক্রিন বন্ধ থাকলেও ভয়েস চলছে",
                    "Voice continues in background"));

            updateButtons();

        } catch(Exception ignored) {}
    }

    private void stopBackgroundService() {

        try {

            Intent intent =
                    new Intent(
                            this,
                            VoiceReadingService.class);

            intent.putExtra(
                    "action",
                    "STOP");

            startService(intent);

        } catch(Exception ignored) {}

        backgroundReading=false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(speaking &&
                !isFinishing() &&
                !exportingAudio &&
                !backgroundReading){

            startBackgroundService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(backgroundReading){

            /*
             * Activity সামনে এলে Background Service
             * বন্ধ করা হবে না। এতে screen-off reading
             * থেকে হঠাৎ করে আবার প্রথম অংশ শুরু হবে না।
             */
            speaking=true;
            updateButtons();
        }
    }

    @Override
    protected void onDestroy() {

        exportingAudio=false;

        cleanExportFiles();

        if(isFinishing())
            stopBackgroundService();

        if(tts!=null){

            tts.stop();
            tts.shutdown();
            tts=null;
        }

        super.onDestroy();
    }
}
