package com.positizing.android;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.*;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import injunction.detector.NegativeSpeechDetector;
import injunction.detector.NegativeSpeechRephraser;
import injunction.detector.SentenceExtractionResult;

public abstract class AbstractPositizingActivity extends Activity {

    private static final int MAX_CACHE_SIZE = 128;

    protected final Map<String, List<String>> suggestionsCache = Collections.synchronizedMap(
            new LinkedHashMap<String, List<String>>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    public static final String TAG = "positizing";
    public static final String GOOGLE_RECOGNITION_SERVICE_NAME = "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService";
    private static final int RecordAudioRequestCode = 0x1001;

    private static final long INACTIVITY_TIMEOUT_MS = 1500; // Adjust as needed
    private Handler inactivityHandler = new Handler();
    private Runnable inactivityRunnable;

    protected SpeechRecognizer recognizer;
    protected NegativeSpeechDetector detector;
    private Intent recognizerIntent;
    private AudioManager audio;
    private static final int DING_STREAM = AudioManager.STREAM_NOTIFICATION;
    private int mStreamVolume;
    private boolean performingSpeechSetup;
    private StringBuilder sentenceBuffer = new StringBuilder();
    private Queue<String> pendingSentences = new ConcurrentLinkedQueue<>();
    private Handler mainHandler;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    protected void prepareDetector() {
        detector = new NegativeSpeechDetector(new AndroidTaskExecutor());
        mainHandler = new Handler(getMainLooper());

        if (ActivityCompat.checkSelfPermission(AbstractPositizingActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(AbstractPositizingActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, RecordAudioRequestCode);
            return;
        }
        setupSpeechRecognizer();
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (resultCode == RecordAudioRequestCode) {
            // the user granted us permission to record audio.
            setupSpeechRecognizer();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    protected void disableSpeechRecognizer() {
        if (recognizer != null) {
            recognizer.stopListening();
            recognizer.destroy();
            recognizer = null;
        }
    }

    protected void setupSpeechRecognizer() {
        if (recognizer != null) {
            recognizer.stopListening();
            recognizer.cancel();
        } else {

            audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            ComponentName recognizerServiceComponent = ComponentName.unflattenFromString(GOOGLE_RECOGNITION_SERVICE_NAME);
            PackageManager pm = getPackageManager();

            try {
                pm.getServiceInfo(recognizerServiceComponent, PackageManager.GET_META_DATA);
                // Google recognition service is available
                recognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext(), recognizerServiceComponent);
                Log.i(TAG, "Using Google recognition service.");
            } catch (PackageManager.NameNotFoundException e) {
                // Google recognition service not available, use default
                recognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
                Log.i(TAG, "Google recognition service not found. Using default service.");
            }
            mStreamVolume = audio.getStreamVolume(DING_STREAM);
            audio.adjustStreamVolume(DING_STREAM, AudioManager.ADJUST_MUTE, 0);

            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");

            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.i(TAG, "ready for speech");
                    performingSpeechSetup = false;
                    unmute();
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.i(TAG, "beginning of speech");
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Optional: Implement if you need audio level feedback
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    Log.i(TAG, "buffer received");
                }

                @Override
                public void onEndOfSpeech() {
                    Log.i(TAG, "End of speech");

                    // Process any remaining text in the buffer
    //                String remainingText = sentenceBuffer.toString().trim();
    //                // Note: Be cautious with this approach as onEndOfSpeech might be called frequently, depending on the speech recognizer's behavior.
    //                if (!remainingText.isEmpty()) {
    //                    Log.i(TAG, "Processing remaining buffer at end of speech: " + remainingText);
    //                    sentenceBuffer.setLength(0);
    //                    processFullSentence(remainingText);
    //                }
                }

                @Override
                public void onError(int error) {
                    if (performingSpeechSetup && error == SpeechRecognizer.ERROR_NO_MATCH) {
                        Log.w(TAG, "Ignoring startup error");
                        unmute();
                        return;
                    }
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        return;
                    }
                    Log.i(TAG, "onError: " + error);
                    startSpeechRecognition();
                }

                @Override
                public void onResults(Bundle results) {
                    processResults(results);
                    startSpeechRecognition();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    processResults(partialResults);
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    Log.i(TAG, "event: " + eventType);
                }
            });
        }

