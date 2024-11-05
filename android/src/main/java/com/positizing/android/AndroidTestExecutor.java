package com.positizing.android;

public class AndroidTaskExecutor implements TaskExecutor {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void execute(Runnable task) {
        executorService.submit(task);
    }
}