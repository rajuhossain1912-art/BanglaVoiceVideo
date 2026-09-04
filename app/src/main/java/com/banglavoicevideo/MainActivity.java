package com.banglavoicevideo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

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

        inputText = findViewById(R.id.inputText);
        btnSpeakBangla = findViewById(R.id.btnSpeakBangla);
        btnSpeakEnglish = findViewById(R.id.btnSpeakEnglish);
        btnStop = findViewById(R.id.btnStop);

        btnSpeakBangla.setOnClickListener(v -> {
            String text = inputText.getText().toString().trim();

            if (!text.isEmpty()) {
                startVoiceService(text, "bn");
            }
        });

        btnSpeakEnglish.setOnClickListener(v -> {
            String text = inputText.getText().toString().trim();

            if (!text.isEmpty()) {
                startVoiceService(text, "en");
            }
        });

        btnStop.setOnClickListener(v -> {
            Intent intent = new Intent(this, VoiceReadingService.class);
            intent.setAction("STOP");
            startService(intent);
        });
    }

    private void startVoiceService(String text, String language) {
        Intent intent = new Intent(this, VoiceReadingService.class);
        intent.setAction("SPEAK");
        intent.putExtra("text", text);
        intent.putExtra("language", language);

        startForegroundService(intent);
    }
}
