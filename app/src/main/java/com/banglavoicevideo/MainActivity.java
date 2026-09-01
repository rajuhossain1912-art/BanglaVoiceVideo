
package com.banglavoicevideo;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText inputText;
    private Button btnSpeakBangla, btnSpeakEnglish, btnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputText = findViewById(R.id.inputText);
        btnSpeakBangla = findViewById(R.id.btnSpeakBangla);
        btnSpeakEnglish = findViewById(R.id.btnSpeakEnglish);
        btnStop = findViewById(R.id.btnStop);

        btnSpeakBangla.setOnClickListener(v -> {
            String text = inputText.getText().toString();
            startVoiceService(text, "bn");
        });

        btnSpeakEnglish.setOnClickListener(v -> {
            String text = inputText.getText().toString();
            startVoiceService(text, "en");
        });

        btnStop.setOnClickListener(v -> {
            Intent intent = new Intent(this, VoiceReadingService.class);
            intent.putExtra("action", "STOP");
            startService(intent);
        });
    }

    private void startVoiceService(String text, String language) {
        if (text.isEmpty()) return;

        Intent intent = new Intent(this, VoiceReadingService.class);
        intent.putExtra("text", text);
        intent.putExtra("language", language);

        // Android 8.0+ (API 26+) এর জন্য startForegroundService নিশ্চিত করা
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