        startSpeechRecognition();
    }

    private void processResults(final Bundle bundle) {
        final ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (results == null || results.isEmpty()) return;

        String transcript = results.get(0);
        Log.i(TAG, "Transcript: " + transcript);

        // Append recognized text to the buffer
        sentenceBuffer.append(transcript).append(" ");

        // Use NegativeSpeechDetector to extract complete sentences
        SentenceExtractionResult extractionResult = detector.extractCompleteSentences(sentenceBuffer.toString());

        List<String> completeSentences = extractionResult.getCompleteSentences();
        String remainingText = extractionResult.getRemainingText();

        // Update the buffer with the remaining text
        sentenceBuffer.setLength(0);
        sentenceBuffer.append(remainingText);

        // Process each complete sentence
        for (String sentence : completeSentences) {
            processFullSentence(sentence);
        }

        // Reset the inactivity timer
        resetInactivityTimer();
    }

    private void resetInactivityTimer() {
        if (inactivityRunnable != null) {
            inactivityHandler.removeCallbacks(inactivityRunnable);
        }
        inactivityRunnable = () -> {
            // Process any remaining text in the buffer
            String remainingText = sentenceBuffer.toString().trim();
            if (!remainingText.isEmpty()) {
                Log.i(TAG, "Processing remaining buffer due to inactivity: " + remainingText);
                sentenceBuffer.setLength(0);
                processFullSentence(remainingText);
            }
        };
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT_MS);
    }

    private void processFullSentence(String sentence) {
        Log.i(TAG, "Processing full sentence: " + sentence);

        // Check if the sentence is already in the cache
        if (suggestionsCache.containsKey(sentence)) {
            List<String> cachedSuggestions = suggestionsCache.get(sentence);
            notifyUser(sentence, cachedSuggestions);
            return;
        }

        // Use a thread-safe list to collect suggestions
        List<String> suggestions = Collections.synchronizedList(new ArrayList<>());

        // Suggest Improved Sentence
//        detector.suggestImprovedSentenceAsync(sentence, improvedSentence -> {
//            if (!improvedSentence.equals(sentence)) {
//                suggestions.add(improvedSentence);
//                suggestionsCache.put(sentence, new ArrayList<>(suggestions));
//                notifyUser(sentence, suggestions);
//            }
//        });
//
//        // Suggest Injunction Replacement
//        detector.suggestInjunctionReplacementAsync(sentence, replacement -> {
//            if (!replacement.equals(sentence)) {
//                suggestions.add(replacement);
//                suggestionsCache.put(sentence, new ArrayList<>(suggestions));
//                notifyUser(sentence, suggestions);
//            }
//        });
//
//        // Suggest Conjunction Replacement
//        detector.suggestConjunctionReplacementAsync(sentence, replacement -> {
//            if (!replacement.equals(sentence)) {
//                suggestions.add(replacement);
//                suggestionsCache.put(sentence, new ArrayList<>(suggestions));
//                notifyUser(sentence, suggestions);
//            }
//        });
        detector.needsReplacement(sentence, ()-> {
            executorService.execute(() -> {
                List<String> replacement;
                try {
                    replacement = NegativeSpeechRephraser.rephraseNegativeSpeech(sentence);
                    notifyUser(sentence, replacement);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });


    }

    private void unmute() {
        final Handler handler = new Handler();
        handler.postDelayed(() -> {
            Log.i(TAG, "Unmute");
            audio.adjustStreamVolume(DING_STREAM, AudioManager.ADJUST_UNMUTE, 0);
        }, 500);
    }

    protected abstract void notifyUser(final String sentence, final List<String> suggestions);

    private void startSpeechRecognition() {
        mute();
        try {
            recognizer.startListening(recognizerIntent);
            performingSpeechSetup = true;
        } catch (Exception e) {
            Log.e(TAG, "Did not bind listener", e);
        }
    }

    private void mute() {
        Log.i(TAG, "Mute");
        audio.adjustStreamVolume(DING_STREAM, AudioManager.ADJUST_MUTE, 0);
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        if (requestCode == RecordAudioRequestCode && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupSpeechRecognizer();
        } else {
            // Permission denied
            Log.e(TAG, "Record audio permission denied");
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
