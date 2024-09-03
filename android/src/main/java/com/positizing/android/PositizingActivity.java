package com.positizing.android;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import injuction.detector.InjunctionDetector;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * PositizerActivity:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 02/09/2024 @ 11:58 p.m.
 */
public class PositizingActivity extends Activity {
    private ImageButton startButton;
    private static final String GOOGLE_RECOGNITION_SERVICE_NAME = "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService";
//    private Button stopButton;

    public byte[] buffer;
    public static DatagramSocket socket;
    private int port = 50005;

    AudioRecord recorder;

    private int sampleRate = 16000; // 44100 for music
    private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
    private boolean status = true;
    private static final int RecordAudioRequestCode = 1;
    private SpeechRecognizer recognizer;
    private InjunctionDetector detector;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.positizing.android.R.layout.positizing_main);

        startButton = (ImageButton) findViewById(com.positizing.android.R.id.myButton);

        startButton.setOnClickListener(startListener);
        detector = new InjunctionDetector();
        ComponentName recognizerServiceComponent = ComponentName.unflattenFromString(GOOGLE_RECOGNITION_SERVICE_NAME);
        recognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext(), recognizerServiceComponent);

        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(final Bundle bundle) {

            }

            @Override
            public void onBeginningOfSpeech() {

            }

            @Override
            public void onRmsChanged(final float v) {

            }

            @Override
            public void onBufferReceived(final byte[] bytes) {

            }

            @Override
            public void onEndOfSpeech() {

            }

            @Override
            public void onError(final int i) {

            }

            @Override
            public void onResults(final Bundle bundle) {
                final ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                for (String result : results) {
                    Log.i("Positizing", "Got speech result: " + result);
                    if (detector.isInjuction(result)) {
                        Log.i("Positizing", "Found injunction: " + result);

                    }
                }


            }

            @Override
            public void onPartialResults(final Bundle bundle) {
                final ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                for (String result : results) {
                    Log.i("Positizing", "Got speech result: " + result);
                    if (detector.isInjuction(result)) {
                        Log.i("Positizing", "Found injunction: " + result);

                    }
                }

            }

            @Override
            public void onEvent(final int i, final Bundle bundle) {

            }
        });
        recognizer.startListening(recognizerIntent);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private final View.OnClickListener stopListener = new View.OnClickListener() {

        @Override
        public void onClick(View arg0) {
            status = false;
            recorder.release();
            Log.d("VS", "Recorder released");
        }

    };

    private final View.OnClickListener startListener = new View.OnClickListener() {

        @Override
        public void onClick(View arg0) {
            status = true;
            startStreaming();
        }

    };

    public void startStreaming() {


        Thread streamThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {

                    byte[] buffer = new byte[minBufSize];

                    if (ActivityCompat.checkSelfPermission(PositizingActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(PositizingActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, RecordAudioRequestCode);

                        return;
                    }
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize * 10);
                    Log.d("VS", "Recorder initialized");

                    recorder.startRecording();


                    while(status) {


                        //reading data from MIC into buffer
                        minBufSize = recorder.read(buffer, 0, buffer.length);


                        System.out.println("MinBufferSize: " +minBufSize);


                    }



                } finally {

                }
            }

        });
        streamThread.start();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
