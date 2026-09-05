package com.banglavoicevideo;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText inputText;
    private Button btnSpeakBangla;
    private Button btnSpeakEnglish;
    private Button btnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ভিউ ইনিশিয়ালাইজেশন
        inputText = findViewById(R.id.inputText);
        btnSpeakBangla = findViewById(R.id.btnSpeakBangla);
        btnSpeakEnglish = findViewById(R.id.btnSpeakEnglish);
        btnStop = findViewById(R.id.btnStop);

        // বাংলা ও ইংরেজি বাটনের জন্য লিসেনার
        btnSpeakBangla.setOnClickListener(v -> processAndSpeak("bn"));
        btnSpeakEnglish.setOnClickListener(v -> processAndSpeak("en"));

        // স্টপ বাটনের জন্য লিসেনার
        btnStop.setOnClickListener(v -> stopVoiceService());
    }

    private void processAndSpeak(String language) {
        String text = inputText.getText().toString().trim();

        // টেক্সট খালি থাকলে ইউজারকে সতর্কতা দেখাবে
        if (text.isEmpty()) {
            inputText.setError("এখানে কিছু লিখুন");
            Toast.makeText(this, "লেখা খালি রাখা যাবে না!", Toast.LENGTH_SHORT).show();
            return;
        }

        startVoiceService(text, language);
    }

    private void startVoiceService(String text, String language) {
        Intent intent = new Intent(this, VoiceReadingService.class);
        intent.setAction("SPEAK");
        intent.putExtra("text", text);
        intent.putExtra("language", language);

        // এন্ড্রয়েড ভার্সন অনুযায়ী সার্ভিস স্টার্ট করা
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopVoiceService() {
        Intent intent = new Intent(this, VoiceReadingService.class);
        intent.setAction("STOP");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
