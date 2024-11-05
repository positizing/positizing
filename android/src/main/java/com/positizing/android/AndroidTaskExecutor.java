package com.positizing.android;

import android.os.Handler;
import android.os.Looper;
import injunction.detector.TaskExecutor;

public class AndroidTaskExecutor implements TaskExecutor {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(Runnable task) {
        new Thread(() -> handler.post(task)).start();
    }
}