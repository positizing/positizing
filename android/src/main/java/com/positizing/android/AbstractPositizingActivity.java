package com.positizing.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import injuction.detector.InjunctionDetector;

import java.net.DatagramSocket;
import java.util.ArrayList;

/**
 * PositizerActivity:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 02/09/2024 @ 11:58 p.m.
 */
public abstract class AbstractPositizingActivity extends Activity {
    // https://stackoverflow.com/questions/64319117/speechrecognizer-not-available-when-targeting-android-11
    public static final String GOOGLE_RECOGNITION_SERVICE_NAME = "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService";

    private int sampleRate = 16000; // 44100 for music
    private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private static final int RecordAudioRequestCode = 0x1001;
    protected SpeechRecognizer recognizer;
    protected InjunctionDetector detector;
    public static final String TAG = "positizing";
    public static final String CHANNEL_ID = "com.positizing.notify";
    private RecognitionListener handler;
    private Intent recognizerIntent;

    protected void prepareDetector() {
        detector = new InjunctionDetector();

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

    private void setupSpeechRecognizer() {

        ComponentName recognizerServiceComponent = ComponentName.unflattenFromString(GOOGLE_RECOGNITION_SERVICE_NAME);

        if (recognizer != null) {
            destroy(recognizer);
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext(), recognizerServiceComponent);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
//        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");

        handler = new RecognitionListener() {
            private boolean hadPartial;
            private String lastResult;

            @Override
            public void onReadyForSpeech(final Bundle bundle) {
                Log.i(TAG, "ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.i(TAG, "beginning of speech");
            }

            @Override
            public void onRmsChanged(final float v) {
            }

            @Override
            public void onBufferReceived(final byte[] bytes) {
                Log.i(TAG, "buffer received");
            }

            @Override
            public void onEndOfSpeech() {
                Log.i(TAG, "End of speech");
            }

            @Override
            public void onError(final int i) {
                Log.i(TAG, "onError: " + i);
            }

            @Override
            public void onResults(final Bundle bundle) {
                if (!hadPartial) {
                    final ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    for (String result : results) {
                        Log.i("Positizing", "Got full speech result: " + result);
                        if (detector.isInjuction(result)) {
                            Log.i("Positizing", "Found injunction onResults: " + result);
                            notifyUser();

                        }
                    }
                }
                startSpeechRecognition();
            }

            @Override
            public void onPartialResults(final Bundle bundle) {
                hadPartial = true;
                final ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                for (String result : results) {
                    lastResult = result;
                    Log.i(TAG, "Got partial speech result: " + result);
                    if (detector.isInjuction(lastResult + " " + result)) {
                        Log.i(TAG, "Found injunction with space: partialResults: " + result);
                        notifyUser();
                    } else if (detector.isInjuction(lastResult + result)) {
                        Log.i(TAG, "Found injunction without space: partialResults: " + result);
                        notifyUser();

                    }
                }

            }

            @Override
            public void onEvent(final int i, final Bundle bundle) {
                Log.i(TAG, "event: " + i);
            }
        };

        startSpeechRecognition();
    }

    protected abstract void notifyUser();

    private void startSpeechRecognition() {
        recognizer.setRecognitionListener(handler);
        recognizer.startListening(recognizerIntent);
    }

    private void destroy(final SpeechRecognizer recognizer) {
        recognizer.stopListening();
        recognizer.destroy();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
