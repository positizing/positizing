package com.positizing.phone;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import com.positizing.android.AbstractPositizingActivity;

import java.util.*;

public class MainActivity extends AbstractPositizingActivity {

    private ImageButton settingsButton;
    private Button startButton;
    private Button stopButton;
    private TextView statusTextView;
    private ListView sentencesListView;

    private ArrayAdapter<String> sentencesAdapter;
    private List<String> detectedSentences;
    private List<List<String>> sentenceSuggestions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.positizing_main);
        prepareDetector();

        settingsButton = findViewById(R.id.settings_button);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        statusTextView = findViewById(R.id.status_text_view);
        sentencesListView = findViewById(R.id.sentences_list_view);

        detectedSentences = new ArrayList<>();
        sentenceSuggestions = new ArrayList<>();

        sentencesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, detectedSentences);
        sentencesListView.setAdapter(sentencesAdapter);

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        startButton.setOnClickListener(v -> {
            setupSpeechRecognizer();
            statusTextView.setText(R.string.detection_status_active);
        });

        stopButton.setOnClickListener(v -> {
            disableSpeechRecognizer();
            statusTextView.setText(R.string.detection_status_inactive);
        });

        sentencesListView.setOnItemClickListener((parent, view, position, id) -> {
            String sentence = detectedSentences.get(position);
            List<String> suggestions = sentenceSuggestions.get(position);

            // Ensure we get the latest suggestions from the cache
            List<String> cachedSuggestions = suggestionsCache.get(sentence);
            if (cachedSuggestions != null) {
                suggestions = cachedSuggestions;
                // Update the list
                sentenceSuggestions.set(position, suggestions);
            }

            Intent intent = new Intent(MainActivity.this, SuggestionsActivity.class);
            intent.putExtra("original_sentence", sentence);
            intent.putStringArrayListExtra("suggestions", new ArrayList<>(suggestions));
            startActivity(intent);
        });
    }

    @Override
    protected void notifyUser(final String sentence, final List<String> suggestions) {
        runOnUiThread(() -> {
            int index = detectedSentences.indexOf(sentence);
            if (index == -1) {
                // Sentence not in the list yet; add it
                detectedSentences.add(sentence);
                sentenceSuggestions.add(new ArrayList<>(suggestions));
                sentencesAdapter.notifyDataSetChanged();
            } else {
                // Sentence already exists; update its suggestions
                sentenceSuggestions.set(index, new ArrayList<>(suggestions));
            }
        });
    }
}
