package com.positizing.android;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import injunction.detector.InjunctionDetector;

import java.util.*;
import java.util.stream.Collectors;

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
    private AudioManager audio;
    private static final int DING_STREAM = AudioManager.STREAM_NOTIFICATION;
    private int mStreamVolume;
    private boolean performingSpeechSetup;

    protected void prepareDetector() {
        detector = new injunction.detector.InjunctionDetector(new AndroidTaskExecutor());

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
        recognizer.stopListening();
    }
    protected void setupSpeechRecognizer() {
        ComponentName recognizerServiceComponent = ComponentName.unflattenFromString(GOOGLE_RECOGNITION_SERVICE_NAME);
        if (recognizer != null) {
            destroy(recognizer);
        }
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        recognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext(), recognizerServiceComponent);
        mStreamVolume = audio.getStreamVolume(DING_STREAM);
        audio.adjustStreamVolume(DING_STREAM, AudioManager.ADJUST_MUTE, 0);

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
                performingSpeechSetup = false;

                unmute();
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
                if (performingSpeechSetup && i == SpeechRecognizer.ERROR_NO_MATCH) {
                    Log.w(TAG, "Ignoring startup error");
                    unmute();
                    return;
                }
                if (i == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    return;
                }
                Log.i(TAG, "onError: " + i);
                startSpeechRecognition();
            }

            @Override
            public void onResults(final Bundle bundle) {
                if (!hadPartial) {
                    processResults(bundle);
                }
                startSpeechRecognition();
            }

            @Override
            public void onPartialResults(final Bundle bundle) {
                hadPartial = true;
                processResults(bundle);

            }

            @Override
            public void onEvent(final int i, final Bundle bundle) {
                Log.i(TAG, "event: " + i);
            }
        };

        startSpeechRecognition();
    }

    private void processResults(final Bundle bundle) {
        final ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        Collection<String> compressed = results.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        compressed = new ArrayList<>(compressed);
        Collections.reverse((List<String>) compressed);
        Log.i(TAG, "Full results: " + compressed);
        for (String sentence : compressed) {
            Log.i("Positizing", "Got full speech result: " + sentence);
            if (detector.isInjuction(sentence)) {
                Log.i("Positizing", "Found injunction onResults: " + sentence);
                notifyUser(sentence);
                break;
            }
        }
    }

    private void unmute() {
        final Handler handler = new Handler();
        handler.postDelayed(() -> {
            //Do something after 10000ms
            Log.i(TAG, "Unmute");
            audio.adjustStreamVolume(DING_STREAM, AudioManager.ADJUST_UNMUTE, 0);
        }, 500);
    }

    protected abstract void notifyUser(final String sentence);

    private void startSpeechRecognition() {
        mute();
        try {

            recognizer.setRecognitionListener(handler);
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

    private void destroy(final SpeechRecognizer recognizer) {
        recognizer.stopListening();
        recognizer.destroy();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected NotificationChannel setupChannel() {

        final AudioAttributes attr = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        NotificationCompat.Builder b = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
        final String name = "positizing_channel";
        final String channelId = name + UUID.randomUUID().toString();

        final int importance = NotificationManager.IMPORTANCE_DEFAULT;
        final NotificationChannel channel = new NotificationChannel(channelId, name, importance);
        channel.enableLights(true);
        channel.setLightColor(Color.GREEN);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        // add vibration
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{ 0L, 300L, 300L, 300L});

        final Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + com.positizing.R.raw.chime);
        channel.setSound(uri, attr);
        return channel;
    }
}
