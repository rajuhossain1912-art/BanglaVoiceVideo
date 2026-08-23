package com.banglavoicevideo;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class MainActivity extends Activity {

    private TextToSpeech textToSpeech;
    private EditText textInput;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 30, 30, 30);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("BanglaVoiceVideo");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 0, 0, 30);
        title.setContentDescription("BanglaVoiceVideo অ্যাপ");

        textInput = new EditText(this);
        textInput.setHint("এখানে আপনার বাংলা বা ইংরেজি লেখা লিখুন");
        textInput.setTextSize(18);
        textInput.setMinLines(8);
        textInput.setGravity(Gravity.TOP);
        textInput.setContentDescription(
                "ভিডিও তৈরির জন্য বাংলা বা ইংরেজি লেখা"
        );

        Button speakButton = new Button(this);
        speakButton.setText("লেখা শুনুন");
        speakButton.setContentDescription("লেখা শুনুন");

        Button stopButton = new Button(this);
        stopButton.setText("ভয়েস বন্ধ করুন");
        stopButton.setContentDescription("ভয়েস বন্ধ করুন");

        statusText = new TextView(this);
        statusText.setText("প্রস্তুত");
        statusText.setTextSize(16);
        statusText.setPadding(0, 25, 0, 0);
        statusText.setContentDescription("বর্তমান অবস্থা");

        layout.addView(title);
        layout.addView(textInput);
        layout.addView(speakButton);
        layout.addView(stopButton);
        layout.addView(statusText);

        setContentView(layout);

        textToSpeech = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                int languageResult =
                        textToSpeech.setLanguage(new Locale("bn", "BD"));

                if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    statusText.setText(
                            "বাংলা ভয়েস আপনার ফোনে পাওয়া যাচ্ছে না।"
                    );
                }
            }
        });

        speakButton.setOnClickListener(v -> speakText());

        stopButton.setOnClickListener(v -> {
            if (textToSpeech != null) {
                textToSpeech.stop();
                statusText.setText("ভয়েস বন্ধ করা হয়েছে।");
            }
        });
    }

    private void speakText() {
        String text = textInput.getText().toString().trim();

        if (text.isEmpty()) {
            statusText.setText("দয়া করে আগে কিছু লেখা লিখুন।");
            return;
        }

        if (textToSpeech != null) {
            textToSpeech.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "BanglaVoiceVideoSpeech"
            );

            statusText.setText("ভয়েস চালু হয়েছে...");
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
