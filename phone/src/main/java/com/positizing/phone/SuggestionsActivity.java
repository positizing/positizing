package com.positizing.phone;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * SuggestionsActivity:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 10/11/2024 @ 11:29 p.m.
 */
public class SuggestionsActivity extends AppCompatActivity {

    private TextView originalSentenceTextView;
    private ListView suggestionsListView;
    private Button backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggestions);

        backButton = findViewById(R.id.back_button);
        originalSentenceTextView = findViewById(R.id.original_sentence_text_view);
        suggestionsListView = findViewById(R.id.suggestions_list_view);

        String originalSentence = getIntent().getStringExtra("original_sentence");
        List<String> suggestions = getIntent().getStringArrayListExtra("suggestions");

        originalSentenceTextView.setText(getString(R.string.original_sentence_label, originalSentence));

        ArrayAdapter<String> suggestionsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, suggestions);
        suggestionsListView.setAdapter(suggestionsAdapter);

        backButton.setOnClickListener(v -> {
            // Finish the activity to go back to the previous one
            finish();
        });
    }
}