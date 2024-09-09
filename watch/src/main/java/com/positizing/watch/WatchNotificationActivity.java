package com.positizing.watch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.positizing.android.AbstractPositizingActivity;
import injuction.detector.InjunctionDetector;

import java.util.ArrayList;

/**
 * PositizerActivity:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 02/09/2024 @ 11:58 p.m.
 */
public class WatchNotificationActivity extends AbstractPositizingActivity {


    private ImageButton startButton;
    private ImageButton stopButton;
    protected TextView textView;
    // https://stackoverflow.com/questions/64319117/speechrecognizer-not-available-when-targeting-android-11
    private static final String GOOGLE_RECOGNITION_SERVICE_NAME = "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService";
    private int sampleRate = 16000; // 44100 for music
    private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
    private boolean status = true;
    private static final int RecordAudioRequestCode = 0x1001;
    private SpeechRecognizer recognizer;
    private InjunctionDetector detector;
    public static final String TAG = "positizing";
    public static final String CHANNEL_ID = TAG;
    private RecognitionListener handler;
    private Intent recognizerIntent;
    private PositizingNotificationReceiver nReceiver;
    private IntentFilter filter;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(nReceiver);
    }

    public void buttonClicked(View v){

//        if(v.getId() == R.id.btnCreateNotify){
//            NotificationManager nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//            NotificationCompat.Builder ncomp = new NotificationCompat.Builder(this);
//            ncomp.setContentTitle("My Notification");
//            ncomp.setContentText("Notification Listener Service Example");
//            ncomp.setTicker("Notification Listener Service Example");
//            ncomp.setSmallIcon(R.drawable.ic_launcher);
//            ncomp.setAutoCancel(true);
//            nManager.notify((int)System.currentTimeMillis(),ncomp.build());
//        }
//        else
        if(v.getId() == R.id.btnClearNotify){
            Intent i = new Intent("com.kpbird.nlsexample.NOTIFICATION_LISTENER_SERVICE_EXAMPLE");
            i.putExtra("command","clearall");
            sendBroadcast(i);
        }
        else if(v.getId() == R.id.btnListNotify){
            Intent i = new Intent("com.kpbird.nlsexample.NOTIFICATION_LISTENER_SERVICE_EXAMPLE");
            i.putExtra("command","list");
            sendBroadcast(i);
        }


    }


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.positizing_watch);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        textView = findViewById(R.id.textField);

        nReceiver = new PositizingNotificationReceiver(this);
        filter = new IntentFilter();
        filter.addAction("com.positizing.notify");
        registerReceiver(nReceiver,filter);


    }

    @Override
    @SuppressLint("ObsoleteSdkInt")
    protected void notifyUser() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
// Vibrate for 500 milliseconds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            v.vibrate(500);
        }
        NotificationCompat.Builder b = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);

    }

}
