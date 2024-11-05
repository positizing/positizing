package com.positizing.phone;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.widget.NestedScrollView;
import com.positizing.android.AbstractPositizingActivity;

/**
 * PositizerActivity:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 02/09/2024 @ 11:58 p.m.
 */
public class PhonePositizingActivity extends AbstractPositizingActivity {
    private Button startButton;
    private Button stopButton;
    private LinearLayout resultsPanel;

    private int sampleRate = 16000; // 44100 for music
    private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
    private static final int RecordAudioRequestCode = 0x1001;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.positizing_main);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        resultsPanel = findViewById(R.id.resultsPanel);

        startButton.setOnClickListener(startListener);

        if (ActivityCompat.checkSelfPermission(PhonePositizingActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(PhonePositizingActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, RecordAudioRequestCode);
            return;
        }
//        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            prepareDetector();
//        } else {
//            notifyUser("Cannot start speech recognition");
//            notifyUser("Please enable google speech recognizer in your settings");
//            notifyUser("Go to Settings, search for \"Text-to-Speech\".");
//            notifyUser("Select Speech Recognition and Synthesis from Google as your preferred engine.");
//        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (resultCode == RecordAudioRequestCode) {
            // the user granted us permission to record audio.
            setupSpeechRecognizer();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @SuppressLint("ObsoleteSdkInt")
    protected void notifyUser(final String sentence) {
        Handler mainHandler = new Handler(getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {

                TextView result = new TextView(PhonePositizingActivity.this);
                result.setText(sentence);
                result.setBackgroundColor(R.drawable.mybg);
                result.setPadding(4, 4, 4, 4);
                /*
                        <TextView
                        android:id="@+id/textField"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:background="@drawable/mybg"
                        android:gravity="center_vertical"
                        android:paddingStart="4dp"
                        android:paddingEnd="4dp"
                        android:text="  "
                        android:textAppearance="?android:attr/textAppearanceLarge"/>
                * */

                resultsPanel.addView(result);
            }
        };
        mainHandler.post(myRunnable);

//        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
//// Vibrate for 500 milliseconds
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
//        } else {
//            //deprecated in API 26
//            v.vibrate(500);
//        }



//        NotificationCompat.Builder b = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
//
//        final AudioAttributes attr = new AudioAttributes.Builder()
//                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
//                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                .build();
//        final String name = "positizing_channel";
//        final String channelId = name + UUID.randomUUID().toString();
//
//        final int importance = NotificationManager.IMPORTANCE_DEFAULT;
//        final NotificationChannel channel = new NotificationChannel(channelId, name, importance);
//        channel.enableLights(true);
//        channel.setLightColor(Color.GREEN);
//        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
//        // add vibration
//        channel.enableVibration(true);
//        channel.setVibrationPattern(new long[]{ 0L, 300L, 300L, 300L});
//
//        final Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + com.positizing.R.raw.chime);
//        channel.setSound(uri, attr);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(PhonePositizingActivity.this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_alert);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com/"));
        PendingIntent pendingIntent = PendingIntent.getActivity(PhonePositizingActivity.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(pendingIntent);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.icon));
        builder.setContentTitle("Detected negative speech");
        builder.setContentText(sentence);
        builder.setSubText("sub text");
        builder.setChannelId(CHANNEL_ID);

        try {
            Uri notification = Uri.parse("android.resource://" + getPackageName() + "/" + com.positizing.R.raw.chime);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }

        NotificationManager notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(1, builder.build());
    }

    private final View.OnClickListener stopListener = new View.OnClickListener() {

        @Override
        public void onClick(View arg0) {
            disableSpeechRecognizer();
            Log.d("VS", "Recorder released");
        }

    };

    private final View.OnClickListener startListener = new View.OnClickListener() {

        @Override
        public void onClick(View arg0) {
            setupSpeechRecognizer();
        }

    };

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        if (requestCode == RecordAudioRequestCode) {
            runOnUiThread(()-> {
                if (detector == null) {
                    prepareDetector();
                } else {
                    setupSpeechRecognizer();
                }
            });
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
