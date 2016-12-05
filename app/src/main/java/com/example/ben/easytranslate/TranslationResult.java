package com.example.ben.easytranslate;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class TranslationResult extends AppCompatActivity {

    TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.translation_result);
        resultText = (TextView) findViewById(R.id.new_result_text);
        if (getIntent() != null && getIntent().hasExtra(Intent.EXTRA_TEXT)) {
            String translation = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            resultText.setText(translation);
        }

    }

}
